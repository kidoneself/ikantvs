package com.jyinshi.content.dto;

import lombok.Data;

import java.io.Serializable;

/** 前台短剧卡片（链接已加密）。 */
@Data
public class DramaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private Integer episodeCount;
    /** 加密后的夸克链 token */
    private String quarkLink;
    /** 加密后的百度链 token */
    private String baiduLink;
    private String coverImage;
}
