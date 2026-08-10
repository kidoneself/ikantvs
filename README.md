# ikantvs

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](server/pom.xml)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1-green.svg)](server/pom.xml)
[![Vue](https://img.shields.io/badge/Vue-3-brightgreen.svg)](web/package.json)

网盘影视资源**信息流**平台（开源版）。提供片库浏览、聚合搜链、每日更新与运营后台；一键 Docker 部署，不依赖作者私有上游。

> 本仓库只提供软件本身。分享链接、账号与站点内容的合规责任由部署者自行承担。

**文档：** [部署指南](docs/部署.md) · [使用说明](docs/使用说明.md)

## 功能

| 功能 | 状态 | 说明 |
|------|------|------|
| 夸克热榜灌片库 | 默认开启 | 启动后分页写入本地影视库 |
| pansou 搜链 | 默认开启 | 点片名搜网盘链接；可关容器省内存 |
| Gying 搜链 | 默认关闭 | 后台「系统设置 → 资源采集」填写账号后启用 |
| 每日更新 | 可用 | 选片或手填 + 粘贴分享链 |
| 手工建片 | 可用 | 后台建片；海报支持本地上传 |
| 一键转存 / 追更 | 可选 | 自备夸克 / 百度 / 迅雷账号 |
| 飞书口令机器人 | 可选 | 自备飞书应用 |
| Cloudflare R2 海报 | 默认关闭 | 自备 R2 |
| 云端片库同步 | 未实现 | 配置项占位；开启也不会拉数 |

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 17 · Spring Boot · MyBatis-Plus · MySQL · Redis |
| 前台 / 后台 | Vue 3 · Vite · Element Plus |
| 部署 | Docker Compose · 一体化业务镜像（含建表 SQL） |

## 快速开始

需要已安装 [Docker](https://docs.docker.com/get-docker/) 与 Docker Compose。

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

业务镜像：`ghcr.io/kidoneself/ikantvs`（含后端、前台、后台与初始化 SQL）。  
Package 需为 **Public**，否则匿名 `pull` 会 401。

宝塔编排、关 pansou、绑定域名、排错与环境变量说明见 **[docs/部署.md](docs/部署.md)**。

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

- 部署常用变量见 [`deploy/.env.example`](deploy/.env.example) 与 [部署文档](docs/部署.md)。
- 运行期开关（pansou / Gying / 公告 / 网盘展示等）在后台 **系统 → 系统设置** 修改，保存后即时生效；对应环境变量仅作**首次启动种子**。
- 请勿将真实 `.env`、cookie、备份或密钥提交到 Git。

## 从源码构建

若暂无可用镜像，可 clone 本仓库后按 `deploy/Dockerfile.app` 与 Compose 自行构建。细节见 [docs/部署.md](docs/部署.md)。

## 文档

| 文档 | 说明 |
|------|------|
| [部署指南](docs/部署.md) | 安装、升级、排错 |
| [使用说明](docs/使用说明.md) | 后台配置、片库、搜链、每日更新、转存 |
| [重做设计方案](docs/重做设计方案.md) | 架构与业务域划分 |
| [资源聚合与检测设计](docs/资源聚合与检测设计.md) | 搜链与入库设计 |

## 贡献

欢迎 Issue 与 Pull Request。提交前请确认：

- 不引入真实域名、IP、密钥或账号密码
- 改动保持可编译、可运行
- 说明动机与验证方式

## 免责声明

本项目按「原样」提供，作者不对部署结果、第三方网盘/接口可用性或由此产生的任何后果负责。使用者须遵守所在地法律法规及各网盘服务条款。

## License

[MIT](LICENSE) © kidoneself
