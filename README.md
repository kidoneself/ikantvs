# ikantvs

网盘影视资源信息流平台（开源壳）。

克隆 → 填域名 → 一条命令 Docker 起来；要完整片库再填**上游 API 链接 + Key**（云端对接开发中）。

## 快速开始（海外单机）

适合香港 / 新加坡 / 美西等 **2 核 4G+** VPS。

```bash
git clone https://github.com/kidoneself/ikantvs.git
cd ikantvs/deploy
./deploy-single.sh init
# 编辑 .env.single：SITE_DOMAIN / ACME_EMAIL / 密码 / JWT_SECRET
# DNS：A 记录指向 VPS；放行 80/443
./deploy-single.sh up
```

| 入口 | 地址 |
|------|------|
| 前台 | `https://你的域名` |
| API | `https://你的域名/api/health` |
| 后台 | `http://服务器IP:8080`（默认 `admin` / `admin123`，**务必改密**） |

空库自动执行 [`deploy/init/`](deploy/init/)（schema + 演示数据）。

### 数据模式

| 模式 | 配置 | 说明 |
|------|------|------|
| **本地演示**（默认） | 不填云端 | 仅演示片；可开 pansou 自采集 |
| **对接云端** | `JYINSHI_CLOUD_*` | 片库/取链走运营方 API（接入中） |

```bash
JYINSHI_CLOUD_ENABLED=true
JYINSHI_CLOUD_BASE_URL=https://api.example.com/api
JYINSHI_CLOUD_API_KEY=sk_xxx

# 纯云端、不自采集：
ENABLE_PANSOU=false
INGEST_ENABLED=false
```

远程发版：`DEPLOY_SERVER=root@IP ./deploy-single.sh all`

模板：[`deploy/.env.single.example`](deploy/.env.single.example)  
部署细节：[`docs/部署架构.md`](docs/部署架构.md)

## 本地开发（可选）

```bash
cd deploy && docker compose up -d          # MySQL + Redis
cd ../server && mvn spring-boot:run
cd ../web && pnpm i && pnpm dev            # :5173
cd ../admin && pnpm i && pnpm dev          # :5174
```

根目录 [`.env.example`](.env.example) 供本地环境变量参考。

## 目录

```
ikantvs/
├── server/   Spring Boot 3 后端
├── web/      前台 Vue 3
├── admin/    后台 Vue 3
├── deploy/   单机 Docker / Caddy / init
└── docs/     架构与设计
```

## 分支说明

| 分支 | 用途 |
|------|------|
| **`main`** | 开源壳（本分支），脱敏、可公开 |
| **`dev`** | 维护者自用，含生产脚本（不对外交付） |

## 边界与免责

- 无前台用户注册、无会员计费。
- 本仓库**仅提供软件与部署能力**，不包含影视资源库数据。
- **部署者须自行确保内容来源与运营的合法合规**；开发者不对第三方资源与使用者行为负责。
- 勿提交真实 `.env`、SQL 备份、网盘 cookie。

## License

[MIT](LICENSE)
