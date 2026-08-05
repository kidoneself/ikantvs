-- push-to-wake：worker 心跳上报可被主站访问的基址，主站入队后 POST /api/wake 秒级唤醒（仍保留轮询兜底）。
ALTER TABLE `transfer_worker`
  ADD COLUMN `wake_url` VARCHAR(255) NULL COMMENT '主站唤醒该 worker 的基址(如 http://ip:8080)' AFTER `note`;
