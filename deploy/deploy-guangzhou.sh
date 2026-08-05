#!/bin/bash
# 发布 jyinshi-next 到【广州】175.178.182.111（唯一后端，复用老项目 MySQL/Redis + Caddy）
set -euo pipefail

SERVER="${DEPLOY_SERVER:-root@175.178.182.111}"
REMOTE_PATH="${DEPLOY_REMOTE_PATH:-/root/jyinshi-next}"
OLD_PROJECT="${OLD_JYINSHI_PATH:-/root/jyinshi}"
LOCAL_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEPLOY_DIR="$LOCAL_ROOT/deploy"
# 必须显式指定 env-file：compose 默认自动读 deploy/.env（本地/香港那套），会把广州配置顶掉
COMPOSE="docker compose --env-file .env.guangzhou -f docker-compose.guangzhou.yml"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[gz]${NC} $1"; }
warn()  { echo -e "${YELLOW}[gz]${NC} $1"; }
err()   { echo -e "${RED}[gz]${NC} $1"; exit 1; }

if [[ -z "${SSHPASS:-}" ]]; then
  SSHPASS='Lzq951201@'
  export SSHPASS
fi

SSH_OPTS="-o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no"
SSH="sshpass -e ssh $SSH_OPTS"
RSYNC="sshpass -e rsync -avz --progress -e 'ssh $SSH_OPTS'"

remote() { $SSH "$SERVER" "$@"; }

check_ssh() {
  info "检查 SSH..."
  remote "echo ok" >/dev/null || err "无法 SSH 到 $SERVER"
}

rsync_project() {
  info "同步 jyinshi-next 到 $SERVER:$REMOTE_PATH ..."
  # --delete：清掉远端已删除源码，避免 Docker 构建编到残留旧类
  eval $RSYNC \
    --delete \
    --exclude '.git' --exclude 'node_modules' --exclude 'target' \
    --exclude 'dist' --exclude '.DS_Store' \
    "$LOCAL_ROOT/server" "$LOCAL_ROOT/web" "$LOCAL_ROOT/admin" \
    "$SERVER:$REMOTE_PATH/"
  eval $RSYNC \
    --exclude 'data' \
    "$DEPLOY_DIR/" "$SERVER:$REMOTE_PATH/deploy/"
}

prepare_env() {
  info "生成 deploy/.env.guangzhou ..."
  if [[ ! -f "$DEPLOY_DIR/.env" ]]; then
    err "本地 deploy/.env 不存在"
  fi
  # 从 .env 带过去（含 TMDB / 观影账号等）；下面再覆盖广州专用项
  awk '!/^VITE_API_BASE=|^CORS_ALLOWED_ORIGINS=|^PANSOU_BASE=|^XUNLEI_REDIRECT_URI=|^REDIS_PASSWORD=|^DB_USERNAME=|^DB_PASSWORD=|^JYINSHI_CLOUD_ENABLED=/' \
    "$DEPLOY_DIR/.env" > "$DEPLOY_DIR/.env.guangzhou"
  cat >> "$DEPLOY_DIR/.env.guangzhou" <<'EOF'
VITE_API_BASE=/api
CORS_ALLOWED_ORIGINS=https://naspt.vip,https://www.naspt.vip,https://so.naspt.vip,https://ik.naspt.vip,https://admin.naspt.vip,https://ikantvs.com,https://www.ikantvs.com
PANSOU_BASE=https://pansou.ikantvs.com
XUNLEI_REDIRECT_URI=https://api.naspt.vip/api/auto-resource/xunlei/callback
REDIS_PASSWORD=redis123456
DB_USERNAME=jyinshi
DB_PASSWORD=jyinshi123
JYINSHI_CLOUD_ENABLED=false
EOF
  # 开源脱敏后 yml 不再带默认密钥；发版前必须能从 .env 带上观影账号
  if ! grep -qE '^INGEST_GYING_ACCOUNTS=.+' "$DEPLOY_DIR/.env.guangzhou"; then
    warn "INGEST_GYING_ACCOUNTS 为空：广州观影采集会跳过。请写在 deploy/.env 里再发版。"
  fi
  eval $RSYNC "$DEPLOY_DIR/.env.guangzhou" "$SERVER:$REMOTE_PATH/deploy/.env.guangzhou"
}

sync_caddy() {
  local old_caddy="$(dirname "$LOCAL_ROOT")/jyinshi/frontend/Caddyfile"
  [[ -f "$old_caddy" ]] || err "找不到老项目 Caddyfile: $old_caddy"
  info "同步老项目 Caddyfile 并重载 jyinshi-frontend ..."
  eval $RSYNC "$old_caddy" "$SERVER:$OLD_PROJECT/frontend/Caddyfile"
  remote "cd $OLD_PROJECT && docker compose up -d --build --no-deps frontend"
}

patch_sys_config() {
  info "更新广州库 sys_config（TMDB 反代 + 迅雷回调）..."
  remote "docker exec jyinshi-mysql mysql -uroot -proot123456 jyinshi_next -e \"
UPDATE sys_config SET config_value='https://tmdb.ikantvs.com/3' WHERE config_key='metadata.tmdb.base-url';
UPDATE sys_config SET config_value='https://tmdb.ikantvs.com/t/p/w500' WHERE config_key='metadata.tmdb.image-base';
UPDATE sys_config SET config_value='https://tmdb.ikantvs.com/t/p/w780' WHERE config_key='metadata.tmdb.backdrop-base';
UPDATE sys_config SET config_value='https://api.naspt.vip/api/auto-resource/xunlei/callback' WHERE config_key='transfer.xunlei.redirect-uri';
\"" 2>/dev/null || warn "sys_config 更新失败（可后台手动改）"
}

deploy_stack() {
  info "构建并启动广州 backend/web/admin ..."
  remote "cd $REMOTE_PATH/deploy && $COMPOSE up -d --build"
}

health() {
  info "健康检查..."
  sleep 8
  remote "curl -fsS -m 15 -H 'Host: api.naspt.vip' http://127.0.0.1/api/health" && echo || warn "api.naspt.vip 健康检查失败"
  remote "curl -fsS -o /dev/null -w 'naspt %{http_code}\n' -m 15 -H 'Host: naspt.vip' http://127.0.0.1/" 2>/dev/null || warn "naspt.vip 检查失败"
  remote "curl -fsS -o /dev/null -w 'ik %{http_code}\n' -m 15 -H 'Host: ik.naspt.vip' http://127.0.0.1/" 2>/dev/null || warn "ik.naspt.vip 检查失败"
}

release_web() {
  info "重建广州前台（同源 /api）..."
  remote "cd $REMOTE_PATH/deploy && VITE_API_BASE=/api $COMPOSE up -d --build --no-deps web"
}

case "${1:-all}" in
  sync)   check_ssh; rsync_project ;;
  env)    check_ssh; prepare_env ;;
  caddy)  check_ssh; sync_caddy ;;
  stack)  check_ssh; deploy_stack ;;
  web)    check_ssh; rsync_project; release_web ;;
  config) check_ssh; patch_sys_config ;;
  health) check_ssh; health ;;
  all)
    check_ssh
    rsync_project
    prepare_env
    deploy_stack
    sync_caddy
    patch_sys_config
    health
    info "✅ 广州部署完成：naspt.vip / ik.naspt.vip / api.naspt.vip / admin.naspt.vip"
    info "下一步：./deploy-hk-gateway.sh 切换香港为网关模式"
    ;;
  *)
    echo "用法: $0 {all|sync|env|stack|web|caddy|config|health}"
    exit 1
    ;;
esac
