package com.jyinshi.transfer.event;

/**
 * 追更首转成功、我方稳定分享链已生成的领域事件。
 *
 * <p>transfer 域发布，content 域监听后把展示用的 {@code media_link.url} 回写成我方链，
 * 让前台点开看到的是我们自己账号的稳定分享（而非上游大佬的原链）。跨域只走事件，
 * transfer 不直接写 content 的表。</p>
 *
 * @param mediaLinkId 锚点链接 id
 * @param myShareUrl  我方稳定分享链
 */
public record AnchorLinkReadyEvent(Long mediaLinkId, String myShareUrl) {
}
