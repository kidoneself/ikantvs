/**
 * 老站同款：点卡片/热搜进结果导向搜索时，剥掉季后缀，避免「绝望写手 第五季」搜不全。
 * 仅用于搜索关键词，展示标题仍用原 title。
 */
export function stripSeasonSuffix(title: string): string {
  return title
    .replace(/\s*(第[一二三四五六七八九十百零0-9]+季)\s*$/i, '')
    .replace(/\s*Season\s*\d+\s*$/i, '')
    .replace(/\s+S\d{1,2}\s*$/i, '')
    .trim()
}

/** 进老站式资源搜索页 /find?q= */
export function findQuery(title: string): { name: 'find'; query: { q: string } } {
  const q = stripSeasonSuffix(title) || title.trim()
  return { name: 'find', query: { q } }
}
