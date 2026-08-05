package com.jyinshi.ops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 按域名的前台网盘开关。ops 域。 */
@Data
@TableName("site_domain_config")
public class SiteDomainConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归一化域名，如 naspt.vip。 */
    private String host;

    /** 0 禁用 / 1 启用。 */
    private Integer enabled;

    /** slug→bool JSON。 */
    private String pansJson;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
