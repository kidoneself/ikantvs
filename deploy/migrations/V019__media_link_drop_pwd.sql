-- url 存完整分享串（含提取码），不再单独拆 pwd
UPDATE media_link
SET url = CONCAT(url, CHAR(10), '提取码：', pwd)
WHERE pwd IS NOT NULL AND TRIM(pwd) <> '';

ALTER TABLE media_link DROP COLUMN pwd;
