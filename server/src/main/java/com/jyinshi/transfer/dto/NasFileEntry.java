package com.jyinshi.transfer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 灌盘差集条目（写入 nas_job.files_json，千云按 fs_id 下载）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NasFileEntry {
    private String fsId;
    private String name;
    private long size;
    /** 相对百度固定夹根的目录，空=根下散文件。 */
    private String relDir;
}
