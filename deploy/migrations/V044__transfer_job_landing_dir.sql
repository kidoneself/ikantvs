-- 首转顶层落地目录名：追更=追更资源 / 用户转存=临时转存。
-- worker 据此在账号根目录下建/复用父夹，把「剧名」夹落进去，实现追更与临时转存物理隔离。
ALTER TABLE transfer_job
    ADD COLUMN landing_dir VARCHAR(128) NULL COMMENT '首转顶层落地目录名(追更资源/临时转存)' AFTER target_folder_id;
