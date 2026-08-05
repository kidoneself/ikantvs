#!/usr/bin/env python3
"""
二次迁移：老库 search_cache 里【没有 tmdb_id】的关键字 → 新库「素条目」+ 网盘链接。

背景：
  首次迁移(migrate_links_from_search_cache.py) 只迁了 tmdb_id 命中新库 media 的行，
  丢掉了 3800+ 个「没匹配上 TMDB 但用户猛搜的真片」(如 逐玉/飞驰人生3/剑来第二季)。
  本脚本把这批补回来，保住老站「搜啥都有」的自由度。

策略（对每个无 tmdb 关键字）：
  1) 清洗标题：去掉 【标签】「」等装饰；电视剧/动漫/综艺抹掉「第X季/第X部」并进正剧。
  2) 先按标题匹配新库已有 media：命中 → 链接直接挂上去（给现有条目补货，不建重复条目）。
  3) 没命中 → 建「素条目」(meta_source='none', tmdb_id=NULL, pub_status=1, 无海报)，再挂链接。
  4) 链接清洗/失效库/密码合并复用首次迁移脚本；share_id 严格照抄 Java ShareIdExtractor，
     入库走 ON DUPLICATE KEY UPDATE（唯一键 media_id+pan_type+share_id），重复只更新不新增。

素条目约定：
  - meta_source='none'  → 可被「补熟」流程后续回填 TMDB 元数据。
  - pub_status=1        → 搜得到（信息流是否展示由接口层过滤 meta_source 控制，另行改动）。
  - type = category(movie/tv/anime/variety)，其余(other/null) → 'other'。
  - media_link.source='migrate'。

季号归一化（只抹「第X季/第X部/SX/Season X」这类明确季标记，全类别通用）：
  - 「剑来第二季」「剑来 第二季」→ 桶「剑来」；「一人之下第六季」→「一人之下」。
  - 裸数字不抹：「飞驰人生3」「问心2」保持独立——无法可靠区分「剧的续季」与「电影续集」，
    抹了会把不同电影的链接混成一桶，故保守保留。

环境变量同 migrate_links_from_search_cache.py（OLD_DB_* / NEW_DB_*）。

示例（在生产服务器 docker 网络内跑）：
  pip install pymysql
  python3 scripts/migrate_plain_media_from_search_cache.py --dry-run
  python3 scripts/migrate_plain_media_from_search_cache.py --dry-run --min-search 20 --min-links 3
  python3 scripts/migrate_plain_media_from_search_cache.py --apply
"""
from __future__ import annotations

import argparse
import hashlib
import re
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

try:
    import pymysql
except ImportError:
    print("请先安装: pip install pymysql", file=sys.stderr)
    sys.exit(1)

from migrate_links_from_search_cache import (  # noqa: E402  复用首次迁移的清洗逻辑
    SKIP_PAN_TYPES,
    VALID_MEDIA_TYPES,
    build_note,
    connect,
    db_config,
    detect_pan_type,
    extract_links_from_json,
    is_invalid_link,
    load_invalid_sets,
    merge_password,
)

# ---------------- 标题清洗 ----------------
_TAG_RE = re.compile(r"[【\[（(][^】\]）)]*[】\]）)]")
_BRACKET_CHARS_RE = re.compile(r"[【】「」『』\[\]]")
_WS_RE = re.compile(r"\s+")
# 明确的季标记（全类别可抹）：第X季/第X部 / SX / Season X
_SEASON_RE = re.compile(
    r"(第\s*[一二三四五六七八九十百千零两0-9]+\s*[季部])"
    r"|(\bseason\s*\d+\b)"
    r"|(\bS\d{1,2}\b)",
    re.I,
)


def clean_title(keyword: str) -> str:
    """老库 keyword → 可展示标题：抽正题、去标签、抹明确季标记、压空白。"""
    if not keyword:
        return ""
    t = keyword.strip()
    m = re.search(r"[「『]([^」』]+)[」』]", t)  # 正题常被引号包住，后跟一串【标签】
    if m and m.group(1).strip():
        t = m.group(1).strip()
    else:
        t = _TAG_RE.sub(" ", t)
    t = _BRACKET_CHARS_RE.sub(" ", t)
    t = _SEASON_RE.sub(" ", t)  # 抹「第X季/第X部/SX/Season X」→ 并进正剧
    t = _WS_RE.sub(" ", t).strip()
    return t[:255]


