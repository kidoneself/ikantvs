package com.jyinshi.transfer.pan.driver;

/**
 * 凭据回写出口（方案A：凭据集中存主站）。迅雷 refresh_token 每次刷新会滚动，
 * driver 刷新后经此把新 token 回写主站，保证重启后从主站拉到的仍是最新可用凭据。
 */
public interface CredentialSink {

    /** 迅雷等滚动型凭据刷新后回写新 refresh_token（best-effort，失败仅告警）。 */
    void onRefreshTokenRolled(PanType type, String accountName, String newRefreshToken);
}
