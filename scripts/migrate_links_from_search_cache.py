#!/usr/bin/env python3
"""
一次性迁移：老库 search_cache.results_json → 新库 media_link（仅网盘，不含磁力）。

原则：
  - 只迁 tmdb_id + category(type) 与新库 media 完全匹配的行
  - 跳过 invalid=true、失效库、magnet、NSFW
  - merged_by_type 里所有网盘类型都迁
  - 支持断点续跑（按 search_cache.id 分批）

环境变量（老库 / 新库可分别配置）：
  OLD_DB_HOST OLD_DB_PORT OLD_DB_USER OLD_DB_PASSWORD OLD_DB_NAME  默认 localhost:3306 jyinshi_db
  NEW_DB_HOST NEW_DB_PORT NEW_DB_USER NEW_DB_PASSWORD NEW_DB_NAME  默认 localhost:3306 jyinshi_next

示例：
  pip install pymysql
  python3 scripts/migrate_links_from_search_cache.py --preflight   # 迁移前自检
  python3 scripts/migrate_links_from_search_cache.py --dry-run
  python3 scripts/migrate_links_from_search_cache.py --apply --reset-checkpoint
  python3 scripts/migrate_links_from_search_cache.py --apply --resume          # 中断后续跑

  # 或使用封装脚本（已配广州老库地址）：
  ./scripts/run_migrate_links.sh --only-media-id 84 --dry-run
  ./scripts/run_migrate_links.sh --only-media-id 84 --apply
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import signal
import sys
import time
from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

try:
    import pymysql
except ImportError:
    print("请先安装: pip install pymysql", file=sys.stderr)
    sys.exit(1)

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_CHECKPOINT = SCRIPT_DIR / ".migrate_links_checkpoint.json"

SKIP_PAN_TYPES = frozenset({"magnet", "unknown", "others", "other"})
VALID_MEDIA_TYPES = frozenset({"movie", "tv", "anime", "variety"})

QUARK_RE = re.compile(r"pan\.quark\.cn/s/([a-zA-Z0-9]+)", re.I)
BAIDU_RE = re.compile(r"pan\.baidu\.com/s/([a-zA-Z0-9_-]+)", re.I)
XUNLEI_RE = re.compile(r"pan\.xunlei\.com/s/([a-zA-Z0-9_-]+)", re.I)

HOST_PAN_MAP = (
    ("pan.quark.cn", "quark"),
    ("pan.baidu.com", "baidu"),
    ("pan.xunlei.com", "xunlei"),
    ("drive.uc.cn", "uc"),
    ("alipan.com", "aliyun"),
    ("aliyundrive.com", "aliyun"),
    ("115.com", "115"),
    ("115cdn.com", "115"),
    ("anxia.com", "115"),
    ("123pan.com", "123"),
    ("123pan.cn", "123"),
    ("123684.com", "123"),
    ("123685.com", "123"),
    ("123912.com", "123"),
    ("123592.com", "123"),
    ("cloud.189.cn", "tianyi"),
    ("caiyun.139.com", "mobile"),
)

_stop_requested = False


def _on_sigint(_signum, _frame):
    global _stop_requested
    _stop_requested = True
    print("\n收到中断，当前批次完成后保存断点并退出…", file=sys.stderr)


@dataclass
class LinkRow:
    media_id: int
    pan_type: str
    url: str
    note: str
    source: str = "pansou"


@dataclass
class Checkpoint:
    last_cache_id: int = 0
    completed: bool = False
    mode: str = ""
    stats: dict[str, int] = field(default_factory=dict)
    inserted_total: int = 0
    skipped_existing_total: int = 0
    updated_at: str = ""

    def to_dict(self) -> dict[str, Any]:
        return {
            "last_cache_id": self.last_cache_id,
            "completed": self.completed,
            "mode": self.mode,
            "stats": self.stats,
            "inserted_total": self.inserted_total,
            "skipped_existing_total": self.skipped_existing_total,
            "updated_at": self.updated_at,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> Checkpoint:
        return cls(
            last_cache_id=int(data.get("last_cache_id") or 0),
            completed=bool(data.get("completed")),
            mode=str(data.get("mode") or ""),
            stats={k: int(v) for k, v in (data.get("stats") or {}).items()},
            inserted_total=int(data.get("inserted_total") or 0),
            skipped_existing_total=int(data.get("skipped_existing_total") or 0),
            updated_at=str(data.get("updated_at") or ""),
        )


def load_checkpoint(path: Path) -> Checkpoint | None:
    if not path.is_file():
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return Checkpoint.from_dict(data)
    except (json.JSONDecodeError, OSError, ValueError):
        return None


def save_checkpoint(path: Path, cp: Checkpoint) -> None:
    cp.updated_at = datetime.now(timezone.utc).isoformat()
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(cp.to_dict(), ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(path)


def db_config(prefix: str, default_db: str) -> dict[str, Any]:
    return {
        "host": os.environ.get(f"{prefix}_DB_HOST", "127.0.0.1"),
        "port": int(os.environ.get(f"{prefix}_DB_PORT", "3306")),
        "user": os.environ.get(f"{prefix}_DB_USER", "jyinshi"),
        "password": os.environ.get(f"{prefix}_DB_PASSWORD", "jyinshi123"),
        "database": os.environ.get(f"{prefix}_DB_NAME", default_db),
        "charset": "utf8mb4",
        "cursorclass": pymysql.cursors.DictCursor,
    }


def connect(cfg: dict[str, Any]):
    return pymysql.connect(**cfg)


def extract_share_id(url: str, pan_type: str) -> str | None:
    pt = pan_type.lower()
    if pt == "quark":
        m = QUARK_RE.search(url)
    elif pt == "baidu":
        m = BAIDU_RE.search(url)
    elif pt == "xunlei":
        m = XUNLEI_RE.search(url)
    else:
        return None
    return m.group(1) if m else None


def detect_pan_type(url: str, json_key: str | None) -> str | None:
    lower = url.lower()
    if lower.startswith("magnet:"):
        return None
    for host, pan in HOST_PAN_MAP:
        if host in lower:
            return pan
    if json_key and json_key.lower() not in SKIP_PAN_TYPES:
        return json_key.lower()
    return None


def normalize_url(url: str) -> str:
    return url.strip()


def url_dedupe_key(url: str) -> str:
    return hashlib.sha256(normalize_url(url).encode()).hexdigest()


def merge_password(url: str, password: str | None, pan_type: str | None = None) -> str:
    url = normalize_url(url)
    pt = (pan_type or "").lower()

    # 百度老数据常见：url|提取码 → ?pwd=提取码
    if pt == "baidu":
        m = re.match(r"^(https?://pan\.baidu\.com/s/[^\s|?#]+)\|([^\s|?#]+)$", url, re.I)
        if m:
            url, password = m.group(1), m.group(2)

    pwd = (password or "").strip()
    if not pwd:
        return url[:1024]
    if "pwd=" in url.lower() or "password=" in url.lower():
        return url[:1024]
    if "提取码" in url:
        return url[:1024]
    if pt in ("baidu", "xunlei", "quark", "123"):
        sep = "&" if "?" in url else "?"
        return f"{url}{sep}pwd={pwd}"[:1024]
    return f"{url}\n提取码：{pwd}"[:1024]


def load_media_map(conn) -> dict[tuple[int, str], int]:
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, tmdb_id, type
            FROM media
            WHERE deleted = 0 AND pub_status = 1 AND tmdb_id IS NOT NULL
            """
        )
        rows = cur.fetchall()
    return {(int(r["tmdb_id"]), r["type"]): int(r["id"]) for r in rows}


