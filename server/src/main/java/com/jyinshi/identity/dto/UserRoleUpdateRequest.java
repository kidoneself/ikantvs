package com.jyinshi.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 修改用户角色：user / contributor / reviewer / admin。 */
@Data
public class UserRoleUpdateRequest {

    @NotBlank(message = "角色不能为空")
    private String role;
}
