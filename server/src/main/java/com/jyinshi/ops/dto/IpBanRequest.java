package com.jyinshi.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 手动封禁 IP 请求。{@code permanent=true} 时忽略 durationSeconds。
 */
@Data
public class IpBanRequest {

    @NotBlank(message = "IP 不能为空")
    private String ip;

    /** 封禁时长（秒）。默认 24 小时。 */
    private int durationSeconds = 86400;

    /** 是否永久封禁。 */
    private boolean permanent = false;

    private String reason = "管理员手动封禁";
}
