/**
 * 爱看 SEO 边缘 Worker（Cloudflare）
 *
 * 作用：不改后端、不改架构，只在边缘为爬虫/分享补齐首屏 <head>。
 *  - GET /detail/:id  → 调后端已有接口 /api/media/{id}，把 title/description/OG/canonical/JSON-LD
 *                       注入静态 index.html 再返回（用户与爬虫拿到同一份，非 cloaking）。
 *  - GET /sitemap.xml → 用 /api/media 列表动态生成站点地图。
 *
 * 其余路由（含静态资源、首页、搜索）不在本 Worker 路由内，直接走原站，零额外开销。
 * 首页/搜索的默认 meta 已写死在 web/index.html。
 *
 * 说明：Cloudflare 内置回环保护——Worker 内 fetch() 的子请求直接回源，不会再次触发本 Worker。
 */

const SITE = 'https://ikantvs.com'
const API_BASE = 'https://api.ikantvs.com/api'
const BRAND = '爱看'

const TYPE_TO_CAT = { movie: '电影', tv: '剧集', anime: '动漫', variety: '综艺' }

export default {
  async fetch(request) {
    const url = new URL(request.url)

    if (url.pathname === '/sitemap.xml') {
      return handleSitemap()
    }

    if (request.method !== 'GET') {
      return fetch(request)
    }

    const originResp = await fetch(request)
    const ct = originResp.headers.get('content-type') || ''
    if (!originResp.ok || !ct.includes('text/html')) {
      return originResp
    }

    const detail = url.pathname.match(/^\/detail\/(\d+)/)
    if (!detail) {
      return originResp
    }

    const meta = await buildDetailMeta(detail[1])
    if (!meta) {
      return originResp
    }
    return injectMeta(originResp, meta)
  },
}

/* ----------------------------- 详情页 meta ----------------------------- */

async function buildDetailMeta(id) {
  const data = await apiJson(`/media/${id}`)
  if (!data) return null
  const m = data.media || data
  if (!m || !m.id) return null

  const cat = TYPE_TO_CAT[m.type || 'movie'] || '影视'
  const year = m.year || (m.releaseDate ? String(m.releaseDate).slice(0, 4) : '')
  const genres = Array.isArray(m.genres) ? m.genres.filter(Boolean) : []
  const gLabel = genres.slice(0, 2).join('/')

  const name = m.title || '未命名'
  const nameYear = year ? `${name}（${year}）` : name
  const title = `${name}${year ? ` (${year})` : ''}${gLabel ? ` ${gLabel}` : ''} ${cat}网盘资源下载 - ${BRAND}`

  const rawDesc = (m.overview || '').replace(/\s+/g, ' ').trim()
  const description = rawDesc
    ? clip(rawDesc, 140)
    : `${nameYear} ${cat}网盘资源，聚合夸克网盘、百度网盘等多来源分享链接，一键复制转存，尽在${BRAND}。`

  const image = m.poster || m.posterThumb || ''
  const canonical = `${SITE}/detail/${m.id}`
  const ogType = m.type === 'movie' ? 'video.movie' : 'video.tv_show'

  const jsonld = {
    '@context': 'https://schema.org',
    '@type': m.type === 'movie' ? 'Movie' : 'TVSeries',
    name,
    url: canonical,
  }
  if (image) jsonld.image = image
  if (description) jsonld.description = description
  if (genres.length) jsonld.genre = genres
  if (year) jsonld.datePublished = String(m.releaseDate || year)
  if (m.directors && m.directors.length) {
    jsonld.director = m.directors.map((n) => ({ '@type': 'Person', name: n }))
  }
  if (m.actors && m.actors.length) {
    jsonld.actor = m.actors.slice(0, 10).map((n) => ({ '@type': 'Person', name: n }))
  }

  const appendHtml =
    (image
      ? `<meta property="og:image" content="${escAttr(image)}">` +
        `<meta name="twitter:image" content="${escAttr(image)}">`
      : '') +
    `<script type="application/ld+json">${jsonSafe(jsonld)}</script>`

  return { title, description, canonical, ogType, appendHtml }
}

