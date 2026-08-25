package com.jyinshi.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.content.dto.PoolIngestRequest;
import com.jyinshi.content.dto.PoolIngestRowVO;
import com.jyinshi.content.dto.PoolIngestResultVO;
import com.jyinshi.content.entity.MediaLink;
import com.jyinshi.content.ingest.ShareIdExtractor;
import com.jyinshi.content.mapper.MediaLinkMapper;
import com.jyinshi.content.pool.PanShareDetector;
import com.jyinshi.content.pool.PoolConstants;
import com.jyinshi.content.pool.PoolPasteParser;
import com.jyinshi.ops.service.SensitiveWordService;
import com.jyinshi.transfer.entity.TransferJob;
import com.jyinshi.transfer.service.TransferJobService;
import com.jyinshi.transfer.service.TransferLibraryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 未绑剧搜索池：同行只入库；自营先转到片库号再索引。
 */
@Slf4j
@Service
public class PoolIngestService {

    private static final Set<String> DIRECT = Set.of("magnet", "ed2k");

    private final MediaLinkMapper mediaLinkMapper;
    private final SensitiveWordService sensitiveWordService;
    private final TransferLibraryService libraryService;
    private final TransferJobService jobService;

    public PoolIngestService(MediaLinkMapper mediaLinkMapper,
                             SensitiveWordService sensitiveWordService,
                             TransferLibraryService libraryService,
                             TransferJobService jobService) {
        this.mediaLinkMapper = mediaLinkMapper;
        this.sensitiveWordService = sensitiveWordService;
        this.libraryService = libraryService;
        this.jobService = jobService;
    }

    public PoolIngestResultVO ingestPeer(PoolIngestRequest req) {
        List<PoolPasteParser.Item> items = resolveItems(req);
        PoolIngestResultVO result = new PoolIngestResultVO();
        for (PoolPasteParser.Item item : items) {
            result.addRow(ingestPeerOne(item));
        }
        return result;
    }

    public PoolIngestResultVO ingestSelf(PoolIngestRequest req) {
        List<PoolPasteParser.Item> items = resolveItems(req);
        PoolIngestResultVO result = new PoolIngestResultVO();
        for (PoolPasteParser.Item item : items) {
            result.addRow(ingestSelfOne(item));
        }
        return result;
    }

    public PoolIngestRowVO selfProgress(Long id) {
        if (id == null) {
            throw new BizException("id 不能为空");
        }
        MediaLink row = mediaLinkMapper.selectById(id);
        if (row == null || !Long.valueOf(PoolConstants.UNBOUND_MEDIA_ID).equals(row.getMediaId())) {
            throw new BizException("记录不存在");
        }
        PoolIngestRowVO vo = baseRow(row.getNote(), row.getPanType(), sourceUrlOf(row), null);
        vo.setId(row.getId());
        if (PoolConstants.SOURCE_SELF.equalsIgnoreCase(row.getSource()) && StringUtils.hasText(row.getUrl())) {
            vo.setStatus("done");
            vo.setShareUrl(row.getUrl());
            vo.setUrl(sourceUrlOf(row));
            return vo;
        }
        TransferJob job = jobService.findLatestLibraryByMediaLinkId(id, libraryService.landingDir());
        if (job == null) {
            job = jobService.findLatestByMediaLinkId(id);
        }
        if (job != null) {
            vo.setUrl(job.getShareUrl());
            vo.setShareUrl(job.getResultShareUrl());
            if ("done".equals(job.getStatus()) && StringUtils.hasText(job.getResultShareUrl())) {
                vo.setStatus("done");
                vo.setShareUrl(job.getResultShareUrl());
            } else if ("failed".equals(job.getStatus())) {
                vo.setStatus("failed");
                vo.setReason(StringUtils.hasText(job.getErrorMsg()) ? job.getErrorMsg() : "转存失败");
            } else {
                vo.setStatus("transferring");
            }
            return vo;
        }
        vo.setStatus("failed");
        vo.setReason("尚未转存或任务已丢失");
        return vo;
    }

