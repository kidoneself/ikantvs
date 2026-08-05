package com.jyinshi.ops.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 单个系统配置项（含 schema 元信息，供后台渲染表单）。 */
@Data
@AllArgsConstructor
public class SysConfigItemVO {

    private String key;
    private String value;
    private String label;
    private String group;
    /** ENUM / BOOL / NUMBER / TEXT */
    private String type;
    /** ENUM 类型的可选值，其它类型为空。 */
    private List<String> options;
    private String description;
}
