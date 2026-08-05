# 爱看 SEO 边缘 Worker

给爬虫/社交分享补齐详情页首屏 `<head>`，并动态生成 `sitemap.xml`。**不改后端、不改架构**，
只调用后端已有接口 `GET /api/media/{id}` 与 `GET /api/media`。

## 它做了什么

- `GET https://ikantvs.com/detail/:id`：注入 `<title>`、`description`、OpenGraph、`canonical`、
  JSON-LD（`Movie` / `TVSeries`）。用户与爬虫拿到同一份 HTML（非 cloaking）。
- `GET https://ikantvs.com/sitemap.xml`：用 `/api/media` 列表生成站点地图（首页 + 榜单 + 详情页）。
- 其余路径（首页、搜索、静态资源）不在路由内，直接走原站。首页/搜索默认 meta 写在 `web/index.html`。

## 部署（一次性）

前置：域名 `ikantvs.com` 已托管在 Cloudflare（小黄云代理）。

```bash
cd edge/seo-worker
npm i -g wrangler          # 或 npx wrangler ...
wrangler login             # 浏览器授权到你的 Cloudflare 账号
wrangler deploy            # 按 wrangler.toml 里的 routes 发布
```

`wrangler deploy` 会自动把两个 Worker 路由挂到 `ikantvs.com/detail/*` 和 `ikantvs.com/sitemap.xml`。
如账号里有多个 zone，首次会让你确认 `ikantvs.com` 的 zone。

## 验证

```bash
# 详情页应能在源码里看到真实片名/简介，而不是空壳
curl -s https://ikantvs.com/detail/1 | grep -Eo '<title>[^<]*</title>|og:description'

# 站点地图
curl -s https://ikantvs.com/sitemap.xml | head
```

也可以在浏览器「查看网页源代码」（不是审查元素）看 `/detail/:id`，`<head>` 里应有注入的 meta 与 JSON-LD。

## 收录（部署后做）

1. 百度站长平台：验证 `ikantvs.com` → 提交 `https://ikantvs.com/sitemap.xml` → 开启「主动推送」。
2. Google Search Console：验证 → 提交同一个 sitemap。

## 本地开发

```bash
cd edge/seo-worker
wrangler dev            # 本地跑，回源到线上 ikantvs.com
```
