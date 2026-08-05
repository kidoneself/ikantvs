package com.jyinshi.search.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 流式搜索 SSE 事件（对齐老站 StreamSearchEvent）。
 * {@code items} 为批量推送，减少「一条一等」的延迟感。
 */
@Data
@NoArgsConstructor
public class StreamSearchEvent {

    /** start / item / items / complete / error */
    private String type;
    private String source;
    private ResourceItem item;
    /** 批量条目（优先于单条 item）。 */
    private List<ResourceItem> items;
    private String message;
    private String error;
    private Integer progress;

    @Data
    @NoArgsConstructor
    public static class ResourceItem {
        private String title;
        /** 加密 token（网盘转存）或明文（磁力/电驴/自营直显）。 */
        private String url;
        private String cloudType;
        private Boolean invalid;
        private Boolean local;
        private String latestEpisode;
        /** 站内 media_link.id；有则转存走 linkId，无则走加密 url。 */
        private Long mediaLinkId;
        private Long mediaId;

        public ResourceItem(String title, String url, String cloudType, Boolean invalid,
                            Boolean local, Long mediaLinkId, Long mediaId) {
            this(title, url, cloudType, invalid, local, mediaLinkId, mediaId, null);
        }

        public ResourceItem(String title, String url, String cloudType, Boolean invalid,
                            Boolean local, Long mediaLinkId, Long mediaId, String latestEpisode) {
            this.title = title;
            this.url = url;
            this.cloudType = cloudType;
            this.invalid = invalid;
            this.local = local;
            this.mediaLinkId = mediaLinkId;
            this.mediaId = mediaId;
            this.latestEpisode = latestEpisode;
        }
    }

    public static StreamSearchEvent start(String message) {
        StreamSearchEvent e = new StreamSearchEvent();
        e.setType("start");
        e.setMessage(message);
        e.setProgress(0);
        return e;
    }

    public static StreamSearchEvent item(String source, ResourceItem item, Integer progress) {
        StreamSearchEvent e = new StreamSearchEvent();
        e.setType("item");
        e.setSource(source);
        e.setItem(item);
        e.setProgress(progress);
        return e;
    }

    public static StreamSearchEvent items(String source, List<ResourceItem> items, Integer progress) {
        StreamSearchEvent e = new StreamSearchEvent();
        e.setType("items");
        e.setSource(source);
        e.setItems(items);
        e.setProgress(progress);
        return e;
    }

    public static StreamSearchEvent complete(String message, int total) {
        StreamSearchEvent e = new StreamSearchEvent();
        e.setType("complete");
        e.setMessage(message + "，共找到 " + total + " 条结果");
        e.setProgress(100);
        return e;
    }

    public static StreamSearchEvent error(String source, String error) {
        StreamSearchEvent e = new StreamSearchEvent();
        e.setType("error");
        e.setSource(source);
        e.setError(error);
        return e;
    }
}
