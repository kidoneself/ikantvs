package com.jyinshi.transfer.pan.driver;

import lombok.Data;

/**
 * 统一文件/目录模型（增量同步引擎用）。分享侧与目标夹侧共用一套。
 *
 * <p>{@link #id} 语义随来源不同：分享侧是「分享内 id」（夸克 fid / 百度 fsId / 迅雷 fileId），
 * 目标夹侧是「本账号内 id」（夸克 fid / 百度路径 / 迅雷 fileId）。
 * {@link #token} 放各家转存所需的每文件令牌（夸克 share_fid_token），没有则为 null。</p>
 */
@Data
public class PanFile {

    private String id;
    private String name;
    private boolean dir;
    private long size;
    /** 转存所需的每文件令牌（夸克 fidToken），可空。 */
    private String token;
    /**
     * 递归进本目录子项时传给 listShareDir 的寻址 id。默认与 {@link #id} 相同（夸克 fid）；
     * 当"转存用 id"与"列子目录用 id"不一致时用它（百度：id=fs_id 用于转存，subId=分享内路径 用于列子目录）。
     */
    private String subId;

    public static PanFile of(String id, String name, boolean dir, long size, String token) {
        PanFile f = new PanFile();
        f.id = id;
        f.name = name;
        f.dir = dir;
        f.size = size;
        f.token = token;
        return f;
    }

    /** 递归子目录时用的寻址 id：subId 优先，回退到 id。 */
    public String childListingId() {
        return subId != null ? subId : id;
    }
}
