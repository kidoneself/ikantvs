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

| | 说明 |
|--|------|
| 夸克热榜 | 启动时分页灌进影视库 |
| pansou | 点片名搜网盘链（可关省内存） |
| 每日更新 | 选片或手填 + 贴链 |
| 手工录入 | 后台建片 + 本地上传海报 |
| 转存 | 可选配置夸克 / 百度 / 迅雷账号 |
| R2 | 可选，远程镜像海报 |

## 目录

`server/` 后端 · `web/` 前台 · `admin/` 后台 · `deploy/` 编排

## 分支

`main` 开源 · `dev` 自用

## 免责

仅提供软件；内容与合规由部署者负责。勿提交真实 `.env` / 备份 / cookie。

## License

[MIT](LICENSE)
