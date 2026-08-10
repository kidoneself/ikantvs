# ikantvs

网盘影视资源信息流平台（开源壳）。

**夸克热榜灌片库** + **pansou 搜链** + 每日更新 / 手工建片。  
一键部署说明：**[docs/部署.md](docs/部署.md)**（宝塔粘贴 compose，或命令行两行 curl）。

## 快速开始

```bash
mkdir -p /opt/ikantvs && cd /opt/ikantvs
curl -fsSL -o docker-compose.yml \
  https://raw.githubusercontent.com/kidoneself/ikantvs/main/deploy/docker-compose.yml
curl -fsSL -o .env \
  https://raw.githubusercontent.com/kidoneself/ikantvs/main/deploy/.env.example
# 编辑 .env：密码、JWT、CORS_ALLOWED_ORIGINS=http://公网IP:3080
docker compose up -d
```

| | |
|--|--|
| 前台 | `http://IP:3080` |
| 后台 | `http://IP:3081`（`admin` / `admin123`，请改密） |

镜像 `ghcr.io/kidoneself/ikantvs`：后端 + 前台 + 后台 + 建表，无需本机 build。  
详细步骤、关 pansou、绑域名、排错 → [docs/部署.md](docs/部署.md)。

## 能力

| 功能 | 开箱状态 | 依赖 |
|------|----------|------|
| 夸克热榜灌片库 | 默认开 | 公开夸克热榜接口 |
| pansou 搜链 | 默认开，可关 | `pansou` 容器（费内存） |
| Gying 搜链 | 默认关 | 后台「资源采集」填账号；env 仅首次种子 |
| 每日更新 | 可用 | 后台选片/手填 + 贴分享链 |
| 手工建片 | 可用 | 后台建片；海报可本地上传 |
| 一键转存 / 追更 | 可选 | 自备夸克 / 百度 / 迅雷账号 |
| 飞书口令机器人 | 可选 | 自备飞书应用（app-id/secret） |
| Cloudflare R2 海报 | 默认关 | 自备 R2 |
| 云端片库同步 | **未实现** | 规划中；开了 env 也不会拉数据 |

镜像未发或 pull 失败时，可本机 build，见 [docs/部署.md](docs/部署.md)。

## 目录

`server/` 后端 · `web/` 前台 · `admin/` 后台 · `deploy/` 编排

## 分支

`main` 开源 · `dev` 自用

## 免责

仅提供软件；内容与合规由部署者负责。勿提交真实 `.env` / 备份 / cookie。

## License

[MIT](LICENSE)
