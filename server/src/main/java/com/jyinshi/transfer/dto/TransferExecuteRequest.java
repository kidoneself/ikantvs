package com.jyinshi.transfer.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * 用户点「转存」：二选一
 * <ul>
 *   <li>{@code mediaLinkId} — 详情/站内链，服务端按 id 解析源链</li>
 *   <li>{@code encryptUrl} — 流式搜索外源链的加密 token（对齐老站）</li>
 * </ul>
 */
@Data
public class TransferExecuteRequest {

    /** 详情页 / 站内搜索的 media_link.id。 */
    private Long mediaLinkId;

    /** 流式搜索返回的加密分享 token。 */
    private String encryptUrl;

    @AssertTrue(message = "请提供 mediaLinkId 或 encryptUrl")
    public boolean isValidTarget() {
        return mediaLinkId != null || StringUtils.hasText(encryptUrl);
    }
}
