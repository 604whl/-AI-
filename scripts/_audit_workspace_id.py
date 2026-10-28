"""Find entities whose workspaceId is declared UUID but whose column is VARCHAR(32).

V49 normalised `workspace_id` to `VARCHAR(32)` project-wide (workspace ids look
like `ws_a501a7d315a8`, not UUIDs), but a few entities kept `private UUID
workspaceId`. MyBatis then binds a UUID against a varchar column, and code
that does `UUID.fromString(workspaceId)` throws "invalid_workspace_id" --
which is why `POST /v1/assets/{id}/submit-review` failed in the e2e run.

This script lists every entity with a UUID workspaceId together with the
actual column type from information_schema.
"""
import subprocess
import sys

ROOT = r"F:\projectAI\XhsAgent"
ENTITY_DIR = (ROOT + r"\Xhs-Backend\src\main\java\com\shortvideoscripagent"
              r"\xhsagentyunying\domain\entity")

DOCKER = ["docker", "exec", "xhsagent-postgres-dev", "psql", "-U", "xhsagent",
          "-d", "xhsagent", "-t", "-A", "-F", "|", "-c"]


def query(sql):
    out = subprocess.run(DOCKER + [sql], capture_output=True, text=True,
                         encoding="utf-8", errors="replace")
    if out.returncode != 0:
        return []
    rows = []
    for line in out.stdout.splitlines():
        line = line.strip()
        if line and "|" in line:
            rows.append(line.split("|"))
    return rows


# table -> workspace_id column type
col_types = {}
for table, col, ctype in query(
        "select table_name, column_name, data_type "
        "from information_schema.columns "
        "where table_schema='app' and column_name='workspace_id';"):
    col_types[table] = ctype

import os
import re

print("entities with UUID workspaceId:")
print("-" * 70)
bad = []
for fn in sorted(os.listdir(ENTITY_DIR)):
    if not fn.endswith(".java"):
        continue
    text = open(os.path.join(ENTITY_DIR, fn), encoding="utf-8",
                errors="replace").read()
    m = re.search(r"@TableName\(\s*(?:value\s*=\s*)?\"([^\"]+)\"", text)
    if not m:
        continue
    table = m.group(1)
    if not re.search(r"private\s+UUID\s+workspaceId\s*;", text):
        continue
    ctype = col_types.get(table, "???")
    flag = "OK " if ctype == "character varying" else "BAD"
    if ctype != "character varying":
        bad.append((fn, table, ctype))
    print("  %s %-34s table=%-28s col=%s" % (flag, fn, table, ctype))

print()
print("MISMATCHES (entity says UUID, column is not varchar): %d" % len(bad))
for fn, table, ctype in bad:
    print("  %s  table=%s  column=%s" % (fn, table, ctype))
