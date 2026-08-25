package com.jyinshi.transfer.service;

import com.jyinshi.common.exception.BizException;
import com.jyinshi.transfer.config.TransferProperties;
import com.jyinshi.transfer.dto.JobEnqueueRequest;
import com.jyinshi.transfer.entity.TransferJob;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 自营录入：用片库号做一次永久转存，落地「自营片库」。不开启巡检。
 */
@Service
public class TransferLibraryService {

    public static final Set<String> LIBRARY_PANS = Set.of("quark", "baidu", "xunlei");

    private final TransferPanPointerService pointerService;
    private final TransferJobService jobService;
    private final TransferProperties props;

    public TransferLibraryService(TransferPanPointerService pointerService,
                                  TransferJobService jobService,
                                  TransferProperties props) {
        this.pointerService = pointerService;
        this.jobService = jobService;
        this.props = props;
    }

    public boolean supports(String panType) {
        return panType != null && LIBRARY_PANS.contains(panType.toLowerCase());
    }

    public String landingDir() {
        return props.getLibrary().getLandingDir();
    }

    public boolean isLibraryLanding(String landingDir) {
        return StringUtils.hasText(landingDir)
                && landingDir.equals(props.getLibrary().getLandingDir());
    }

    public String requireAccount(String panType) {
        if (!supports(panType)) {
            throw new BizException("该盘不支持转入片库（目前仅夸克/百度/迅雷）");
        }
        return pointerService.requireLibraryAccount(panType);
    }

    /**
     * 入队片库转存。已有同 link 的在途片库任务则返回该任务。
     *
     * @return job id
     */
    public Long enqueue(Long mediaLinkId, String panType, String shareUrl, String password) {
        if (mediaLinkId == null || !StringUtils.hasText(panType) || !StringUtils.hasText(shareUrl)) {
            throw new BizException("转存参数不完整");
        }
        TransferJob inflight = jobService.findLatestLibraryByMediaLinkId(mediaLinkId,
                props.getLibrary().getLandingDir());
        if (inflight != null && ("pending".equals(inflight.getStatus()) || "running".equals(inflight.getStatus()))) {
            return inflight.getId();
        }
        String acct = requireAccount(panType);
        JobEnqueueRequest req = new JobEnqueueRequest();
        req.setJobType("transfer");
        req.setPanType(panType.toLowerCase());
        req.setAccountName(acct);
        req.setShareUrl(shareUrl);
        req.setSharePwd(password);
        req.setMediaLinkId(mediaLinkId);
        req.setLandingDir(props.getLibrary().getLandingDir());
        req.setPriority(6);
        return jobService.enqueue(req).getId();
    }
}
