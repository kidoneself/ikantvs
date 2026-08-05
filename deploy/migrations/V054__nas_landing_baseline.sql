-- NAS 只追新：建落地夹时记下当时百度文件名基线，之后差集排除基线（老的靠迅雷上游补）。
ALTER TABLE `nas_landing`
  ADD COLUMN `baseline_json` MEDIUMTEXT NULL
    COMMENT '建夹时百度相对路径文件名快照 JSON 数组，NAS 只灌基线之外的新文件'
    AFTER `xunlei_share_url`;
