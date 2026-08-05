#!/bin/bash
# 海外单机单域名（一台 VPS：MySQL+Redis+backend+web+admin+Caddy；pansou 可选）
#
# 本机（已 SSH 到服务器）：
#   cd deploy && ./deploy-single.sh init   # 生成并编辑 .env.single
#   ./deploy-single.sh up
#
# 从笔记本远程发：
#   export DEPLOY_SERVER=root@x.x.x.x
#   export SSHPASS='...'          # 或已配 SSH key，可不设
#   export DEPLOY_REMOTE_PATH=/opt/jyinshi-next
#   ./deploy-single.sh all
#
# 子命令：init | sync | env | up|stack | web | backend | admin | caddy | status | health | down | all
set -euo pipefail

LOCAL_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEPLOY_DIR="$LOCAL_ROOT/deploy"
ENV_FILE="$DEPLOY_DIR/.env.single"
COMPOSE_FILE="docker-compose.single.yml"

# 按 .env.single 的 ENABLE_PANSOU 决定是否带 pansou profile
compose_cmd() {
  local enable_pansou="true"
  if [[ -f "$ENV_FILE" ]] && grep -qE '^ENABLE_PANSOU=' "$ENV_FILE"; then
    enable_pansou="$(grep -E '^ENABLE_PANSOU=' "$ENV_FILE" | head -1 | cut -d= -f2- | tr -d '\"'"'"'[:space:]')"
  fi
  if [[ "${enable_pansou}" == "true" || "${enable_pansou}" == "1" ]]; then
    echo "docker compose --env-file .env.single -f $COMPOSE_FILE --profile pansou"
  else
    echo "docker compose --env-file .env.single -f $COMPOSE_FILE"
  fi
}

COMPOSE="$(compose_cmd)"

SERVER="${DEPLOY_SERVER:-}"
REMOTE_PATH="${DEPLOY_REMOTE_PATH:-/opt/jyinshi-next}"
SSH_PORT="${DEPLOY_SSH_PORT:-22}"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[single]${NC} $1"; }
warn()  { echo -e "${YELLOW}[single]${NC} $1"; }
err()   { echo -e "${RED}[single]${NC} $1"; exit 1; }

remote_mode() { [[ -n "$SERVER" ]]; }

# SSH：有 SSHPASS 用密码，否则走本机 key
setup_ssh() {
  SSH_OPTS="-p $SSH_PORT -o StrictHostKeyChecking=no"
  if [[ -n "${SSHPASS:-}" ]]; then
    command -v sshpass >/dev/null || err "装了 SSHPASS 但本机没有 sshpass"
    SSH="sshpass -e ssh $SSH_OPTS -o PreferredAuthentications=password -o PubkeyAuthentication=no"
    RSYNC="sshpass -e rsync -avz --progress -e \"ssh $SSH_OPTS -o PreferredAuthentications=password -o PubkeyAuthentication=no\""
  else
    SSH="ssh $SSH_OPTS"
    RSYNC="rsync -avz --progress -e \"ssh $SSH_OPTS\""
  fi
}

remote() { $SSH "$SERVER" "$@"; }

run_compose() {
  COMPOSE="$(compose_cmd)"
  # shellcheck disable=SC2086
  if remote_mode; then
    # 远端也按同款 env 读 ENABLE_PANSOU；把 profile 参数一并带上
    remote "cd $REMOTE_PATH/deploy && $(compose_cmd) $*"
  else
    (cd "$DEPLOY_DIR" && $COMPOSE $*)
  fi
}

load_env_local() {
  local require_real="${1:-1}"
  [[ -f "$ENV_FILE" ]] || err "缺少 $ENV_FILE ，先：./deploy-single.sh init 并编辑"
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
  [[ -n "${SITE_DOMAIN:-}" ]] || err "SITE_DOMAIN 不能为空"
  if [[ "$require_real" == "1" ]]; then
    [[ "$SITE_DOMAIN" != "example.com" ]] \
      || err "请在 .env.single 里把 SITE_DOMAIN 改成真实域名"
    [[ -n "${ACME_EMAIL:-}" && "$ACME_EMAIL" != "admin@example.com" ]] \
      || warn "ACME_EMAIL 仍是示例邮箱，证书签发可能有问题"
  fi
}

# 写入或覆盖 KEY=VALUE（并去掉同名注释示例行）
set_env() {
  local key="$1" value="$2"
  grep -vE "^${key}=" "$ENV_FILE" | grep -vE "^# ${key}=" > "$ENV_FILE.tmp"
  echo "${key}=${value}" >> "$ENV_FILE.tmp"
  mv "$ENV_FILE.tmp" "$ENV_FILE"
}

# 若 KEY 尚无有效赋值行，则追加（不覆盖已有）
upsert_env() {
  local key="$1" value="$2"
  if grep -qE "^${key}=" "$ENV_FILE"; then
    return 0
  fi
  set_env "$key" "$value"
  info "已写入 ${key}"
}

