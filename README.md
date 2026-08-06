# ikantvs

网盘影视资源信息流平台（开源壳）。

夸克热榜灌片库 + pansou 搜链；后台可加每日更新。  
部署：宝塔拉取预构建镜像即可，见 [docs/部署.md](docs/部署.md)。

```bash
# 把 deploy/ 放到服务器后：
cd deploy
cp .env.example .env    # 改密码 / JWT；CORS 先写 http://IP:3080
docker compose --env-file .env --profile pansou pull
docker compose --env-file .env --profile pansou up -d
```

| 入口 | 地址 |
|------|------|
| 前台 | `http://服务器IP:3080`（有域名再宝塔反代） |
| 后台 | `http://服务器IP:3081`（admin / admin123，改密） |

业务镜像：`ghcr.io/kidoneself/ikantvs`（一个镜像含后端 + 前台 + 后台）。

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
