package com.jyinshi.content.service;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/** 前台 media 分页查询缓存键（含搜索词与筛选项）。 */
record MediaSearchCacheKey(
        long page,
        long size,
        String type,
        String keyword,
        String sort,
        Integer yearFrom,
        Integer yearTo,
        String genre,
        String country,
        BigDecimal minRating
) {

    static MediaSearchCacheKey of(long page, long size, String type, String keyword, String sort,
                                  Integer yearFrom, Integer yearTo, String genre,
                                  String country, BigDecimal minRating) {
        return new MediaSearchCacheKey(
                page,
                size,
                norm(type),
                norm(keyword),
                norm(sort),
                yearFrom,
                yearTo,
                norm(genre),
                norm(country),
                minRating
        );
    }

    String digest() {
        String raw = page + "|" + size + "|" + type + "|" + keyword + "|" + sort + "|"
                + yearFrom + "|" + yearTo + "|" + genre + "|" + country + "|" + minRating;
        return DigestUtil.md5Hex(raw);
    }

    private static String norm(String s) {
        return StringUtils.hasText(s) ? s.trim() : "";
    }
}
