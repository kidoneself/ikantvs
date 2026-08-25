package com.jyinshi.content.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 入池提交结果。 */
@Data
public class PoolIngestResultVO {

    private int added;
    private int updated;
    private int skipped;
    private int failed;
    private List<PoolIngestRowVO> rows = new ArrayList<>();

    public void addRow(PoolIngestRowVO row) {
        rows.add(row);
        String st = row.getStatus() == null ? "" : row.getStatus();
        switch (st) {
            case "added", "transferring", "done" -> added++;
            case "updated" -> updated++;
            case "failed" -> failed++;
            default -> skipped++;
        }
    }
}