def norm_key(title: str) -> str:
    """标题匹配/去重键：去所有空白+装饰，转小写。"""
    if not title:
        return ""
    k = _BRACKET_CHARS_RE.sub("", title)
    k = re.sub(r"\s+", "", k)
    return k.lower()


def map_media_type(category: str | None) -> str:
    c = (category or "").strip().lower()
    return c if c in VALID_MEDIA_TYPES else "other"


# ---------------- share_id（严格照抄 Java ShareIdExtractor / V023） ----------------
_BTIH_RE = re.compile(r"btih:([A-Za-z0-9]+)", re.I)
_SURL_RE = re.compile(r"surl=([^&#\r\n]+)")
_ASCII_OK_RE = re.compile(r"^[A-Za-z0-9._~-]{1,64}$")
_MD5_RE = re.compile(r"^[a-f0-9]{32}$")


def _md5(s: str | None) -> str:
    return hashlib.md5((s or "").encode("utf-8")).hexdigest()


def _raw_candidate(url: str, pan: str) -> str | None:
    if pan == "magnet":
        m = _BTIH_RE.search(url)
        return m.group(1).lower() if m else None
    if pan == "baidu":
        m = _SURL_RE.search(url)
        if m:
            return m.group(1)
    cut = re.split(r"[?#]", url, maxsplit=1)[0]
    while cut.endswith("/"):
        cut = cut[:-1]
    slash = cut.rfind("/")
    seg = cut[slash + 1:] if slash >= 0 else cut
    return seg or None


def compute_share_id(url: str, pan_type: str) -> str:
    if not url or not url.strip():
        return _md5(url)
    pan = (pan_type or "").strip().lower()
    first = re.split(r"[\r\n]", url.strip(), maxsplit=1)[0].strip()
    cand = _raw_candidate(first, pan)
    if (cand and pan == "baidu" and "surl=" not in first
            and len(cand) > 1 and cand.startswith("1") and not _MD5_RE.match(cand)):
        cand = cand[1:]
    if cand and _ASCII_OK_RE.match(cand):
        return cand
    return _md5(first)


@dataclass
class PlainLink:
    media_id: int
    pan_type: str
    url: str
    share_id: str
    note: str
    source: str = "migrate"


# ---------------- 新库读写 ----------------
def load_title_index(new_conn) -> dict[str, int]:
    """新库现有 media：norm_key(title) → media_id。冲突时优先 tmdb/已发布。"""
    index: dict[str, int] = {}
    best_rank: dict[str, int] = {}
    with new_conn.cursor() as cur:
        cur.execute("SELECT id, title, tmdb_id, pub_status FROM media WHERE deleted = 0")
        rows = cur.fetchall()
    for r in rows:
        key = norm_key(r["title"] or "")
        if not key:
            continue
        rank = (2 if r.get("tmdb_id") else 0) + (1 if r.get("pub_status") == 1 else 0)
        if key not in index or rank > best_rank.get(key, -1):
            index[key] = int(r["id"])
            best_rank[key] = rank
    return index


def create_plain_media(new_conn, title: str, media_type: str) -> int:
    with new_conn.cursor() as cur:
        cur.execute(
            "INSERT INTO media (type, title, meta_source, pub_status, hot) VALUES (%s, %s, 'none', 1, 0)",
            (media_type, title),
        )
        new_conn.commit()
        return int(cur.lastrowid)


def upsert_links(new_conn, rows: list[PlainLink]) -> int:
    """与 MediaLinkMapper.upsert 同语义：命中 uk_link(media_id,pan_type,share_id) 只更新不新增。"""
    if not rows:
        return 0
    sql = """
        INSERT INTO media_link
          (media_id, pan_type, url, share_id, note, source, status, invalid, last_seen_at, created_at, updated_at)
        VALUES (%s, %s, %s, %s, %s, %s, 'approved', 0, NOW(), NOW(), NOW())
        ON DUPLICATE KEY UPDATE
          note = VALUES(note),
          url = VALUES(url),
          last_seen_at = NOW(),
          updated_at = NOW()
    """
    with new_conn.cursor() as cur:
        cur.executemany(
            sql,
            [(r.media_id, r.pan_type, r.url, r.share_id, r.note, r.source) for r in rows],
        )
    new_conn.commit()
    return len(rows)