# 按 SITE_DOMAIN 同步 CORS / 迅雷回调；VITE_API_BASE 缺省才补
ensure_derived_env() {
  local require_real="${1:-1}"
  load_env_local "$require_real"
  set_env "CORS_ALLOWED_ORIGINS" "https://${SITE_DOMAIN},http://127.0.0.1:8080"
  set_env "XUNLEI_REDIRECT_URI" "https://${SITE_DOMAIN}/api/auto-resource/xunlei/callback"
  upsert_env "VITE_API_BASE" "/api"
  info "已按 SITE_DOMAIN=${SITE_DOMAIN} 同步 CORS / 迅雷回调（后台若用公网 IP:8080，请把 Origin 追加进 CORS_ALLOWED_ORIGINS）"
}

cmd_init() {
  if [[ ! -f "$ENV_FILE" ]]; then
    cp "$DEPLOY_DIR/.env.single.example" "$ENV_FILE"
    info "已生成 $ENV_FILE ，请编辑 SITE_DOMAIN / ACME_EMAIL / 密码 / JWT_SECRET"
  else
    info ".env.single 已存在，跳过复制"
  fi
  ensure_derived_env 0
  load_env_local 0
  COMPOSE="$(compose_cmd)"
  info "下一步（海外单机）："
  info "  1) 编辑 .env.single：SITE_DOMAIN / ACME_EMAIL / 密码 / JWT_SECRET"
  info "  2) 要对齐全站数据：填 JYINSHI_CLOUD_* ；只要壳可保持 false"
  info "  3) 纯云端可设 ENABLE_PANSOU=false 省内存"
  info "  4) DNS：A 记录 → 本机公网 IP；放行 80/443（建议 UDP 443）"
  info "  5) ./deploy-single.sh up"
}

check_ssh() {
  setup_ssh
  info "检查 SSH $SERVER ..."
  remote "echo ok" >/dev/null || err "无法 SSH 到 $SERVER"
}

rsync_project() {
  setup_ssh
  info "同步代码到 $SERVER:$REMOTE_PATH ..."
  remote "mkdir -p $REMOTE_PATH"
  eval $RSYNC \
    --delete \
    --exclude '.git' --exclude 'node_modules' --exclude 'target' \
    --exclude 'dist' --exclude '.DS_Store' \
    "$LOCAL_ROOT/server" "$LOCAL_ROOT/web" "$LOCAL_ROOT/admin" \
    "$SERVER:$REMOTE_PATH/"
  eval $RSYNC \
    --exclude 'data' \
    --exclude '.env' \
    --exclude '.env.guangzhou' \
    "$DEPLOY_DIR/" "$SERVER:$REMOTE_PATH/deploy/"
  # 密钥单独同步，避免被 --exclude 误伤
  [[ -f "$ENV_FILE" ]] || err "本地没有 .env.single"
  eval $RSYNC "$ENV_FILE" "$SERVER:$REMOTE_PATH/deploy/.env.single"
}

cmd_env() {
  ensure_derived_env
  if remote_mode; then
    check_ssh
    eval $RSYNC "$ENV_FILE" "$SERVER:$REMOTE_PATH/deploy/.env.single"
    info "已推送 .env.single → 远端"
  fi
}

cmd_up() {
  ensure_derived_env
  if remote_mode; then
    check_ssh
    cmd_env
  fi
  info "构建并启动全栈..."
  run_compose "up -d --build"
}

cmd_service() {
  local svc="$1"
  ensure_derived_env
  if remote_mode; then
    check_ssh
    rsync_project
  fi
  info "重建 $svc ..."
  run_compose "up -d --build --no-deps $svc"
}

cmd_status() {
  run_compose "ps"
}

cmd_health() {
  load_env_local
  info "健康检查 SITE_DOMAIN=$SITE_DOMAIN ..."
  sleep 3
  if remote_mode; then
    setup_ssh
    remote "curl -fsS -m 20 -H 'Host: $SITE_DOMAIN' http://127.0.0.1/api/health" && echo \
      || warn "本机 Host 探测失败（证书/未起好时正常，可再试 https）"
    remote "curl -fsS -m 20 https://$SITE_DOMAIN/api/health" && echo \
      || warn "https://$SITE_DOMAIN/api/health 失败"
    remote "curl -fsS -o /dev/null -w 'web %{http_code}\n' -m 20 -H 'Host: $SITE_DOMAIN' http://127.0.0.1/" \
      || warn "前台探测失败"
    remote "curl -fsS -o /dev/null -w 'admin8080 %{http_code}\n' -m 20 http://127.0.0.1:8080/" \
      || warn "后台 :8080 探测失败"
  else
    curl -fsS -m 20 -H "Host: $SITE_DOMAIN" http://127.0.0.1/api/health && echo \
      || warn "本机 Host 探测失败"
    curl -fsS -m 20 "https://$SITE_DOMAIN/api/health" && echo \
      || warn "https://$SITE_DOMAIN/api/health 失败（DNS/证书未好时先看上一条）"
    curl -fsS -o /dev/null -w "web %{http_code}\n" -m 20 -H "Host: $SITE_DOMAIN" http://127.0.0.1/ \
      || warn "前台探测失败"
    curl -fsS -o /dev/null -w "admin8080 %{http_code}\n" -m 20 http://127.0.0.1:8080/ \
      || warn "后台 :8080 探测失败"
  fi
}