    private List<PoolPasteParser.Item> resolveItems(PoolIngestRequest req) {
        if (req == null) {
            throw new BizException("请粘贴标题和网盘链接");
        }
        if (req.getItems() != null && !req.getItems().isEmpty()) {
            List<PoolPasteParser.Item> out = new ArrayList<>();
            for (PoolIngestRequest.PoolIngestItemInput in : req.getItems()) {
                if (in == null || !StringUtils.hasText(in.getUrl())) {
                    continue;
                }
                String url = in.getUrl().trim();
                String pan = PanShareDetector.detect(url);
                if (pan == null) {
                    continue;
                }
                String title = StringUtils.hasText(in.getTitle()) ? in.getTitle().trim() : "未命名资源";
                if (title.length() > 255) {
                    title = title.substring(0, 255);
                }
                String pwd = StringUtils.hasText(in.getPassword()) ? in.getPassword().trim()
                        : PanShareDetector.extractPwd(url);
                out.add(new PoolPasteParser.Item(title, url, pwd, pan));
                if (out.size() >= PoolConstants.MAX_ITEMS) {
                    break;
                }
            }
            return out;
        }
        String text = req.getText();
        if (!StringUtils.hasText(text)) {
            throw new BizException("请粘贴标题和网盘链接");
        }
        if (text.length() > PoolConstants.MAX_CHARS) {
            throw new BizException("文本过长，最多约 8 万字");
        }
        return PoolPasteParser.parse(text);
    }

    private PoolIngestRowVO ingestPeerOne(PoolPasteParser.Item item) {
        PoolIngestRowVO vo = baseRow(item.title(), item.panType(), item.url(), null);
        if (sensitiveWordService.isBlocked(item.title())) {
            vo.setStatus("skipped");
            vo.setReason("标题命中敏感词");
            return vo;
        }
        String url = MediaLinkUrlNormalizer.normalize(item.url(), item.panType());
        String shareId = ShareIdExtractor.extract(url, item.panType());
        MediaLink exist = findUnbound(item.panType(), shareId);
        if (exist != null) {
            vo.setId(exist.getId());
            if (isProtected(exist)) {
                vo.setStatus("skipped");
                vo.setReason("已是自营/手工，未覆盖");
                return vo;
            }
            exist.setNote(item.title());
            exist.setUrl(url);
            exist.setLastSeenAt(LocalDateTime.now());
            mediaLinkMapper.updateById(exist);
            vo.setStatus("updated");
            vo.setReason("已更新标题/源链");
            return vo;
        }
        MediaLink row = newUnbound(item, shareId, PoolConstants.SOURCE_POOL);
        try {
            mediaLinkMapper.insert(row);
        } catch (DuplicateKeyException e) {
            MediaLink again = findUnbound(item.panType(), shareId);
            if (again != null) {
                vo.setId(again.getId());
                vo.setStatus("skipped");
                vo.setReason("并发重复");
                return vo;
            }
            throw e;
        }
        vo.setId(row.getId());
        vo.setStatus("added");
        return vo;
    }

