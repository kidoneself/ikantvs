package com.jyinshi.transfer.controller;

import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.ratelimit.RateLimit;
import com.jyinshi.transfer.dto.TransferExecuteRequest;
import com.jyinshi.transfer.dto.TransferResultVO;
import com.jyinshi.transfer.service.TransferRecordService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向用户的转存接口（前台详情页「转存」按钮）。匿名可用，按 IP 限频（Redis，跨实例共享）。
 * 异步：execute 命中缓存直接返回 done，否则返回 transferring+jobId，前端轮询 result。
 */
@RestController
@RequestMapping("/api/transfer")
public class TransferPublicController {

    private final TransferRecordService recordService;

    public TransferPublicController(TransferRecordService recordService) {
        this.recordService = recordService;
    }

    /** 点击转存：命中缓存秒返回我方链，否则入队首转并返回 jobId。单 IP 60 秒最多 15 次。 */
    @RateLimit(key = "transfer", time = 60, count = 15, message = "转存操作太频繁了")
    @PostMapping("/execute")
    public Result<TransferResultVO> execute(@Valid @RequestBody TransferExecuteRequest req) {
        return Result.success(recordService.execute(req));
    }

    /** 轮询转存结果。 */
    @GetMapping("/result")
    public Result<TransferResultVO> result(@RequestParam Long jobId) {
        return Result.success(recordService.result(jobId));
    }
}
