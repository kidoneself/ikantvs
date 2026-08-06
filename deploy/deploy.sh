#!/bin/bash
# 可选快捷方式。宝塔也可直接粘贴 docker-compose.yml 启动。
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
  up       拉取并启动（一份 compose，自动建表）
  down     停止
  status   状态
  logs     日志

说明见 ../docs/部署.md
EOF
}

run_compose() {
  (cd "$DEPLOY_DIR" && docker compose --env-file .env -f docker-compose.yml "$@")
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
      info "已生成 .env，请改密码 / JWT_SECRET / CORS_ALLOWED_ORIGINS"
    fi
    ;;
  up)
    [[ -f "$ENV_FILE" ]] || err "缺少 .env，先：./deploy.sh init"
    info "拉取镜像..."
    run_compose pull
    info "启动..."
    run_compose up -d
    echo
    info "前台 http://服务器IP:3080  后台 http://服务器IP:3081"
    ;;
  down)
    [[ -f "$ENV_FILE" ]] || err "缺少 .env"
    run_compose down
    ;;
  status)
    [[ -f "$ENV_FILE" ]] || err "缺少 .env"
    run_compose ps
    ;;
  logs)
    [[ -f "$ENV_FILE" ]] || err "缺少 .env"
    shift || true
    run_compose logs -f --tail=100 "$@"
    ;;
  -h|--help|help) usage ;;
  *) usage; exit 1 ;;
esac
