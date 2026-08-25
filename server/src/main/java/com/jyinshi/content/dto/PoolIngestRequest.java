package com.jyinshi.content.dto;

import lombok.Data;

import java.util.List;

/** 同行 / 自营录入请求：粘贴文本或结构化 items。 */
@Data
public class PoolIngestRequest {

    private String text;
    private List<PoolIngestItemInput> items;

    @Data
    public static class PoolIngestItemInput {
        private String title;
        private String url;
        private String password;
    }
}
