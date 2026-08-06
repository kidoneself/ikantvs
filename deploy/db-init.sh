#!/bin/sh
# 空库导入 /opt/ikantvs/sql；已有 user 表则跳过（可重复执行）
set -eu

HOST="${MYSQL_HOST:-mysql}"
ROOT_PASS="${MYSQL_ROOT_PASSWORD:-change-me-root}"
DB="${MYSQL_DATABASE:-jyinshi_next}"
export MYSQL_PWD="$ROOT_PASS"

echo "[db-init] waiting for mysql at $HOST ..."
i=0
until mysqladmin ping -h"$HOST" -uroot --silent 2>/dev/null; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "[db-init] mysql not ready" >&2
    exit 1
  fi
  sleep 2
done

EXISTS="$(mysql -h"$HOST" -uroot -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB}' AND table_name='user'" 2>/dev/null || echo 0)"
if [ "${EXISTS}" != "0" ]; then
  echo "[db-init] schema already present, skip"
  exit 0
fi

echo "[db-init] importing SQL into ${DB} ..."
for f in /opt/ikantvs/sql/*.sql; do
  [ -f "$f" ] || continue
  echo "[db-init]   $f"
  mysql -h"$HOST" -uroot "$DB" < "$f"
done
echo "[db-init] done"
