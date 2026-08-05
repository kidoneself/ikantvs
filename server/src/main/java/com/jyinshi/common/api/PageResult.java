package com.jyinshi.common.api;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页返回结构。
 *
 * @param <T> 列表元素类型
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private long total;
    private long page;
    private long size;
    private List<T> records;

    public static <T> PageResult<T> of(long total, long page, long size, List<T> records) {
        PageResult<T> p = new PageResult<>();
        p.total = total;
        p.page = page;
        p.size = size;
        p.records = records;
        return p;
    }
}
