#!/usr/bin/env bash
# 网盘链迁移：老库 jyinshi_db → 新库 jyinshi_next.media_link
# 用法见 scripts/migrate_links_from_search_cache.py
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# 广州老库（只读）；新库默认本机 Docker jyinshi_next
export OLD_DB_HOST="${OLD_DB_HOST:-175.178.182.111}"
export OLD_DB_PORT="${OLD_DB_PORT:-33068}"
export OLD_DB_USER="${OLD_DB_USER:-jyinshi}"
export OLD_DB_PASSWORD="${OLD_DB_PASSWORD:-jyinshi123}"
export OLD_DB_NAME="${OLD_DB_NAME:-jyinshi_db}"

export NEW_DB_HOST="${NEW_DB_HOST:-127.0.0.1}"
export NEW_DB_PORT="${NEW_DB_PORT:-3306}"
export NEW_DB_USER="${NEW_DB_USER:-jyinshi}"
export NEW_DB_PASSWORD="${NEW_DB_PASSWORD:-jyinshi123}"
export NEW_DB_NAME="${NEW_DB_NAME:-jyinshi_next}"

if ! python3 -c "import pymysql" 2>/dev/null; then
  echo "安装依赖: pip3 install pymysql"
  pip3 install pymysql -q
fi

exec python3 scripts/migrate_links_from_search_cache.py "$@"