def load_invalid_sets(conn) -> tuple[set[tuple[str, str]], set[str]]:
    share_keys: set[tuple[str, str]] = set()
    url_keys: set[str] = set()
    with conn.cursor() as cur:
        cur.execute("SELECT pan_type, share_id, share_url FROM invalid_share_link")
        for r in cur.fetchall():
            pt = (r.get("pan_type") or "").lower()
            sid = (r.get("share_id") or "").strip()
            if pt and sid:
                share_keys.add((pt, sid))
            su = (r.get("share_url") or "").strip()
            if su:
                url_keys.add(url_dedupe_key(su))
    return share_keys, url_keys


def is_invalid_link(url: str, pan_type: str, invalid_shares: set[tuple[str, str]], invalid_urls: set[str]) -> bool:
    if url_dedupe_key(url) in invalid_urls:
        return True
    sid = extract_share_id(url, pan_type)
    if sid and (pan_type.lower(), sid) in invalid_shares:
        return True
    return False


def parse_category(category: str | None) -> str | None:
    if not category:
        return None
    c = category.strip().lower()
    return c if c in VALID_MEDIA_TYPES else None


def fetch_cache_for_tmdb(old_conn, tmdb_id: int, media_type: str) -> list[dict]:
    """单剧测试：拉取该 tmdb+type 的全部 search_cache 行。"""
    with old_conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, keyword, tmdb_id, category, is_nsfw, results_json
            FROM search_cache
            WHERE tmdb_id = %s
              AND category = %s
              AND results_json IS NOT NULL
              AND TRIM(results_json) <> ''
              AND (is_nsfw IS NULL OR is_nsfw = 0)
            ORDER BY id
            """,
            (tmdb_id, media_type),
        )
        return list(cur.fetchall())


def resolve_media_target(new_conn, media_id: int) -> tuple[int, int, str, str]:
    with new_conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, tmdb_id, type, title
            FROM media
            WHERE id = %s AND deleted = 0 AND pub_status = 1 AND tmdb_id IS NOT NULL
            """,
            (media_id,),
        )
        row = cur.fetchone()
    if not row:
        raise SystemExit(f"media_id={media_id} 不存在或未发布或无 tmdb_id")
    return int(row["id"]), int(row["tmdb_id"]), row["type"], row["title"] or ""


