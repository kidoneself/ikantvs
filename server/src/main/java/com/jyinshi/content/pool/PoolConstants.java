package com.jyinshi.content.pool;

/** 未绑剧搜索池常量。 */
public final class PoolConstants {

    private PoolConstants() {
    }

    /** 未绑剧哨兵：不对应影视库条目。 */
    public static final long UNBOUND_MEDIA_ID = 0L;

    /** 同行录入。 */
    public static final String SOURCE_POOL = "pool";

    /** 自营录入 / 站长精选。 */
    public static final String SOURCE_SELF = "self";

    public static final int MAX_CHARS = 80_000;
    public static final int MAX_ITEMS = 200;
}
