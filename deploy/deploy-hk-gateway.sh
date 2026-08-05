#!/bin/bash
# 切换【香港】149.88.68.90 为网关模式：停 backend/mysql/redis，只留 pansou + 静态前台 + TMDB 反代
set -euo pipefail

SERVER="${DEPLOY_SERVER:-root@149.88.68.90}"
REMOTE_PATH="${DEPLOY_REMOTE_PATH:-/root/jyinshi-next}"
LOCAL_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEPLOY_DIR="$LOCAL_ROOT/deploy"
OLD_COMPOSE="docker compose -f docker-compose.prod.yml"
GW_COMPOSE="docker compose -f docker-compose.hk-gateway.yml"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[hk-gw]${NC} $1"; }
warn()  { echo -e "${YELLOW}[hk-gw]${NC} $1"; }
err()   { echo -e "${RED}[hk-gw]${NC} $1"; exit 1; }

if [[ -z "${SSHPASS:-}" && -f "$DEPLOY_DIR/.env" ]]; then
  SSHPASS="$(grep -E '^SSHPASS=' "$DEPLOY_DIR/.env" | head -n1 | cut -d= -f2-)"
  export SSHPASS
fi
: "${SSHPASS:?未找到 SSHPASS（deploy/.env 或 export SSHPASS=）}"

SSH_OPTS="-o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no"
SSH="sshpass -e ssh $SSH_OPTS"
RSYNC="sshpass -e rsync -avz --progress -e 'ssh $SSH_OPTS'"

remote() { $SSH "$SERVER" "$@"; }

check_ssh() {
  remote "echo ok" >/dev/null || err "无法 SSH 到 $SERVER"
}

sync_gateway() {
  info "同步网关 compose / Caddyfile / web ..."
  # --delete：清掉远端已删源码，避免 Docker 构建编到残留旧文件（如 Detail.vue）
  eval $RSYNC \
    --delete \
    --exclude '.git' --exclude 'node_modules' --exclude 'target' --exclude 'dist' \
    "$LOCAL_ROOT/web/" "$SERVER:$REMOTE_PATH/web/"
  eval $RSYNC \
    "$DEPLOY_DIR/docker-compose.hk-gateway.yml" \
    "$DEPLOY_DIR/Caddyfile.hk-gateway" \
    "$DEPLOY_DIR/.env" \
    "$SERVER:$REMOTE_PATH/deploy/"
}

cutover() {
  info "停旧全栈（backend/mysql/redis/admin）..."
  remote "cd $REMOTE_PATH/deploy && $OLD_COMPOSE down 2>/dev/null || true"
  info "启动网关栈（pansou + web + caddy）..."
  remote "cd $REMOTE_PATH/deploy && $GW_COMPOSE up -d --build"
}

health() {
  sleep 6
  remote "curl -fsS -m 15 https://tmdb.ikantvs.com/3/configuration?api_key=test 2>/dev/null | head -c 80; echo" \
    || warn "tmdb 反代检查（401/403 也可能说明反代通了）"
  remote "curl -fsS -o /dev/null -w 'ikantvs %{http_code}\n' -m 15 https://ikantvs.com/" 2>/dev/null || warn "ikantvs.com 检查失败"
  remote "curl -fsS -o /dev/null -w 'api redirect %{http_code}\n' -m 15 -I https://api.ikantvs.com/api/health 2>/dev/null | head -5" || true
}

case "${1:-all}" in
  sync)     check_ssh; sync_gateway ;;
  cutover)  check_ssh; cutover ;;
  health)   check_ssh; health ;;
  all)
    check_ssh
    sync_gateway
    cutover
    health
    info "✅ 香港已切换为网关：ikantvs.com 静态 + pansou + tmdb 反代"
    info "API 唯一入口：https://api.naspt.vip/api/health"
    warn "请确认 DNS 已加 pansou.ikantvs.com → 香港 IP（广州 backend 采集用）"
    ;;
  *)
    echo "用法: $0 {all|sync|cutover|health}"
    exit 1
    ;;
esac
