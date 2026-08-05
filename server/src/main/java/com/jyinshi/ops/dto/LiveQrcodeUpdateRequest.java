package com.jyinshi.ops.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LiveQrcodeUpdateRequest {

    @Size(max = 500)
    private String qrcodeImage;

    @Size(max = 500)
    private String mpQrcodeImage;

    @Size(max = 100)
    private String title;

    @Size(max = 200)
    private String tipText;

    /** 0 / 1 */
    private Integer status;
}
