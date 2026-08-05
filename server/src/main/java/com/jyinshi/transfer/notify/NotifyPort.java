package com.jyinshi.transfer.notify;

import com.jyinshi.transfer.entity.TransferMonitor;

/**
 * 转存/追更事件的主动通知插口。
 *
 * <p>默认由 {@code WebhookNotifyPort}（飞书/企微 webhook）实现；未启用或未配置时不推送。
 * 换渠道时另写实现并 {@code @Primary} 即可。</p>
 */
public interface NotifyPort {

    /** 追更成功补到新内容：按剧攒批，飞书文案对齐老站「✔ 剧名 → 集数」。 */
    void syncUpdated(TransferMonitor monitor, String latestFileName);

    /** 追更源被判失效（死链/连续巡检失败）。 */
    void monitorInvalid(TransferMonitor monitor);

    /** 网盘账号凭据失效，需要重新扫码登录。 */
    void accountInvalid(String panType, String accountName);
}
