#!/bin/bash
# 宝塔部署：./deploy.sh init|up|down|status|logs
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$DEPLOY_DIR/.env"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[deploy]${NC} $1"; }
warn()  { echo -e "${YELLOW}[deploy]${NC} $1"; }
err()   { echo -e "${RED}[deploy]${NC} $1"; exit 1; }

usage() {
  cat <<'EOF'
用法: ./deploy.sh <命令>

  init     生成 .env
  up       构建并启动
  down     停止
  status   状态
  logs     日志（可跟服务名）

说明见 ../docs/部署.md
EOF
}

compose_cmd() {
  local enable_pansou="true"
  if [[ -f "$ENV_FILE" ]] && grep -qE '^ENABLE_PANSOU=' "$ENV_FILE"; then
    enable_pansou="$(grep -E '^ENABLE_PANSOU=' "$ENV_FILE" | head -1 | cut -d= -f2- | tr -d '\"'"'"'[:space:]')"
  fi
  if [[ "${enable_pansou}" == "true" || "${enable_pansou}" == "1" ]]; then
    echo "docker compose --env-file .env -f docker-compose.yml --profile pansou"
  else
    echo "docker compose --env-file .env -f docker-compose.yml"
  fi
}

run_compose() {
  # shellcheck disable=SC2086
  (cd "$DEPLOY_DIR" && $(compose_cmd) "$@")
}

load_env() {
  [[ -f "$ENV_FILE" ]] || err "缺少 .env，先：./deploy.sh init"
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
}

CMD="${1:-}"
[[ -n "$CMD" ]] || { usage; exit 1; }
cd "$DEPLOY_DIR"

case "$CMD" in
  init)
    if [[ -f "$ENV_FILE" ]]; then
      warn ".env 已存在，未覆盖"
    else
      cp "$DEPLOY_DIR/.env.example" "$ENV_FILE"
      info "已生成 .env，请改：密码 / JWT_SECRET / CORS_ALLOWED_ORIGINS"
    fi
    ;;
  up)
    load_env
    info "启动中（不占 80/443）..."
    run_compose up -d --build
    echo
    info "本机：前台 127.0.0.1:3080  API 127.0.0.1:3088  后台 :3081"
    info "宝塔：反代 → 3080，加 /api → 3088，开 SSL。见 docs/部署.md"
    ;;
  down)
    load_env
    run_compose down
    ;;
  status)
    load_env
    run_compose ps
    ;;
  logs)
    load_env
    shift || true
    run_compose logs -f --tail=100 "$@"
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage
    exit 1
    ;;
esac
