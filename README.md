# ikantvs

网盘影视资源信息流平台（开源壳）。

夸克热榜灌片库 + pansou 搜链；后台可加每日更新。  
部署：**宝塔 + Docker**，见 [docs/部署.md](docs/部署.md)。

```bash
git clone https://github.com/kidoneself/ikantvs.git
cd ikantvs/deploy
./deploy.sh init          # 改密码 / JWT / CORS 域名
./deploy.sh up
# 宝塔：加站点 → SSL → 反代 127.0.0.1:3080，配置 /api → 3088
```

| 入口 | 地址 |
|------|------|
| 前台 | `https://你的域名` |
| 后台 | `http://服务器IP:3081`（admin / admin123，务必改密） |

## 能力

| | 说明 |
|--|------|
| 夸克热榜 | 默认灌进 media |
| pansou | 点片名搜链（可关省内存） |
| 每日更新 | 选片或手填片名 + 贴链 |
| TMDB | 可选 |

## 目录

`server/` 后端 · `web/` 前台 · `admin/` 后台 · `deploy/` 部署

## 分支

`main` 开源 · `dev` 自用

## 免责

仅提供软件；内容与合规由部署者负责。勿提交真实 `.env` / 备份 / cookie。

## License

[MIT](LICENSE)
