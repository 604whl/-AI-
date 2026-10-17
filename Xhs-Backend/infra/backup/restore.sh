#!/bin/bash
# ==============================================================================
# XhsAgent 恢复脚本
# 功能：从 MinIO backup bucket 恢复 PostgreSQL 数据
# 用法：./restore.sh <backup_file> [target_env]
# 示例：./restore.sh pg_prod_20261017_120000.sql.gz prod
# ==============================================================================

set -euo pipefail

BACKUP_FILE="${1:-}"
TARGET_ENV="${2:-prod}"

if [[ -z "$BACKUP_FILE" ]]; then
    echo "Usage: $0 <backup_file> [target_env]"
    echo "Available backups:"
    ls -la ./data/backups/pg_*.sql.gz 2>/dev/null || echo "  No backups found in ./data/backups/"
    exit 1
fi

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-xhsagent}"
PGUSER="${PGUSER:-xhsagent}"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://localhost:9000}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-minioadmin}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-minioadmin}"
MINIO_BUCKET="${MINIO_BUCKET:-xhs-agent-backup}"
BACKUP_DIR="${BACKUP_DIR:-./data/backups}"

# ---------------------------------------------------------------------------
# 1. 确认备份文件存在
# ---------------------------------------------------------------------------
if [[ ! -f "$BACKUP_FILE" ]]; then
    echo "[restore] Backup file not found locally: $BACKUP_FILE"
    # 尝试从 MinIO 下载
    if command -v mc &> /dev/null; then
        echo "[restore] Trying to download from MinIO..."
        mc alias set minio "$MINIO_ENDPOINT" "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" 2>/dev/null || true
        mc cp "minio/${MINIO_BUCKET}/pg_${TARGET_ENV}/${BACKUP_FILE}" "$BACKUP_DIR/" || {
            echo "[restore] Failed to download from MinIO"
            exit 1
        }
    else
        echo "[restore] Error: backup file not found and mc (MinIO Client) not available"
        exit 1
    fi
fi

BACKUP_FILE=$(realpath "$BACKUP_FILE")

# ---------------------------------------------------------------------------
# 2. 警告：生产恢复需要二次确认
# ---------------------------------------------------------------------------
if [[ "$TARGET_ENV" == "prod" ]]; then
    echo "=============================================="
    echo "  WARNING: Production database restore!"
    echo "  Backup file: $BACKUP_FILE"
    echo "  Target: $PGDATABASE @ $PGHOST:$PGPORT"
    echo "=============================================="
    read -p "Type 'yes' to confirm: " CONFIRM
    if [[ "$CONFIRM" != "yes" ]]; then
        echo "[restore] Aborted"
        exit 1
    fi
fi

# ---------------------------------------------------------------------------
# 3. 创建预恢复备份（万一恢复失败）
# ---------------------------------------------------------------------------
PRE_BACKUP="$BACKUP_DIR/pre_restore_${TARGET_ENV}_$(date '+%Y%m%d_%H%M%S').sql.gz"
echo "[restore] Creating pre-restore backup: $PRE_BACKUP"
PGPASSWORD="${PGPASSWORD:-xhsagent}" pg_dump -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" \
    --no-owner --no-acl --compress=9 | gzip > "$PRE_BACKUP" || {
    echo "[restore] Warning: pre-restore backup failed, continuing anyway..."
}

# ---------------------------------------------------------------------------
# 4. 恢复数据库
# ---------------------------------------------------------------------------
echo "[restore] Restoring database from $BACKUP_FILE..."

# 终止现有连接
PGPASSWORD="${PGPASSWORD:-xhsagent}" psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres -c \
    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$PGDATABASE' AND pid<>pg_backend_pid();" 2>/dev/null || true

# 创建/清空数据库
PGPASSWORD="${PGPASSWORD:-xhsagent}" psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres -c \
    "DROP DATABASE IF EXISTS ${PGDATABASE}_restore_temp;" 2>/dev/null || true
PGPASSWORD="${PGPASSWORD:-xhsagent}" psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres -c \
    "CREATE DATABASE ${PGDATABASE}_restore_temp;" 2>/dev/null || true

# 恢复数据
gunzip -c "$BACKUP_FILE" | PGPASSWORD="${PGPASSWORD:-xhsagent}" psql \
    -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "${PGDATABASE}_restore_temp" \
    --set ON_ERROR_STOP=on

if [[ $? -eq 0 ]]; then
    echo "[restore] Database restored successfully to temporary database"
    echo "[restore] To complete restore, swap: ${PGDATABASE}_restore_temp -> $PGDATABASE"
else
    echo "[restore] ERROR: Database restore failed"
    exit 1
fi

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Restore preparation completed!"
echo "[restore] Backup file: $BACKUP_FILE"
echo "[restore] Pre-restore backup: $PRE_BACKUP"
echo "[restore] Restored to: ${PGDATABASE}_restore_temp"
echo "[restore] Next step: swap database names if satisfied with the restore"
