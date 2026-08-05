# ikantvs

网盘影视资源信息流平台（开源壳）。

**推荐：宝塔管域名/证书 + Docker 跑业务**（不抢 80/443）。  
默认用夸克热榜灌片库，点片名 pansou 搜链；后台可加每日更新。

## 快速开始（宝塔，推荐）

1. 宝塔安装 **Docker**
2. 服务器上：

```bash
cd /www/wwwroot   # 或任意目录
git clone https://github.com/kidoneself/ikantvs.git
cd ikantvs/deploy
./deploy-baota.sh init
# 编辑 .env.baota：密码 / JWT_SECRET / CORS_ALLOWED_ORIGINS=https://你的域名
./deploy-baota.sh up
```

3. 宝塔添加网站 → SSL → 反代到 `127.0.0.1:3080`，并配置 `/api` → `3088`  
   **图文步骤：[docs/宝塔部署.md](docs/宝塔部署.md)**

| 入口 | 地址 |
|------|------|
| 前台 | `https://你的域名`（宝塔） |
| 后台 | `http://服务器IP:3081`（默认 `admin` / `admin123`，务必改密） |

## 开源默认能力

| 能力 | 说明 |
|------|------|
| **夸克热榜 → media** | 定时同步（无搜索，仅排行） |
| **pansou 搜链** | 点卡片用片名搜网盘资源 |
| **每日更新** | 选片或手填片名新建，再贴上游链 |

可选：申请 [TMDB Key](https://www.themoviedb.org/settings/api) 填 `TMDB_API_KEY`。

内存紧可关 pansou：`.env.baota` 里 `ENABLE_PANSOU=false`。

## 附录：无宝塔的海外 VPS（Caddy 自动 HTTPS）

适合没有面板、自己管域名的机器：

```bash
cd ikantvs/deploy
./deploy-single.sh init
# 编辑 .env.single：SITE_DOMAIN / ACME_EMAIL / 密码 / JWT
./deploy-single.sh up
```

详见 [`docs/部署架构.md`](docs/部署架构.md)。

## 本地开发（可选）

```bash
cd deploy && docker compose up -d          # MySQL + Redis
cd ../server && mvn spring-boot:run
cd ../web && pnpm i && pnpm dev            # :5173
cd ../admin && pnpm i && pnpm dev          # :5174
```

## 目录

```
ikantvs/
├── server/   Spring Boot 3
├── web/      前台 Vue 3
├── admin/    后台 Vue 3
├── deploy/   Docker / 宝塔与单机脚本
└── docs/     部署与设计
```

## 分支

| 分支 | 用途 |
|------|------|
| **`main`** | 开源壳 |
| **`dev`** | 维护者自用 |

## 边界与免责

- 无前台注册、无会员计费。
- 夸克热榜为第三方公开接口，稳定性不保证。
- **部署者须自行确保内容与运营合法合规**。
- 勿提交真实 `.env`、SQL 备份、cookie。

## License

[MIT](LICENSE)
