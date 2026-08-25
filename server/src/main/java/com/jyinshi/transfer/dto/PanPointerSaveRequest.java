package com.jyinshi.transfer.dto;

import lombok.Data;

/** 保存某盘追更号 / 片库号。空字符串表示清除。 */
@Data
public class PanPointerSaveRequest {

    private String panType;
    private String followAccountName;
    private String libraryAccountName;
}
