package com.jyinshi.identity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 修改用户状态：0 正常 / 1 封禁。 */
@Data
public class UserStatusUpdateRequest {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