cmd_down() {
  warn "将停止单机栈容器（数据卷保留）"
  run_compose "down"
}

# 交付站消毒：关掉通知/NAS，清空飞书/迅雷密钥与转存运行态（不含影视库表）
cmd_sanitize() {
  load_env_local
  info "消毒私有配置（notify / NAS / 迅雷密钥 / 转存账号）..."
  local sql
  sql=$(cat <<'SQL'
UPDATE sys_config SET config_value='false' WHERE config_key IN ('notify.enabled','transfer.nas.enabled','xunlei.sdk.enabled');
UPDATE sys_config SET config_value='' WHERE config_key IN (
  'notify.feishu.app-id','notify.feishu.app-secret','notify.feishu.user-id',
  'notify.webhook.url','notify.webhook.secret',
  'transfer.xunlei.client-id','transfer.xunlei.client-secret','transfer.nas.wake-url',
  'xunlei.partner.custom'
);
DELETE FROM sys_config WHERE config_key='notify.feishu.legacy-seeded';
TRUNCATE TABLE transfer_account;
TRUNCATE TABLE transfer_monitor;
TRUNCATE TABLE transfer_job;
TRUNCATE TABLE transfer_record;
TRUNCATE TABLE transfer_login_session;
TRUNCATE TABLE nas_job;
TRUNCATE TABLE nas_landing;
SQL
)
  if remote_mode; then
    setup_ssh
    remote "ROOT_PW=\$(grep '^MYSQL_ROOT_PASSWORD=' $REMOTE_PATH/deploy/.env.single | cut -d= -f2-); docker exec -i jyinshi-mysql mysql -uroot -p\"\$ROOT_PW\" jyinshi_next" <<<"$sql"
    run_compose "restart backend"
  else
    local root_pw
    root_pw=$(grep '^MYSQL_ROOT_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)
    docker exec -i jyinshi-mysql mysql -uroot -p"$root_pw" jyinshi_next <<<"$sql"
    (cd "$DEPLOY_DIR" && $COMPOSE restart backend)
  fi
  info "已消毒；backend 已重启以刷新配置缓存"
}

usage() {
  cat <<EOF
用法: $0 {init|sync|env|up|stack|web|backend|admin|caddy|sanitize|status|health|down|all}

  面向一台海外 VPS 的单机单域名交付（推荐开源默认路径）。

  init     生成 .env.single 并补全 CORS/迅雷回调
  sync     仅 rsync（需 DEPLOY_SERVER）
  env      补全并（远程时）推送 .env.single
  up|stack 全量 up -d --build（ENABLE_PANSOU=true 时带 pansou）
  web|backend|admin|caddy  只重建对应服务
  sanitize 清空飞书/NAS/迅雷私有配置与转存账号（交付给别人用）
  status   docker compose ps
  health   探活
  down     compose down（保留 volume）
  all      sync + up + health（远程）或 up + health（本机）

环境变量:
  DEPLOY_SERVER        如 root@1.2.3.4；空则本机执行
  DEPLOY_REMOTE_PATH   默认 /opt/jyinshi-next
  DEPLOY_SSH_PORT      默认 22
  SSHPASS              可选，密码登录

.env.single 关键点:
  JYINSHI_CLOUD_*      对接运营方数据的一份链接
  ENABLE_PANSOU        false=不启 pansou（纯云端壳更省内存）
EOF
}

case "${1:-}" in
  init)   cmd_init ;;
  sync)
    remote_mode || err "sync 需要 export DEPLOY_SERVER=root@IP"
    check_ssh
    ensure_derived_env
    rsync_project
    ;;
  env)    cmd_env ;;
  up|stack) cmd_up ;;
  web)    cmd_service web ;;
  backend) cmd_service backend ;;
  admin)  cmd_service admin ;;
  caddy)  cmd_service caddy ;;
  status)
    if remote_mode; then check_ssh; fi
    cmd_status
    ;;
  health)
    if remote_mode; then check_ssh; fi
    cmd_health
    ;;
  down)
    if remote_mode; then check_ssh; fi
    cmd_down
    ;;
  sanitize)
    if remote_mode; then check_ssh; fi
    cmd_sanitize
    ;;
  all)
    if remote_mode; then
      check_ssh
      ensure_derived_env
      rsync_project
      run_compose "up -d --build"
      cmd_health
      info "✅ 单机部署完成：https://$SITE_DOMAIN  后台 http://服务器IP:8080"
    else
      cmd_up
      cmd_health
      load_env_local
      info "✅ 单机部署完成：https://$SITE_DOMAIN  后台 http://本机IP:8080"
    fi
    ;;
  ""|-h|--help) usage ;;
  *)
    usage
    exit 1
    ;;
esac
