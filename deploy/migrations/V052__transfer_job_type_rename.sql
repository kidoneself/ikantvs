-- V052: job_type 对齐产品语义
-- probe→check(检查)  first_save→create|transfer  sync→update(更新)  delete 不变
-- first_save 拆分：绑了 monitor 号的视为监控「创建」，其余视为用户「转存」

UPDATE transfer_job SET job_type = 'check'  WHERE job_type = 'probe';
UPDATE transfer_job SET job_type = 'update' WHERE job_type = 'sync';

UPDATE transfer_job j
  INNER JOIN transfer_account a
    ON a.pan_type = j.pan_type
   AND a.account_name = j.account_name
   AND a.role = 'monitor'
SET j.job_type = 'create'
WHERE j.job_type = 'first_save';

UPDATE transfer_job SET job_type = 'transfer' WHERE job_type = 'first_save';
