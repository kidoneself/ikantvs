# ikantvs

网盘影视资源信息流平台（开源壳）。

夸克热榜灌片库 + pansou 搜链；后台可加每日更新。  
部署：**宝塔粘贴一份 compose 即可**，见 [docs/部署.md](docs/部署.md)。

1. 复制 [`deploy/docker-compose.yml`](deploy/docker-compose.yml) 到宝塔 Docker → 编排  
2. 配置 `.env`（密码 / JWT / `CORS_ALLOWED_ORIGINS=http://IP:3080`）  
3. 启动 → `http://IP:3080` 前台 · `http://IP:3081` 后台  

镜像：`ghcr.io/kidoneself/ikantvs`（含后端 + 前台 + 后台 + 建表，无需本机 build）。

## 能力

| | 说明 |
|--|------|
| 夸克热榜 | 默认灌进 media |
| pansou | 点片名搜链（可关省内存） |
| 每日更新 | 选片或手填片名 + 贴链 |
| TMDB | 可选 |

## 目录

`server/` 后端 · `web/` 前台 · `admin/` 后台 · `deploy/` 编排

## 分支

`main` 开源 · `dev` 自用

## 免责

仅提供软件；内容与合规由部署者负责。勿提交真实 `.env` / 备份 / cookie。

## License

[MIT](LICENSE)