# ---------------- 老库读取 ----------------
def fetch_plain_batch(old_conn, after_id: int, limit: int, min_links: int, min_search: int) -> list[dict]:
    with old_conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, keyword, category, results_json, total_count, search_count
            FROM search_cache
            WHERE id > %s
              AND tmdb_id IS NULL
              AND results_json IS NOT NULL AND TRIM(results_json) <> ''
              AND (is_nsfw IS NULL OR is_nsfw = 0)
              AND COALESCE(total_count, 0) >= %s
              AND COALESCE(search_count, 0) >= %s
            ORDER BY id
            LIMIT %s
            """,
            (after_id, min_links, min_search, limit),
        )
        return list(cur.fetchall())


def count_plain_total(old_conn, min_links: int, min_search: int) -> int:
    with old_conn.cursor() as cur:
        cur.execute(
            """
            SELECT COUNT(*) AS c FROM search_cache
            WHERE tmdb_id IS NULL
              AND results_json IS NOT NULL AND TRIM(results_json) <> ''
              AND (is_nsfw IS NULL OR is_nsfw = 0)
              AND COALESCE(total_count, 0) >= %s
              AND COALESCE(search_count, 0) >= %s
            """,
            (min_links, min_search),
        )
        return int(cur.fetchone()["c"])


def extract_links_for_media(
    cache: dict, media_id: int, keyword: str, invalid_shares, invalid_urls,
    session_dedupe: set[tuple[int, str, str]], stats: Counter,
) -> list[PlainLink]:
    rows: list[PlainLink] = []
    for pan_key, item in extract_links_from_json(cache["results_json"]):
        if item.get("invalid") is True:
            stats["skip_json_invalid"] += 1
            continue
        raw_url = (item.get("url") or "").strip()
        if not raw_url.startswith("http") and not raw_url.startswith("magnet:"):
            stats["skip_bad_url"] += 1
            continue
        pan_type = detect_pan_type(raw_url, pan_key)
        if not pan_type or pan_type in SKIP_PAN_TYPES:
            stats["skip_non_pan"] += 1
            continue
        url = merge_password(raw_url, item.get("password"), pan_type)
        if is_invalid_link(url, pan_type, invalid_shares, invalid_urls):
            stats["skip_invalid_db"] += 1
            continue
        share_id = compute_share_id(url, pan_type)
        dkey = (media_id, pan_type, share_id)  # 与 DB 唯一键 uk_link 一致
        if dkey in session_dedupe:
            stats["skip_dup"] += 1
            continue
        session_dedupe.add(dkey)
        rows.append(PlainLink(
            media_id=media_id, pan_type=pan_type, url=url[:1024],
            share_id=share_id[:64], note=build_note(keyword, item),
        ))
        stats["links_ready"] += 1
        stats[f"pan_{pan_type}"] += 1
    return rows


def print_stats(stats: Counter, media_created: int, media_matched: int, upserted: int):
    print("\n=== 素条目迁移统计 ===")
    print(f"扫描关键字:            {stats.get('kw_rows', 0)}")
    print(f"跳过(空标题):          {stats.get('skip_empty_title', 0)}")
    print(f"命中已有 media:        {media_matched}")
    print(f"新建素条目:            {media_created}")
    print(f"跳过(JSON invalid):    {stats.get('skip_json_invalid', 0)}")
    print(f"跳过(失效库):          {stats.get('skip_invalid_db', 0)}")
    print(f"跳过(本轮重复):        {stats.get('skip_dup', 0)}")
    print(f"跳过(非网盘/坏链):     {stats.get('skip_non_pan', 0) + stats.get('skip_bad_url', 0)}")
    print(f"去重后待写链接:        {stats.get('links_ready', 0)}")
    print(f"实际 upsert 链接:      {upserted}（命中唯一键的只更新，不会重复新增）")
    pan_stats = {k[4:]: v for k, v in stats.items() if k.startswith("pan_")}
    if pan_stats:
        print("按盘型:", dict(sorted(pan_stats.items(), key=lambda x: -x[1])))


def run(old_conn, new_conn, *, apply: bool, min_links: int, min_search: int,
        cache_batch: int, insert_batch_size: int, limit_keywords: int | None) -> int:
    stats: Counter = Counter()
    title_index = load_title_index(new_conn)
    print(f"新库现有 media 标题索引: {len(title_index)}")

    invalid_shares, invalid_urls = load_invalid_sets(old_conn)
    print(f"失效库 share 键: {len(invalid_shares)}, url 键: {len(invalid_urls)}")

    session_dedupe: set[tuple[int, str, str]] = set()
    total = count_plain_total(old_conn, min_links, min_search)
    print(f"待处理无 tmdb 关键字: {total}（links≥{min_links}, search≥{min_search}）\n")

    media_created = 0
    media_matched = 0
    upserted_total = 0
    dry_created: dict[str, int] = {}
    fake_id_seq = -1

    after_id = 0
    processed = 0
    stop = False
    while not stop:
        batch = fetch_plain_batch(old_conn, after_id, cache_batch, min_links, min_search)
        if not batch:
            break
        pending: list[PlainLink] = []
        for cache in batch:
            after_id = max(after_id, int(cache["id"]))
            stats["kw_rows"] += 1
            processed += 1

            title = clean_title(cache.get("keyword") or "")
            if not title:
                stats["skip_empty_title"] += 1
                if limit_keywords and processed >= limit_keywords:
                    stop = True
                    break
                continue
            key = norm_key(title)
            mtype = map_media_type(cache.get("category"))

            media_id = title_index.get(key)
            if media_id is not None:
                media_matched += 1
            elif apply:
                media_id = create_plain_media(new_conn, title, mtype)
                title_index[key] = media_id
                media_created += 1
            else:
                media_id = dry_created.get(key)
                if media_id is None:
                    media_id = fake_id_seq
                    fake_id_seq -= 1
                    dry_created[key] = media_id
                    media_created += 1

            pending.extend(extract_links_for_media(
                cache, media_id, title, invalid_shares, invalid_urls, session_dedupe, stats))

            if limit_keywords and processed >= limit_keywords:
                stop = True
                break

        if apply and pending:
            for i in range(0, len(pending), insert_batch_size):
                upserted_total += upsert_links(new_conn, pending[i:i + insert_batch_size])

        print(f"[进度] cache_id≤{after_id} | 已扫 {processed} 词 | 命中 {media_matched} | 新建 {media_created} | 累计 upsert {upserted_total}")

    print_stats(stats, media_created, media_matched, upserted_total)
    if apply:
        print(f"\n✅ 完成：新建素条目 {media_created} 个，upsert 链接 {upserted_total} 条。")
        print("素条目 meta_source='none'，可跑「补熟」流程回填 TMDB 元数据。")
    else:
        print(f"\n[dry-run] 将新建 ~{media_created} 素条目、命中 {media_matched} 已有条目、"
              f"去重后待写 ~{stats.get('links_ready', 0)} 链接（未写库）。")
        print("确认后: python3 scripts/migrate_plain_media_from_search_cache.py --apply")
    return 0


def main():
    p = argparse.ArgumentParser(description="无 tmdb 关键字 → 素条目 + 链接")
    g = p.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true", help="只统计，不写库")
    g.add_argument("--apply", action="store_true", help="写入新库")
    p.add_argument("--min-links", type=int, default=1, help="关键字最少链接数（默认 1）")
    p.add_argument("--min-search", type=int, default=0, help="关键字最少搜索次数（默认 0=全量）")
    p.add_argument("--cache-batch", type=int, default=200, help="每批读取关键字数")
    p.add_argument("--insert-batch", type=int, default=500, help="每批 upsert 链接数")
    p.add_argument("--limit-keywords", type=int, default=None, help="最多处理 N 个关键字（小样本试跑）")
    args = p.parse_args()

    old_cfg = db_config("OLD", "jyinshi_db")
    new_cfg = db_config("NEW", "jyinshi_next")
    print(f"老库: {old_cfg['host']}:{old_cfg['port']}/{old_cfg['database']}")
    print(f"新库: {new_cfg['host']}:{new_cfg['port']}/{new_cfg['database']}\n")

    try:
        old_conn = connect(old_cfg)
    except pymysql.Error as e:
        print(f"无法连接老库: {e}", file=sys.stderr)
        sys.exit(1)
    try:
        new_conn = connect(new_cfg)
    except pymysql.Error as e:
        print(f"无法连接新库: {e}", file=sys.stderr)
        sys.exit(1)

    try:
        sys.exit(run(
            old_conn, new_conn,
            apply=args.apply,
            min_links=max(0, args.min_links),
            min_search=max(0, args.min_search),
            cache_batch=max(1, args.cache_batch),
            insert_batch_size=max(1, args.insert_batch),
            limit_keywords=args.limit_keywords,
        ))
    finally:
        old_conn.close()
        new_conn.close()


if __name__ == "__main__":
    main()
