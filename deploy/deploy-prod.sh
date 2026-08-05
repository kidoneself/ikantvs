#!/bin/bash
# ============================================
# 【已停用】旧脚本会把整站（含 backend/mysql）发到香港 149.88.68.90。
# 当前拓扑：
#   广州 = 唯一后端 / 后台 / MySQL / Redis  →  ./deploy-guangzhou.sh
#   香港 = 出海网关（web + pansou + caddy） →  ./deploy-hk-gateway.sh
# ============================================
set -euo pipefail

cat >&2 <<'EOF'
[deploy-prod] 已停用，禁止再往香港发 backend / mysql / redis。

请改用：
  广州后端/后台：  ./deploy-guangzhou.sh {backend|admin|web|all|…}
  香港网关前台：  ./deploy-hk-gateway.sh {all|web|pansou|cutover|…}

详见 docs/部署架构.md
EOF
exit 1
