package com.jyinshi.transfer.notify;

import com.jyinshi.transfer.entity.TransferMonitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 兜底空实现：只打日志。真实推送由 {@link WebhookNotifyPort}（{@code @Primary}）负责。
 */
@Slf4j
@Component
public class LogNotifyPort implements NotifyPort {

    @Override
    public void syncUpdated(TransferMonitor monitor, String latestFileName) {
        log.info("[通知·占位] 追更更新: mediaLinkId={}, pan={}, 最新={}",
                monitor.getMediaLinkId(), monitor.getPanType(), latestFileName);
    }

    @Override
    public void monitorInvalid(TransferMonitor monitor) {
        log.info("[通知·占位] 追更源失效: mediaLinkId={}, pan={}, url={}",
                monitor.getMediaLinkId(), monitor.getPanType(), monitor.getShareUrl());
    }

    @Override
    public void accountInvalid(String panType, String accountName) {
        log.info("[通知·占位] 账号失效需重扫: pan={}, 账号={}", panType, accountName);
    }
}
