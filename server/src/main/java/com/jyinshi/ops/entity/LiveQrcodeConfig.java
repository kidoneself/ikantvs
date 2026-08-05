package com.jyinshi.ops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 活码 / 站内加群配置（单行 id=1）。ops 域。 */
@Data
@TableName("live_qrcode_config")
public class LiveQrcodeConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 微信群二维码图 URL。 */
    private String qrcodeImage;

    /** 公众号二维码图 URL。 */
    private String mpQrcodeImage;

    private String title;

    private String tipText;

    private Integer scanCount;

    /** 0 禁用 / 1 启用。 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
