package com.jyinshi.transfer.pan.driver;

import lombok.Data;

/**
 * 账号信息快照（昵称 / 用户 id / 空间），供后台展示。
 *
 * <p>空间单位为字节；未知用 -1。剩余空间 = total - used（由展示端计算，避免各家口径不一）。</p>
 */
@Data
public class AccountInfo {

    private String nickname;
    private String uid;
    /** 总空间（字节）；-1 未知。 */
    private long totalSpace = -1;
    /** 已用空间（字节）；-1 未知。 */
    private long usedSpace = -1;

    public static AccountInfo of(String nickname, String uid, long total, long used) {
        AccountInfo i = new AccountInfo();
        i.nickname = nickname;
        i.uid = uid;
        i.totalSpace = total;
        i.usedSpace = used;
        return i;
    }
}