def fetch_cache_batch(old_conn, after_id: int, limit: int) -> list[dict]:
    with old_conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, keyword, tmdb_id, category, is_nsfw, results_json
            FROM search_cache
            WHERE id > %s
              AND tmdb_id IS NOT NULL
              AND results_json IS NOT NULL
              AND TRIM(results_json) <> ''
              AND (is_nsfw IS NULL OR is_nsfw = 0)
            ORDER BY id
            LIMIT %s
            """,
            (after_id, limit),
        )
        return list(cur.fetchall())


def count_cache_total(old_conn, after_id: int = 0) -> int:
    with old_conn.cursor() as cur:
        cur.execute(
            """
            SELECT COUNT(*) AS c FROM search_cache
            WHERE id > %s
              AND tmdb_id IS NOT NULL
              AND results_json IS NOT NULL
              AND TRIM(results_json) <> ''
              AND (is_nsfw IS NULL OR is_nsfw = 0)
            """,
            (after_id,),
        )
        return int(cur.fetchone()["c"])


def extract_links_from_json(results_json: str) -> list[tuple[str, dict]]:
    try:
        data = json.loads(results_json)
    except json.JSONDecodeError:
        return []
    merged = data.get("merged_by_type") or {}
    if not isinstance(merged, dict):
        return []
    out: list[tuple[str, dict]] = []
    for pan_key, items in merged.items():
        if not isinstance(items, list):
            continue
        pk = (pan_key or "").lower()
        if pk in SKIP_PAN_TYPES:
            continue
        for item in items:
            if isinstance(item, dict):
                out.append((pk, item))
    return out


def build_note(keyword: str, item: dict) -> str:
    note = (item.get("note") or item.get("workTitle") or "").strip()
    if note:
        return note[:255]
    return (keyword or "")[:255]


def process_cache_row(
    cache: dict,
    media_map: dict[tuple[int, str], int],
    invalid_shares,
    invalid_urls,
    session_dedupe: set[tuple[int, str, str]],
    stats: Counter,
) -> list[LinkRow]:
    rows: list[LinkRow] = []
    stats["cache_rows"] += 1
    tmdb_id = int(cache["tmdb_id"])
    media_type = parse_category(cache.get("category"))
    if media_type is None:
        stats["skip_bad_category"] += 1
        return rows
    media_id = media_map.get((tmdb_id, media_type))
    if media_id is None:
        stats["skip_no_media"] += 1
        return rows
    stats["cache_matched"] += 1

    keyword = cache.get("keyword") or ""
    for pan_key, item in extract_links_from_json(cache["results_json"]):
        if item.get("invalid") is True:
            stats["skip_json_invalid"] += 1
            continue
        raw_url = (item.get("url") or "").strip()
        if not raw_url.startswith("http"):
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
        dkey = (media_id, pan_type, url_dedupe_key(url))
        if dkey in session_dedupe:
            stats["skip_dup"] += 1
            continue
        session_dedupe.add(dkey)
        rows.append(
            LinkRow(
                media_id=media_id,
                pan_type=pan_type,
                url=url[:1024],
                note=build_note(keyword, item),
            )
        )
        stats["links_ready"] += 1
        stats[f"pan_{pan_type}"] += 1
    return rows


def load_existing_keys(new_conn) -> set[tuple[int, str, str]]:
    keys: set[tuple[int, str, str]] = set()
    with new_conn.cursor() as cur:
        cur.execute("SELECT media_id, pan_type, url FROM media_link")
        for r in cur.fetchall():
            keys.add((int(r["media_id"]), r["pan_type"], url_dedupe_key(r["url"])))
    return keys


def insert_batch(new_conn, rows: list[LinkRow]) -> int:
    if not rows:
        return 0
    sql = """
        INSERT INTO media_link
          (media_id, pan_type, url, note, source, status, invalid, report_count)
        VALUES (%s, %s, %s, %s, %s, 'approved', 0, 0)
    """
    with new_conn.cursor() as cur:
        cur.executemany(
            sql,
            [(r.media_id, r.pan_type, r.url, r.note, r.source) for r in rows],
        )
    new_conn.commit()
    return len(rows)


def counter_to_dict(c: Counter) -> dict[str, int]:
    return {k: int(v) for k, v in c.items()}


def dict_to_counter(d: dict[str, int]) -> Counter:
    return Counter({k: int(v) for k, v in d.items()})


def run_preflight(old_conn, new_conn, checkpoint_path: Path) -> int:
    print("=== 迁移前自检 ===\n")
    ok = True

    with new_conn.cursor() as cur:
        cur.execute(
            "SELECT COUNT(*) AS c FROM media WHERE deleted=0 AND pub_status=1 AND tmdb_id IS NOT NULL"
        )
        media_n = int(cur.fetchone()["c"])
        cur.execute("SELECT COUNT(*) AS c FROM media_link")
        link_n = int(cur.fetchone()["c"])

    with old_conn.cursor() as cur:
        cur.execute(
            """
            SELECT COUNT(*) AS c FROM search_cache
            WHERE tmdb_id IS NOT NULL
              AND results_json IS NOT NULL
              AND TRIM(results_json) <> ''
              AND (is_nsfw IS NULL OR is_nsfw = 0)
            """
        )
        cache_n = int(cur.fetchone()["c"])
        cur.execute("SELECT COUNT(*) AS c FROM invalid_share_link")
        invalid_n = int(cur.fetchone()["c"])

    print(f"新库 media（已发布+有 tmdb）: {media_n}")
    print(f"新库 media_link 现有:         {link_n}")
    print(f"老库可迁 search_cache:        {cache_n}")
    print(f"老库 invalid_share_link:      {invalid_n}")

    if media_n < 1000:
        print("⚠ media 数量偏少，请确认新库数据正常")
        ok = False
    if cache_n < 1000:
        print("⚠ search_cache 可迁行数偏少")
        ok = False

    cp = load_checkpoint(checkpoint_path)
    if cp:
        print(f"\n已有断点: mode={cp.mode}, last_cache_id={cp.last_cache_id}, completed={cp.completed}")
        if cp.mode == "dry-run":
            print("→ 正式 --apply 请带 --reset-checkpoint，避免 dry-run 断点干扰")

    # 试拉一批，确认 JSON 可解析
    batch = fetch_cache_batch(old_conn, 0, 3)
    parsed = 0
    for row in batch:
        if extract_links_from_json(row["results_json"]):
            parsed += 1
    print(f"\n试读 search_cache 前 3 条，含链接 JSON: {parsed}/3")
    if parsed == 0 and batch:
        print("⚠ 样本 JSON 未解析出链接，请检查数据结构")
        ok = False

    if ok:
        print("\n✅ 自检通过，可以 --apply --reset-checkpoint")
        return 0
    print("\n❌ 自检有问题，请先排查再迁移")
    return 1


def print_stats(stats: Counter, inserted: int, skipped_existing: int, last_id: int, completed: bool):
    print("\n=== 迁移统计 ===")
    print(f"断点 last_cache_id:        {last_id}")
    print(f"已完成:                    {'是' if completed else '否（可 --resume 续跑）'}")
    print(f"search_cache 扫描行数:     {stats.get('cache_rows', 0)}")
    print(f"tmdb+type 匹配 media:      {stats.get('cache_matched', 0)}")
    print(f"跳过(无对应 media):        {stats.get('skip_no_media', 0)}")
    print(f"跳过(category 无效):       {stats.get('skip_bad_category', 0)}")
    print(f"跳过(JSON invalid=true):   {stats.get('skip_json_invalid', 0)}")
    print(f"跳过(失效库):              {stats.get('skip_invalid_db', 0)}")
    print(f"跳过(重复):                {stats.get('skip_dup', 0)}")
    print(f"跳过(非网盘/坏链):         {stats.get('skip_non_pan', 0) + stats.get('skip_bad_url', 0)}")
    print(f"累计写入链接:              {inserted}")
    print(f"累计已存在跳过:            {skipped_existing}")
    pan_stats = {k[4:]: v for k, v in stats.items() if k.startswith("pan_")}
    if pan_stats:
        print("按盘型:", dict(sorted(pan_stats.items(), key=lambda x: -x[1])))


def run_single_media(
    old_conn,
    new_conn,
    *,
    media_id: int,
    apply: bool,
    insert_batch_size: int,
) -> int:
    mid, tmdb_id, media_type, title = resolve_media_target(new_conn, media_id)
    print(f"=== 单剧测试: [{mid}] {title} (tmdb={tmdb_id}, type={media_type}) ===\n")

    media_map = load_media_map(new_conn)
    if media_map.get((tmdb_id, media_type)) != mid:
        print("tmdb+type 与新库 media 不一致", file=sys.stderr)
        return 1

    invalid_shares, invalid_urls = load_invalid_sets(old_conn)
    batch = fetch_cache_for_tmdb(old_conn, tmdb_id, media_type)
    if not batch:
        print("老库 search_cache 无匹配行")
        return 1
    print(f"老库 search_cache 命中 {len(batch)} 行")

    stats: Counter = Counter()
    session_dedupe: set[tuple[int, str, str]] = set()
    links: list[LinkRow] = []
    for cache in batch:
        links.extend(
            process_cache_row(cache, media_map, invalid_shares, invalid_urls, session_dedupe, stats)
        )
    links = [r for r in links if r.media_id == mid]

    existing_keys = load_existing_keys(new_conn) if apply else set()
    to_insert: list[LinkRow] = []
    skipped = 0
    for r in links:
        k = (r.media_id, r.pan_type, url_dedupe_key(r.url))
        if k in existing_keys:
            skipped += 1
            continue
        to_insert.append(r)

    print_stats(stats, 0, skipped, batch[-1]["id"], True)

    if not apply:
        print(f"\n[dry-run] 该剧可写入 {len(to_insert)} 条（已存在跳过 {skipped}）")
        print(f"确认: --only-media-id {mid} --apply")
        return 0

    inserted = 0
    for i in range(0, len(to_insert), insert_batch_size):
        inserted += insert_batch(new_conn, to_insert[i : i + insert_batch_size])
    print(f"\n已写入 {inserted} 条 → media_id={mid}")
    print(f"验证: GET /api/media/{mid}/links")
    return 0


def run_migration(
    old_conn,
    new_conn,
    *,
    apply: bool,
    checkpoint_path: Path,
    resume: bool,
    cache_batch: int,
    insert_batch_size: int,
) -> int:
    global _stop_requested
    mode = "apply" if apply else "dry-run"

    if resume:
        cp = load_checkpoint(checkpoint_path)
        if cp and cp.completed and cp.mode == mode:
            print(f"上次已完成 ({checkpoint_path})，无需续跑。若要重来加 --reset-checkpoint")
            print_stats(dict_to_counter(cp.stats), cp.inserted_total, cp.skipped_existing_total, cp.last_cache_id, True)
            return 0
        if cp and cp.mode and cp.mode != mode:
            print(f"断点模式为 {cp.mode}，当前 {mode} 不一致。请 --reset-checkpoint 或换 --dry-run/--apply", file=sys.stderr)
            return 1
    else:
        cp = None

    start_id = cp.last_cache_id if cp else 0
    stats = dict_to_counter(cp.stats) if cp else Counter()
    inserted_total = cp.inserted_total if cp else 0
    skipped_existing_total = cp.skipped_existing_total if cp else 0

    media_map = load_media_map(new_conn)
    print(f"新库可匹配 media 条目: {len(media_map)}")

    invalid_shares, invalid_urls = load_invalid_sets(old_conn)
    print(f"失效库 share 键: {len(invalid_shares)}, url 键: {len(invalid_urls)}")

    existing_keys = load_existing_keys(new_conn) if apply else set()
    session_dedupe: set[tuple[int, str, str]] = set()

    remaining = count_cache_total(old_conn, start_id)
    print(f"待处理 search_cache 行数: {remaining}（从 id > {start_id}）")
    if resume and cp:
        print(f"续跑：已累计写入 {inserted_total} 条")

    signal.signal(signal.SIGINT, _on_sigint)

    processed_batches = 0
    while not _stop_requested:
        batch = fetch_cache_batch(old_conn, start_id, cache_batch)
        if not batch:
            break

        batch_links: list[LinkRow] = []
        batch_max_id = start_id
        for cache in batch:
            batch_max_id = max(batch_max_id, int(cache["id"]))
            batch_links.extend(
                process_cache_row(
                    cache, media_map, invalid_shares, invalid_urls, session_dedupe, stats
                )
            )

        batch_inserted = 0
        batch_skipped = 0
        if apply and batch_links:
            to_insert: list[LinkRow] = []
            for r in batch_links:
                k = (r.media_id, r.pan_type, url_dedupe_key(r.url))
                if k in existing_keys:
                    batch_skipped += 1
                    continue
                to_insert.append(r)
                existing_keys.add(k)

            for i in range(0, len(to_insert), insert_batch_size):
                chunk = to_insert[i : i + insert_batch_size]
                batch_inserted += insert_batch(new_conn, chunk)
                if _stop_requested:
                    break

        inserted_total += batch_inserted
        skipped_existing_total += batch_skipped
        start_id = batch_max_id
        processed_batches += 1

        cp_state = Checkpoint(
            last_cache_id=start_id,
            completed=False,
            mode=mode,
            stats=counter_to_dict(stats),
            inserted_total=inserted_total,
            skipped_existing_total=skipped_existing_total,
        )
        save_checkpoint(checkpoint_path, cp_state)

        done = count_cache_total(old_conn, start_id)
        print(
            f"[batch {processed_batches}] cache_id≤{start_id} | "
            f"本批链接 {len(batch_links)} | "
            f"写入 {batch_inserted} | 已存在跳过 {batch_skipped} | 剩余 cache 行 {done}"
        )

        if _stop_requested:
            print(f"已保存断点: {checkpoint_path}（last_cache_id={start_id}）")
            print_stats(stats, inserted_total, skipped_existing_total, start_id, False)
            return 130

    completed = not _stop_requested
    cp_final = Checkpoint(
        last_cache_id=start_id,
        completed=completed,
        mode=mode,
        stats=counter_to_dict(stats),
        inserted_total=inserted_total,
        skipped_existing_total=skipped_existing_total,
    )
    save_checkpoint(checkpoint_path, cp_final)

    print_stats(stats, inserted_total, skipped_existing_total, start_id, completed)
    if apply:
        if completed:
            print(f"\n完成，共写入 {inserted_total} 条 media_link。")
        else:
            print(f"\n中断，已写入 {inserted_total} 条。续跑: --apply --resume")
    else:
        print(f"\n[dry-run] 解析出有效链接 {stats.get('links_ready', 0)} 条（未写库）。")
        if completed:
            print("确认后: python3 scripts/migrate_links_from_search_cache.py --apply")
    return 0


def main():
    parser = argparse.ArgumentParser(description="search_cache 网盘链 → media_link（支持断点）")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--preflight", action="store_true", help="迁移前自检（只读，不写库）")
    group.add_argument("--dry-run", action="store_true", help="只扫描统计，不写库")
    group.add_argument("--apply", action="store_true", help="写入新库")
    parser.add_argument("--resume", action="store_true", help="从断点文件续跑（默认从头，除非指定此项）")
    parser.add_argument(
        "--reset-checkpoint",
        action="store_true",
        help="删除断点文件后从头开始",
    )
    parser.add_argument(
        "--checkpoint-file",
        type=Path,
        default=DEFAULT_CHECKPOINT,
        help=f"断点 JSON 路径（默认 {DEFAULT_CHECKPOINT.name}）",
    )
    parser.add_argument("--cache-batch", type=int, default=200, help="每批 search_cache 行数")
    parser.add_argument("--insert-batch", type=int, default=500, help="每批 INSERT 条数")
    parser.add_argument(
        "--only-media-id",
        type=int,
        metavar="ID",
        help="只迁移/测试新库某一个 media_id（不写全库断点）",
    )
    args = parser.parse_args()

    if args.only_media_id and args.preflight:
        print("--only-media-id 不能与 --preflight 同用", file=sys.stderr)
        sys.exit(1)

    cp_path: Path = args.checkpoint_file
    if args.reset_checkpoint and cp_path.is_file():
        cp_path.unlink()
        print(f"已删除断点: {cp_path}")

    old_cfg = db_config("OLD", "jyinshi_db")
    new_cfg = db_config("NEW", "jyinshi_next")

    print(f"老库: {old_cfg['host']}:{old_cfg['port']}/{old_cfg['database']}")
    print(f"新库: {new_cfg['host']}:{new_cfg['port']}/{new_cfg['database']}")
    print(f"断点文件: {cp_path}")

    try:
        old_conn = connect(old_cfg)
    except pymysql.Error as e:
        print(f"无法连接老库: {e}", file=sys.stderr)
        print("请设置 OLD_DB_HOST / OLD_DB_PORT 等环境变量指向 jyinshi_db", file=sys.stderr)
        sys.exit(1)

    try:
        new_conn = connect(new_cfg)
    except pymysql.Error as e:
        print(f"无法连接新库: {e}", file=sys.stderr)
        sys.exit(1)

    if args.apply and not args.reset_checkpoint and not args.only_media_id:
        cp_check = load_checkpoint(cp_path)
        if cp_check and cp_check.mode == "dry-run":
            print("检测到 dry-run 断点，正式全量迁移请加 --reset-checkpoint", file=sys.stderr)
            sys.exit(1)

    cp_existing = load_checkpoint(cp_path) if cp_path.is_file() else None
    auto_resume = (
        args.apply
        and not args.reset_checkpoint
        and cp_existing is not None
        and not cp_existing.completed
        and cp_existing.mode == "apply"
    )
    should_resume = args.resume or auto_resume

    try:
        if args.preflight:
            sys.exit(run_preflight(old_conn, new_conn, cp_path))

        if args.only_media_id:
            if args.resume:
                print("单剧模式不支持 --resume", file=sys.stderr)
                sys.exit(1)
            sys.exit(
                run_single_media(
                    old_conn,
                    new_conn,
                    media_id=args.only_media_id,
                    apply=args.apply,
                    insert_batch_size=max(1, args.insert_batch),
                )
            )

        code = run_migration(
            old_conn,
            new_conn,
            apply=args.apply,
            checkpoint_path=cp_path,
            resume=should_resume,
            cache_batch=max(1, args.cache_batch),
            insert_batch_size=max(1, args.insert_batch),
        )
        sys.exit(code)
    finally:
        old_conn.close()
        new_conn.close()


if __name__ == "__main__":
    main()
