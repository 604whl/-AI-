#!/bin/bash
# ==============================================================================
# XhsAgent 备份脚本
# 功能：pg_dump 压缩备份 + MinIO 数据同步
# 用法：./backup.sh [env]
# 示例：./backup.sh prod
# ==============================================================================

set -euo pipefail

ENV="${1:-prod}"
TIMESTAMP=$(date '+%Y%m%d_%H%M%S')
BACKUP_DIR="${BACKUP_DIR:-./data/backups}"
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-xhsagent}"
PGUSER="${PGUSER:-xhsagent}"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://localhost:9000}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-minioadmin}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-minioadmin}"
MINIO_BUCKET="${MINIO_BUCKET:-xhs-agent-backup}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"

mkdir -p "$BACKUP_DIR"

# ---------------------------------------------------------------------------
# 1. PostgreSQL 备份
# ---------------------------------------------------------------------------
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting backup..."

PGDUMP_FILE="$BACKUP_DIR/pg_${ENV}_${TIMESTAMP}.sql.gz"
echo "[backup] Dumping PostgreSQL to $PGDUMP_FILE..."
PGPASSWORD="${PGPASSWORD:-xhsagent}" pg_dump -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" \
    --no-owner --no-acl --compress=9 \
    | gzip > "$PGDUMP_FILE"

PGDUMP_SIZE=$(du -h "$PGDUMP_FILE" | cut -f1)
echo "[backup] PostgreSQL dump created: $PGDUMP_FILE ($PGDUMP_SIZE)"

# ---------------------------------------------------------------------------
# 2. MinIO 数据同步到本地备份目录
# ---------------------------------------------------------------------------
if command -v mc &> /dev/null; then
    echo "[backup] Syncing MinIO data..."
    mc alias set minio "$MINIO_ENDPOINT" "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" 2>/dev/null || true
    mc cp -r "minio/xhs-agent/" "$BACKUP_DIR/minio_xhs_agent_${TIMESTAMP}/" 2>/dev/null || true
    echo "[backup] MinIO sync completed"
else
    echo "[backup] mc (MinIO Client) not found, skipping MinIO sync"
fi

# ---------------------------------------------------------------------------
# 3. 上传到 MinIO backup bucket
# ---------------------------------------------------------------------------
if command -v mc &> /dev/null; then
    echo "[backup] Uploading to MinIO backup bucket..."
    mc mb -p "minio/${MINIO_BUCKET}" 2>/dev/null || true
    mc cp "$PGDUMP_FILE" "minio/${MINIO_BUCKET}/pg_${ENV}/"
    echo "[backup] Uploaded to MinIO: ${MINIO_BUCKET}/pg_${ENV}/pg_${ENV}_${TIMESTAMP}.sql.gz"
fi

# ---------------------------------------------------------------------------
# 4. 清理旧备份（保留最近 N 天）
# ---------------------------------------------------------------------------
echo "[backup] Cleaning old backups (retention: ${BACKUP_RETENTION_DAYS} days)..."
find "$BACKUP_DIR" -name "pg_${ENV}_*.sql.gz" -mtime "+${BACKUP_RETENTION_DAYS}" -delete 2>/dev/null || true
find "$BACKUP_DIR" -name "minio_xhs_agent_*" -type d -mtime "+${BACKUP_RETENTION_DAYS}" -exec rm -rf {} + 2>/dev/null || true

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Backup completed successfully!"
echo "[backup] Backup file: $PGDUMP_FILE"
echo "[backup] Local backup dir: $BACKUP_DIR"

# 记录备份元数据
METADATA_FILE="$BACKUP_DIR/backup_metadata_${TIMESTAMP}.txt"
cat > "$METADATA_FILE" <<EOF
ENV=$ENV
TIMESTAMP=$TIMESTAMP
PGDUMP_FILE=$PGDUMP_FILE
PGDUMP_SIZE=$PGDUMP_SIZE
BACKUP_RETENTION_DAYS=$BACKUP_RETENTION_DAYS
MINIO_ENDPOINT=$MINIO_ENDPOINT
MINIO_BUCKET=$MINIO_BUCKET
EOF

echo "[backup] Metadata: $METADATA_FILE"
