package com.jyinshi.content.dto;

import com.jyinshi.content.entity.Ranking;
import lombok.Data;

import java.util.List;

/** 榜单对外 VO。{@link #items} 仅在需要时填充（公开列表 / 后台详情）。 */
@Data
public class RankingVO {

    private Long id;
    private String name;
    private String slug;
    private String description;
    private Integer sort;
    private Integer enabled;
    private Integer itemCount;
    private List<MediaVO> items;

    public static RankingVO from(Ranking r) {
        RankingVO vo = new RankingVO();
        vo.id = r.getId();
        vo.name = r.getName();
        vo.slug = r.getSlug();
        vo.description = r.getDescription();
        vo.sort = r.getSort();
        vo.enabled = r.getEnabled();
        return vo;
    }
}
