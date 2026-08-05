package com.jyinshi.transfer.pan.exec;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 增量同步结果：这次追更新增了哪些文件。分享链不变（复用固定夹），故不含新链接。 */
@Data
public class SyncResult {

    private boolean success;
    private int newFileCount;
    /** 本次新增的文件名（供主站记录/展示）。 */
    private List<String> newFiles = new ArrayList<>();
    /** 新增里"最新"的一个（追更进度展示，如最新集数文件名）。 */
    private String latestFileName;
    private String message;

    public static SyncResult ok(List<String> newFiles, String latestFileName) {
        SyncResult r = new SyncResult();
        r.success = true;
        r.newFiles = newFiles;
        r.newFileCount = newFiles.size();
        r.latestFileName = latestFileName;
        r.message = newFiles.isEmpty() ? "无新增" : ("新增 " + newFiles.size() + " 个文件");
        return r;
    }

    public static SyncResult fail(String message) {
        SyncResult r = new SyncResult();
        r.success = false;
        r.message = message;
        return r;
    }
}
