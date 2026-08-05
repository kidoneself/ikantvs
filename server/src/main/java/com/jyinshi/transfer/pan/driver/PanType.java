package com.jyinshi.transfer.pan.driver;

/** 支持的网盘类型。 */
public enum PanType {
    QUARK,
    BAIDU,
    XUNLEI;

    public static PanType of(String s) {
        if (s == null) return null;
        try {
            return PanType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
