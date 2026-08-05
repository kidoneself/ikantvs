package com.jyinshi.transfer.pan.driver;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 打开一个分享后的上下文（增量同步全程复用，避免重复取 token）。
 *
 * <p>各家差异放在这里：夸克用 {@link #shareId}+{@link #token}(stoken)；
 * 百度用 shareId+uk+sekey（uk 放 {@link #extra}）；迅雷用 shareId+token。</p>
 */
@Data
public class ShareContext {

    private boolean ok;
    private String message;

    private String shareId;
    /** 夸克 stoken / 百度 sekey / 迅雷 pass_code_token 等。 */
    private String token;
    /** 分享根目录 id（夸克/迅雷为 "0"，百度为根路径）。 */
    private String rootDirId = "0";
    /** 其它家特有字段（如百度 uk、账号标题）。 */
    private Map<String, Object> extra = new HashMap<>();

    public static ShareContext fail(String message) {
        ShareContext c = new ShareContext();
        c.ok = false;
        c.message = message;
        return c;
    }
}
