package com.jyinshi.ops.dto;

import lombok.Data;

@Data
public class LiveQrcodeAdminVO {
    private String qrcodeImage;
    private String mpQrcodeImage;
    private String title;
    private String tipText;
    private Integer scanCount;
    private Integer status;
}
