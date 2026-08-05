#!/usr/bin/env python3
"""一次性修复 media_link 中百度 url|提取码 格式 → ?pwd=提取码"""
from __future__ import annotations

import os
import re
import sys

try:
    import pymysql
except ImportError:
    print("pip install pymysql", file=sys.stderr)
    sys.exit(1)

BAIDU_PIPE = re.compile(
    r"^(https?://pan\.baidu\.com/s/[^\s|?#]+)\|([^\s|?#]+)$", re.I
)
EXTRACT_LINE = re.compile(r"^提取码[:：]\s*(\S+)", re.I)


def normalize_url(url: str, pan_type: str) -> str | None:
    url = (url or "").strip()
    if not url or pan_type.lower() != "baidu":
        return None
    lines = url.split("\n")
    share = lines[0].strip()
    m = BAIDU_PIPE.match(share)
    if m:
        share, pwd = m.group(1), m.group(2)
    elif "pwd=" in share.lower():
        return None
    else:
        pwd = None
        for line in lines[1:]:
            em = EXTRACT_LINE.match(line.strip())
            if em:
                pwd = em.group(1)
                break
        if not pwd:
            return None
    sep = "&" if "?" in share else "?"
    if "pwd=" in share.lower():
        return share[:1024]
    return f"{share}{sep}pwd={pwd}"[:1024]


def main() -> None:
    dry = "--dry-run" in sys.argv
    conn = pymysql.connect(
        host=os.environ.get("NEW_DB_HOST", "127.0.0.1"),
        port=int(os.environ.get("NEW_DB_PORT", "3306")),
        user=os.environ.get("NEW_DB_USER", "jyinshi"),
        password=os.environ.get("NEW_DB_PASSWORD", "jyinshi123"),
        database=os.environ.get("NEW_DB_NAME", "jyinshi_next"),
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )
    updated = 0
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, url FROM media_link WHERE pan_type='baidu' "
            "AND (url LIKE '%|%' OR url LIKE '%提取码%')"
        )
        rows = cur.fetchall()
        for row in rows:
            new_url = normalize_url(row["url"], "baidu")
            if not new_url or new_url == row["url"]:
                continue
            updated += 1
            if not dry:
                cur.execute(
                    "UPDATE media_link SET url=%s WHERE id=%s",
                    (new_url, row["id"]),
                )
    if not dry:
        conn.commit()
    conn.close()
    mode = "dry-run" if dry else "apply"
    print(f"[{mode}] baidu url normalized: {updated}")


if __name__ == "__main__":
    main()
