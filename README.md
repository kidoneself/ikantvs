# jyinshi-next

网盘影视资源信息流平台（开源壳）。

**默认交付路径：一台海外 VPS 跑整站。**  
克隆仓库 → 填域名 → 一条命令起来；要完整片库再填**一份上游 API 链接 + Key**。

## 推荐：海外单机（给别人用的正路）

适合香港 / 新加坡 / 美西等任意一台能出网的 VPS（建议 **2 核 4G+**）。

```bash
# 在服务器上
git clone https://github.com/kidoneself/ikantvs.git && cd ikantvs/deploy
./deploy-single.sh init
# 编辑 .env.single：SITE_DOMAIN / ACME_EMAIL / 密码 / JWT_SECRET
# DNS：A 记录指向该机；放行 80/443（建议 UDP 443）
./deploy-single.sh up
```

起来后：

| 入口 | 地址 |
|------|------|
| 前台 | `https://你的域名` |
| API | `https://你的域名/api/health` |
| 后台 | `http://服务器IP:8080`（默认 `admin` / `admin123`，务必改密） |

空库会自动执行 [`deploy/init/`](deploy/init/)（最终态 schema + 演示数据），**不用**再跑 55 个历史迁移。

### 两种数据模式

| 模式 | `.env.single` | 说明 |
|------|---------------|------|
| **本地演示**（默认） | 不填云端 | 只有演示片，可自己开 pansou 采集 |
| **对接云端** | 见下 | 片库/取链走运营方（「一份链接」） |

```bash
JYINSHI_CLOUD_ENABLED=true
JYINSHI_CLOUD_BASE_URL=https://api.example.com/api
JYINSHI_CLOUD_API_KEY=sk_xxx

# 只要云端、自己不采集时，可关 pansou 省约 1G 内存：
ENABLE_PANSOU=false
INGEST_ENABLED=false
```

> 云端同步/取链代理仍在接入中；配置与启动日志已就绪。

笔记本远程往 VPS 发也可以：

```bash
export DEPLOY_SERVER=root@x.x.x.x
# export SSHPASS='...'   # 或已配 SSH key
./deploy-single.sh all
```

模板：[`deploy/.env.single.example`](deploy/.env.single.example)  
细节：[`docs/部署架构.md`](docs/部署架构.md) §0

## 开发机（可选）

只起 MySQL + Redis，本机跑前后端：

```bash
cd deploy && docker compose up -d
cd ../server && mvn spring-boot:run
cd ../web && pnpm i && pnpm dev      # :5173
cd ../admin && pnpm i && pnpm dev    # :5174
```

根目录 [`.env.example`](.env.example) 供本地环境变量参考。

## 数据库

| 路径 | 用途 |
|------|------|
| [`deploy/init/`](deploy/init/) | **新装空库**：一份 schema + 演示种子 |
| [`deploy/migrations/`](deploy/migrations/) | **已有库增量**：只追加 `V056+` |

## 目录

```
jyinshi-next/
├── server/   后端 Spring Boot 3
├── web/      前台 Vue 3
├── admin/    后台 Vue 3
├── deploy/   海外单机 compose / Caddy / init
└── docs/     设计与产品文档
```

## 边界

- 无前台用户、无会员计费；变现走网盘转存推广。
- 开源壳默认海外单机；运营方自用的多机拓扑不在本仓库维护。
- 勿提交真实 `.env`、SQL 备份、cookie。
