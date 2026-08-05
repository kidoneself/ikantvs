#!/bin/bash
# 宝塔机部署：Docker 跑业务，域名/HTTPS 交给宝塔 Nginx
#
#   cd deploy
#   ./deploy-baota.sh init    # 生成 .env.baota
#   编辑密码 / JWT / CORS_ALLOWED_ORIGINS
#   ./deploy-baota.sh up
#   再到宝塔按 docs/宝塔部署.md 反代域名
#
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$DEPLOY_DIR/.env.baota"
COMPOSE_FILE="docker-compose.baota.yml"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[baota]${NC} $1"; }
warn()  { echo -e "${YELLOW}[baota]${NC} $1"; }
err()   { echo -e "${RED}[baota]${NC} $1"; exit 1; }

compose_cmd() {
  local enable_pansou="true"
  if [[ -f "$ENV_FILE" ]] && grep -qE '^ENABLE_PANSOU=' "$ENV_FILE"; then
    enable_pansou="$(grep -E '^ENABLE_PANSOU=' "$ENV_FILE" | head -1 | cut -d= -f2- | tr -d '\"'"'"'[:space:]')"
  fi
  if [[ "${enable_pansou}" == "true" || "${enable_pansou}" == "1" ]]; then
    echo "docker compose --env-file .env.baota -f $COMPOSE_FILE --profile pansou"
  else
    echo "docker compose --env-file .env.baota -f $COMPOSE_FILE"
  fi
}

run_compose() {
  # shellcheck disable=SC2086
  (cd "$DEPLOY_DIR" && $(compose_cmd) "$@")
}

cmd_init() {
  if [[ -f "$ENV_FILE" ]]; then
    warn "已存在 $ENV_FILE ，未覆盖"
  else
    cp "$DEPLOY_DIR/.env.baota.example" "$ENV_FILE"
    info "已生成 $ENV_FILE ，请编辑：数据库密码 / JWT_SECRET / CORS_ALLOWED_ORIGINS"
  fi
  info "下一步："
  info "  1) 编辑 .env.baota"
  info "  2) ./deploy-baota.sh up"
  info "  3) 宝塔添加站点并反代（见 ../docs/宝塔部署.md）"
}

cmd_up() {
  [[ -f "$ENV_FILE" ]] || err "缺少 $ENV_FILE ，先：./deploy-baota.sh init"
  info "构建并启动（不占用 80/443）..."
  run_compose up -d --build
  echo
  info "容器已启动。本机端口："
  info "  前台 web     http://127.0.0.1:3080  （宝塔反代 / ）"
  info "  后端 API     http://127.0.0.1:3088  （宝塔反代 /api ）"
  info "  管理后台     http://服务器IP:3081   （防火墙放行 3081）"
  echo
  info "请到宝塔：网站 → 添加站点 → SSL → 反向代理，详见 docs/宝塔部署.md"
  info "健康检查：curl -sS http://127.0.0.1:3088/api/health"
}

cmd_down() {
  [[ -f "$ENV_FILE" ]] || err "缺少 $ENV_FILE"
  run_compose down
}

cmd_status() {
  [[ -f "$ENV_FILE" ]] || err "缺少 $ENV_FILE"
  run_compose ps
}

cmd_logs() {
  [[ -f "$ENV_FILE" ]] || err "缺少 $ENV_FILE"
  run_compose logs -f --tail=100 "${1:-}"
}

usage() {
  cat <<EOF
用法: ./deploy-baota.sh <命令>

  init     生成 .env.baota（不覆盖已有）
  up       构建并启动 compose（宝塔反代用）
  down     停止并移除容器
  status   查看容器状态
  logs     跟踪日志（可跟服务名，如 backend）

环境: ENABLE_PANSOU=false 可不启 pansou（改 .env.baota 后重新 up）
文档: ../docs/宝塔部署.md
EOF
}

cd "$DEPLOY_DIR"
case "${1:-}" in
  init)   cmd_init ;;
  up)     cmd_up ;;
  down)   cmd_down ;;
  status) cmd_status ;;
  logs)   shift; cmd_logs "$@" ;;
  *)      usage; exit 1 ;;
esac