    private PoolIngestRowVO ingestSelfOne(PoolPasteParser.Item item) {
        PoolIngestRowVO vo = baseRow(item.title(), item.panType(), item.url(), null);
        if (sensitiveWordService.isBlocked(item.title())) {
            vo.setStatus("skipped");
            vo.setReason("标题命中敏感词");
            return vo;
        }
        String sourceUrl = MediaLinkUrlNormalizer.normalize(item.url(), item.panType());
        String shareId = ShareIdExtractor.extract(sourceUrl, item.panType());
        MediaLink exist = findUnbound(item.panType(), shareId);

        if (exist != null && PoolConstants.SOURCE_SELF.equalsIgnoreCase(exist.getSource())
                && StringUtils.hasText(exist.getUrl())) {
            vo.setId(exist.getId());
            vo.setStatus("skipped");
            vo.setShareUrl(exist.getUrl());
            vo.setReason("已有我方链，未重转");
            return vo;
        }

        if (DIRECT.contains(item.panType())) {
            MediaLink row = exist != null ? exist : newUnbound(item, shareId, PoolConstants.SOURCE_SELF);
            row.setSource(PoolConstants.SOURCE_SELF);
            row.setNote(item.title());
            row.setUrl(sourceUrl);
            row.setInvalid(0);
            saveOrUpdate(row, exist != null);
            vo.setId(row.getId());
            vo.setStatus("done");
            vo.setShareUrl(sourceUrl);
            return vo;
        }

        if (!libraryService.supports(item.panType())) {
            vo.setStatus("failed");
            vo.setReason("该盘不支持转入片库（目前仅夸克/百度/迅雷）");
            return vo;
        }
        try {
            libraryService.requireAccount(item.panType());
        } catch (BizException e) {
            vo.setStatus("failed");
            vo.setReason(e.getMessage());
            return vo;
        }

        MediaLink row = exist != null ? exist : newUnbound(item, shareId, PoolConstants.SOURCE_SELF);
        row.setSource(PoolConstants.SOURCE_SELF);
        row.setNote(item.title());
        row.setUrl("");
        saveOrUpdate(row, exist != null);
        vo.setId(row.getId());

        try {
            libraryService.enqueue(row.getId(), item.panType(), sourceUrl, item.password());
        } catch (BizException e) {
            vo.setStatus("failed");
            vo.setReason(e.getMessage());
            return vo;
        }
        vo.setStatus("transferring");
        return vo;
    }

    private void saveOrUpdate(MediaLink row, boolean update) {
        if (update) {
            row.setLastSeenAt(LocalDateTime.now());
            mediaLinkMapper.updateById(row);
            return;
        }
        try {
            mediaLinkMapper.insert(row);
        } catch (DuplicateKeyException e) {
            MediaLink again = findUnbound(row.getPanType(), row.getShareId());
            if (again != null) {
                row.setId(again.getId());
                mediaLinkMapper.updateById(row);
            } else {
                throw e;
            }
        }
    }

    private MediaLink newUnbound(PoolPasteParser.Item item, String shareId, String source) {
        MediaLink row = new MediaLink();
        row.setMediaId(PoolConstants.UNBOUND_MEDIA_ID);
        row.setPanType(item.panType());
        row.setUrl(PoolConstants.SOURCE_POOL.equals(source)
                ? MediaLinkUrlNormalizer.normalize(item.url(), item.panType()) : "");
        row.setShareId(shareId);
        row.setNote(item.title());
        row.setSource(source);
        row.setStatus("approved");
        row.setInvalid(0);
        row.setLastSeenAt(LocalDateTime.now());
        return row;
    }

    private MediaLink findUnbound(String panType, String shareId) {
        return mediaLinkMapper.selectOne(Wrappers.<MediaLink>lambdaQuery()
                .eq(MediaLink::getMediaId, PoolConstants.UNBOUND_MEDIA_ID)
                .eq(MediaLink::getPanType, panType)
                .eq(MediaLink::getShareId, shareId)
                .last("LIMIT 1"));
    }

    private static boolean isProtected(MediaLink row) {
        String src = row.getSource();
        return PoolConstants.SOURCE_SELF.equalsIgnoreCase(src) || "manual".equalsIgnoreCase(src);
    }

    private String sourceUrlOf(MediaLink row) {
        TransferJob job = jobService.findLatestByMediaLinkId(row.getId());
        if (job != null && StringUtils.hasText(job.getShareUrl())
                && !job.getShareUrl().startsWith("delete-batch:")) {
            return job.getShareUrl();
        }
        return row.getUrl();
    }

    private static PoolIngestRowVO baseRow(String title, String pan, String url, String reason) {
        return PoolIngestRowVO.of(title, pan, PanShareDetector.label(pan), url, null, reason);
    }
}
