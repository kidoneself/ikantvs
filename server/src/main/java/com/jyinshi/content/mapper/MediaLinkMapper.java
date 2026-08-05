package com.jyinshi.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jyinshi.content.entity.MediaLink;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MediaLinkMapper extends BaseMapper<MediaLink> {

    /**
     * 幂等入库：唯一键 {@code (media_id, pan_type, share_id)} 命中则更新，否则插入。
     *
     * <p>更新语义：note/url 用最新采集覆盖（较新覆盖）、刷新 last_seen_at（新鲜度）；
     * <b>但 manual / self 链接不被覆盖</b>（人工录入与站长精选保持不动——self 的 url 只能是我方链，
     * 绝不能被外源采集盖回上游大佬链）；<b>不碰 invalid/check_state</b>
     * （死活只在转存点击时判，死链软删记忆不因再次采集复活）。
     *
     * @return MyBatis 受影响行数（1=插入，2=更新，语义不精确，新增计数请在 service 侧算）
     */
    @Insert("""
            INSERT INTO media_link
              (media_id, pan_type, url, share_id, note, source, status, invalid, last_seen_at, created_at, updated_at)
            VALUES
              (#{mediaId}, #{panType}, #{url}, #{shareId}, #{note}, #{source}, 'approved', 0, NOW(), NOW(), NOW())
            ON DUPLICATE KEY UPDATE
              note = IF(source IN ('manual', 'self'), note, VALUES(note)),
              url = IF(source IN ('manual', 'self'), url, VALUES(url)),
              last_seen_at = NOW(),
              updated_at = NOW()
            """)
    int upsert(@Param("mediaId") Long mediaId,
               @Param("panType") String panType,
               @Param("url") String url,
               @Param("shareId") String shareId,
               @Param("note") String note,
               @Param("source") String source);
}
