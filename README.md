# ikantvs

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](server/pom.xml)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1-green.svg)](server/pom.xml)
[![Vue](https://img.shields.io/badge/Vue-3-brightgreen.svg)](web/package.json)

**网盘影视资源信息流平台** —— 夸克热榜灌片库、多源搜链、每日更新、运营后台；支持夸克 / 百度 / 迅雷一键转存（可选）。Docker 一键部署，不绑定作者私有上游。

---

## 温馨提示

- 本项目**仅提供软件**，自身不托管、不内置任何影视文件或分享链接库。
- **请勿用于任何违法用途**；站点内容、账号与合规由部署者自行负责。
- 片库默认来自公开夸克热榜接口；搜链依赖你自行启用的 pansou / Gying 等来源，作者不保证第三方接口长期可用。
- 遇到问题请先查阅文档并自行排查；欢迎 Issue / PR。

---

## 法律声明与使用协议

使用本项目即表示你同意：

1. 本项目为开源软件，按 MIT 许可提供，仅供学习、研究与合法自用部署；
2. 项目未附带版权影视资源、下载地址或商业片源授权；
3. 作者不支持、不协助任何侵犯版权或传播侵权内容的行为；
4. 若将本项目用于违法违规网站或传播侵权资源，责任由使用者自行承担，与作者无关；
5. 作者不对因使用本项目产生的任何直接或间接后果承担法律责任；
6. 若不同意上述条款，请勿下载、使用或传播本项目。

---

## 功能一览

| 功能 | 状态 | 说明 |
|------|------|------|
| 夸克热榜灌片库 | 默认开启 | 启动后分页写入本地影视库 |
| pansou 搜链 | 默认开启 | 点片名搜网盘链接；可关容器省内存 |
| Gying 搜链 | 默认关闭 | 后台「资源采集」填写账号后启用 |
| 每日更新 | 可用 | 选片或手填 + 粘贴分享链 |
| 手工建片 | 可用 | 后台建片；海报支持本地上传 |
| 一键转存 / 追更 | 可选 | 自备夸克 / 百度 / 迅雷账号 |
| 飞书口令机器人 | 可选 | 自备飞书应用 |
| Cloudflare R2 海报 | 默认关闭 | 可选远程镜像；不配则用本地上传 / 热榜原图 |
| 云端片库同步 | 未实现 | 配置占位；开启也不会拉数 |

---

## 快速部署

需要已安装 [Docker](https://docs.docker.com/get-docker/) 与 Docker Compose。完整说明（宝塔、域名、排错）见 **[docs/部署.md](docs/部署.md)**；装好后怎么用见 **[docs/使用说明.md](docs/使用说明.md)**。

```bash
mkdir -p /opt/ikantvs && cd /opt/ikantvs

curl -fsSL -o docker-compose.yml \
  https://raw.githubusercontent.com/kidoneself/ikantvs/main/deploy/docker-compose.yml
curl -fsSL -o .env \
  https://raw.githubusercontent.com/kidoneself/ikantvs/main/deploy/.env.example

# 至少修改：MYSQL_ROOT_PASSWORD、DB_PASSWORD、JWT_SECRET、
# CORS_ALLOWED_ORIGINS=http://你的公网IP:3080
docker compose up -d
```

| 入口 | 地址 |
|------|------|
| 前台 | `http://<服务器IP>:3080` |
| 后台 | `http://<服务器IP>:3081`（默认 `admin` / `admin123`，**请立即改密**） |

业务镜像：`ghcr.io/kidoneself/ikantvs`（后端 + 前台 + 后台 + 建表 SQL）。  
GitHub Package 需为 **Public**，否则匿名 `pull` 会 401。镜像不可用时可按部署文档从源码构建。

**装好后建议：** 改密 → 系统设置（网盘展示 / 公告 / 资源采集）→ 等夸克灌库日志出现 `[quark-ranking] 同步完成`。

---

## 文档

| 文档 | 说明 |
|------|------|
| [部署指南](docs/部署.md) | 安装、升级、排错 |
| [使用说明](docs/使用说明.md) | 后台配置、片库、搜链、每日更新、转存 |
| [重做设计方案](docs/重做设计方案.md) | 架构与业务域划分 |
| [资源聚合与检测设计](docs/资源聚合与检测设计.md) | 搜链与入库设计 |

---

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 17 · Spring Boot · MyBatis-Plus · MySQL · Redis |
| 前台 / 后台 | Vue 3 · Vite · Element Plus |
| 部署 | Docker Compose · 一体化业务镜像 |

## 仓库结构

```
ikantvs/
├── server/     # 后端 API
├── web/        # 用户前台
├── admin/      # 运营后台
├── deploy/     # Compose、镜像与建表脚本
└── docs/       # 部署与设计文档
```

## 配置说明

- 常用变量：[`deploy/.env.example`](deploy/.env.example)
- pansou / Gying / 公告 / 网盘展示等：后台 **系统 → 系统设置**（保存即生效；env 仅首次种子）
- **请勿**提交真实 `.env`、cookie、备份或密钥

---

## 贡献

欢迎 Issue 与 Pull Request。提交前请确认：

- 不引入真实域名、IP、密钥或账号密码
- 改动保持可编译、可运行
- 说明动机与验证方式

---

## License

[MIT](LICENSE) © kidoneself
