# ikantvs

网盘影视资源信息流平台（开源壳）。

克隆 → 填域名 → Docker 起来。默认用**夸克热榜**灌片库，点片名用 **pansou** 搜链；后台可加**每日更新**。

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

空库自动执行 [`deploy/init/`](deploy/init/)；启动后会从夸克热榜同步一批片到本地库（需机器能访问外网）。

## 开源默认能力（刻意保持简单）

| 能力 | 说明 |
|------|------|
| **夸克热榜 → media** | 定时同步电影/剧/综艺/动漫排行（**无搜索接口**） |
| **pansou 搜链** | 点卡片用片名搜网盘资源 |
| **每日更新** | 后台选片或**手填片名新建**，再贴上游分享链 |

可选：自己申请 [TMDB API Key](https://www.themoviedb.org/settings/api) 填进 `TMDB_API_KEY`，后台补录会更全；不填也能用。

```bash
# .env.single
QUARK_RANKING_ENABLED=true   # 默认开
TMDB_API_KEY=                # 可选
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
| **`main`** | 开源壳（本分支） |
| **`dev`** | 维护者自用 |

## 边界与免责

- 无前台用户注册、无会员计费。
- 夸克热榜为第三方公开接口，稳定性不保证；片库数据由部署者自行维护。
- **部署者须自行确保内容来源与运营的合法合规**。
- 勿提交真实 `.env`、SQL 备份、网盘 cookie。

## License

[MIT](LICENSE)