/** 用 HTMLRewriter 就地改写已有标签，避免重复的 meta。 */
function injectMeta(resp, meta) {
  return new HTMLRewriter()
    .on('title', new InnerSetter(meta.title))
    .on('meta[name="description"]', new AttrSetter('content', meta.description))
    .on('link[rel="canonical"]', new AttrSetter('href', meta.canonical))
    .on('meta[property="og:title"]', new AttrSetter('content', meta.title))
    .on('meta[property="og:description"]', new AttrSetter('content', meta.description))
    .on('meta[property="og:url"]', new AttrSetter('content', meta.canonical))
    .on('meta[property="og:type"]', new AttrSetter('content', meta.ogType))
    .on('meta[name="twitter:title"]', new AttrSetter('content', meta.title))
    .on('meta[name="twitter:description"]', new AttrSetter('content', meta.description))
    .on('head', new Appender(meta.appendHtml))
    .transform(resp)
}

/* ------------------------------ sitemap ------------------------------- */

async function handleSitemap() {
  const size = 200
  const maxUrls = 5000
  const first = await apiJson(`/media?page=1&size=${size}&sort=new`)
  const total = first && typeof first.total === 'number' ? first.total : 0
  let records = (first && first.records) || []

  const pages = Math.min(Math.ceil(total / size), Math.ceil(maxUrls / size))
  const tasks = []
  for (let p = 2; p <= pages; p++) {
    tasks.push(apiJson(`/media?page=${p}&size=${size}&sort=new`))
  }
  for (const r of await Promise.all(tasks)) {
    if (r && r.records) records = records.concat(r.records)
  }

  const staticUrls = [
    `<url><loc>${SITE}/</loc><changefreq>daily</changefreq><priority>1.0</priority></url>`,
    `<url><loc>${SITE}/ranking</loc><changefreq>daily</changefreq><priority>0.8</priority></url>`,
  ].join('')

  const items = records
    .filter((m) => m && m.id)
    .map((m) => {
      const lastmod = m.updatedAt ? `<lastmod>${String(m.updatedAt).slice(0, 10)}</lastmod>` : ''
      return `<url><loc>${SITE}/detail/${m.id}</loc>${lastmod}<changefreq>weekly</changefreq></url>`
    })
    .join('')

  const xml =
    `<?xml version="1.0" encoding="UTF-8"?>` +
    `<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${staticUrls}${items}</urlset>`

  return new Response(xml, {
    headers: {
      'content-type': 'application/xml; charset=utf-8',
      'cache-control': 'public, max-age=3600',
    },
  })
}

/* ------------------------------ 工具 ---------------------------------- */

async function apiJson(path) {
  try {
    const r = await fetch(`${API_BASE}${path}`, {
      // 后端开启了来源校验（CORS_ENFORCE_ORIGIN），必须带允许的 Origin。
      headers: { Origin: SITE },
      cf: { cacheTtl: 600, cacheEverything: true },
    })
    if (!r.ok) return null
    const j = await r.json()
    // 后端统一信封 { code, message, data }，取 data；兼容裸响应。
    if (j && typeof j === 'object' && 'code' in j) {
      return j.code === 0 ? j.data : null
    }
    return j
  } catch {
    return null
  }
}

function clip(s, n) {
  const str = String(s)
  return str.length > n ? str.slice(0, n).trimEnd() + '…' : str
}

function escAttr(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function jsonSafe(obj) {
  return JSON.stringify(obj).replace(/</g, '\\u003c')
}

class AttrSetter {
  constructor(attr, val) {
    this.attr = attr
    this.val = val
  }
  element(el) {
    if (this.val != null) el.setAttribute(this.attr, this.val)
  }
}

class InnerSetter {
  constructor(val) {
    this.val = val
  }
  element(el) {
    el.setInnerContent(this.val)
  }
}

class Appender {
  constructor(html) {
    this.html = html
  }
  element(el) {
    if (this.html) el.append(this.html, { html: true })
  }
}
