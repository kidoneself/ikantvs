package com.jyinshi.transfer.event;

/**
 * NAS 迅雷落地夹分享已就绪（只加百度也会建迅雷夹并分享）。
 *
 * <p>transfer 发布，content 监听后 upsert {@code media_link(source=self, pan=xunlei)}，
 * 前台才能看到迅雷自营链。跨域只走事件。</p>
 *
 * @param mediaId  剧 id
 * @param shareUrl 迅雷永久分享链（含 pwd）
 */
public record NasXunleiShareReadyEvent(Long mediaId, String shareUrl) {
}
