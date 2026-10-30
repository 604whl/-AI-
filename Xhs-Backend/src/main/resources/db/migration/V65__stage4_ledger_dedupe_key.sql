-- V65: stage4 ledger dedupe key (P0-11)
--
-- Background (P0-11):
--   Task retry (TaskQueueConsumerRunner#handleFailure -> scheduleRetry) and orphan
--   task recovery (TaskRecoveryService#recoverOrphanTasks) both re-deliver the SAME
--   taskId to AnalysisTaskHandler. If the handler charges quota on every execution,
--   one task that fails N times is charged N times.
--   Verified in dev DB: 3 rows of ANALYSIS ledger share one ref_id (1 task charged 3x).
--
-- Design:
--   Add a dedupe_key column. Only call sites where "one business event should be
--   charged exactly once" populate it. A partial unique index on
--   (workspace_id, dedupe_key) WHERE status='CONSUMED' is the concurrency backstop.
--
-- Why not a unique index on (workspace_id, ref_id, action) directly:
--   Multiple Agent messages in the same session are each supposed to be charged
--   (action='CHAT'), so ref_id alone cannot distinguish "redelivery" from
--   "legitimately multiple charges". Hence an explicit dedupe_key: the caller
--   declares "this one must be idempotent".
--
-- Why the index is partial (WHERE status='CONSUMED'):
--   When a ledger row is refunded its status becomes 'REFUNDED' and it must
--   release its dedupe_key, otherwise a legitimate re-charge after refund
--   would be rejected by the unique index.
--   Reversal rows (related_log_id NOT NULL) carry a NULL dedupe_key and are
--   therefore never subject to the unique constraint.
--
-- Idempotency:
--   All statements are IF NOT EXISTS / idempotent, safe to re-run.

SET search_path TO app, kb, public;

-- ============================================================
-- 1. app.usage_ledger: add idempotency dedupe key
-- ============================================================
ALTER TABLE app.usage_ledger
    ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(160);

COMMENT ON COLUMN app.usage_ledger.dedupe_key IS
    'Idempotency dedupe key. Only populated by call sites where one business event '
    'must be charged exactly once; format <ACTION>:<ref_id>. NULL means the row is '
    'not subject to dedupe (e.g. CHAT messages, BALANCE flow rows, reversal rows).';

-- ============================================================
-- 2. Partial unique index: concurrency backstop
--    Only status='CONSUMED' rows participate; refunded rows release their key
-- ============================================================
CREATE UNIQUE INDEX IF NOT EXISTS uq_usage_ledger_dedupe_key
    ON app.usage_ledger (workspace_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL
      AND status = 'CONSUMED';

-- ============================================================
-- 3. Lookup index for the idempotent replay path
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_usage_ledger_dedupe_lookup
    ON app.usage_ledger (workspace_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL;

-- ============================================================
-- 4. Historical data self-healing:
--    For each (workspace_id, ref_id, 'ANALYSIS') group with more than one
--    CONSUMED row, keep the earliest as the valid charge and refund the rest
--    (mark REFUNDED + write a -1 reversal row) so that net amount is zero.
--    Idempotent: already-refunded rows no longer match status='CONSUMED'.
-- ============================================================
CREATE OR REPLACE FUNCTION app.v65_reconcile_duplicate_analysis_charges()
RETURNS INTEGER AS $$
DECLARE
    grp        RECORD;
    dup_row    RECORD;
    keep_id    UUID;
    affected   INTEGER := 0;
BEGIN
    FOR grp IN
        SELECT workspace_id, ref_id, action
        FROM app.usage_ledger
        WHERE ref_id IS NOT NULL
          AND action = 'ANALYSIS'
          AND status = 'CONSUMED'
          AND dedupe_key IS NULL
        GROUP BY workspace_id, ref_id, action
        HAVING COUNT(*) > 1
    LOOP
        -- earliest row is the valid charge
        SELECT id INTO keep_id
        FROM app.usage_ledger
        WHERE workspace_id = grp.workspace_id
          AND ref_id = grp.ref_id
          AND action = grp.action
          AND status = 'CONSUMED'
          AND dedupe_key IS NULL
        ORDER BY created_at ASC, id ASC
        LIMIT 1;

        -- refund every other row (duplicate charge)
        FOR dup_row IN
            SELECT id, workspace_id, user_id, action, unit_price, amount, price_version, provider
            FROM app.usage_ledger
            WHERE workspace_id = grp.workspace_id
              AND ref_id = grp.ref_id
              AND action = grp.action
              AND status = 'CONSUMED'
              AND dedupe_key IS NULL
              AND id <> keep_id
            ORDER BY created_at ASC, id ASC
        LOOP
            UPDATE app.usage_ledger
            SET status = 'REFUNDED'
            WHERE id = dup_row.id AND status = 'CONSUMED';

            INSERT INTO app.usage_ledger
                (id, workspace_id, user_id, action, ref_id, delta, status,
                 related_log_id, reason, unit_price, amount, price_version, provider, created_at)
            VALUES
                (gen_random_uuid(), dup_row.workspace_id, dup_row.user_id, dup_row.action, NULL, -1, 'CONSUMED',
                 dup_row.id, 'refund:migration-v65-duplicate-charge',
                 dup_row.unit_price,
                 COALESCE(-dup_row.amount, 0),
                 dup_row.price_version, dup_row.provider, NOW());

            affected := affected + 1;
        END LOOP;
    END LOOP;

    RETURN affected;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    n INTEGER;
BEGIN
    n := app.v65_reconcile_duplicate_analysis_charges();
    RAISE NOTICE 'V65: reconciled % duplicate ANALYSIS charge rows', n;
END
$$;

DROP FUNCTION IF EXISTS app.v65_reconcile_duplicate_analysis_charges();

-- ============================================================
-- 5. Backfill dedupe_key for the surviving ANALYSIS charge rows
--    (one key per workspace_id + ref_id + action)
-- ============================================================
UPDATE app.usage_ledger l
SET dedupe_key = l.action || ':' || l.ref_id::text
WHERE l.ref_id IS NOT NULL
  AND l.action = 'ANALYSIS'
  AND l.status = 'CONSUMED'
  AND l.dedupe_key IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM app.usage_ledger e
      WHERE e.workspace_id = l.workspace_id
        AND e.ref_id = l.ref_id
        AND e.action = l.action
        AND e.status = 'CONSUMED'
        AND e.created_at < l.created_at
  );

COMMENT ON INDEX app.uq_usage_ledger_dedupe_key IS
    'S4-3 ledger idempotency: at most one CONSUMED row per (workspace_id, dedupe_key); refunded rows release their key';
