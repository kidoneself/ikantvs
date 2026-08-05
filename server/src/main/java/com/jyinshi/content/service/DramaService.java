package com.jyinshi.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.content.config.DramaProperties;
import com.jyinshi.content.dto.DramaVO;
import com.jyinshi.content.entity.Drama;
import com.jyinshi.content.mapper.DramaMapper;
import com.jyinshi.search.util.LinkEncryptUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 短剧：TGForwarder 导入 + 前台列表/搜索。
 * 封面按内容 SHA-256 落盘，可与老站共用同一宿主机目录（同图只一份文件）。
 */
@Slf4j
@Service
public class DramaService {

    private final DramaMapper mapper;
    private final DramaProperties props;

    public DramaService(DramaMapper mapper, DramaProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    public boolean validateToken(String token) {
        return StringUtils.hasText(token) && token.equals(props.getImportToken());
    }

    public PageResult<DramaVO> listForUser(int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 50);
        Page<Drama> result = mapper.selectPage(new Page<>(p, s),
                new LambdaQueryWrapper<Drama>()
                        .eq(Drama::getStatus, 1)
                        .orderByDesc(Drama::getMessageTime)
                        .orderByDesc(Drama::getId));
        return PageResult.of(result.getTotal(), p, s, toVoList(result.getRecords()));
    }

    public PageResult<DramaVO> searchForUser(String keyword, int page, int size) {
        if (!StringUtils.hasText(keyword)) {
            throw new BizException("搜索关键词不能为空");
        }
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 50);
        Page<Drama> result = mapper.selectPage(new Page<>(p, s),
                new LambdaQueryWrapper<Drama>()
                        .eq(Drama::getStatus, 1)
                        .like(Drama::getTitle, keyword.trim())
                        .orderByDesc(Drama::getMessageTime)
                        .orderByDesc(Drama::getId));
        return PageResult.of(result.getTotal(), p, s, toVoList(result.getRecords()));
    }

    public long countActive() {
        return mapper.selectCount(new LambdaQueryWrapper<Drama>().eq(Drama::getStatus, 1));
    }

    /**
     * TGForwarder 导入：按夸克链 upsert。
     */
    public Drama importDrama(String title, String quarkLink, String baiduLink,
                             Integer episodeCount, String sourceChannel,
                             String messageTime, MultipartFile cover) {
        String coverUrl = null;
        if (cover != null && !cover.isEmpty()) {
            try {
                coverUrl = saveCoverImage(cover);
            } catch (Exception e) {
                log.warn("[短剧导入] 封面保存失败: {}", e.getMessage());
            }
        }

        LocalDateTime parsedTime = LocalDateTime.now();
        if (StringUtils.hasText(messageTime)) {
            try {
                String raw = messageTime.trim();
                if (raw.length() >= 19) {
                    parsedTime = LocalDateTime.parse(raw.substring(0, 19),
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                }
            } catch (Exception e) {
                log.warn("[短剧导入] 时间解析失败: {}", messageTime);
            }
        }

        Drama existing = mapper.selectOne(new LambdaQueryWrapper<Drama>()
                .eq(Drama::getQuarkLink, quarkLink)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();

        if (existing != null) {
            existing.setTitle(title);
            if (StringUtils.hasText(baiduLink)) {
                existing.setBaiduLink(baiduLink);
            }
            if (episodeCount != null) {
                existing.setEpisodeCount(episodeCount);
            }
            if (coverUrl != null) {
                existing.setCoverImage(coverUrl);
            }
            if (StringUtils.hasText(sourceChannel)) {
                existing.setSourceChannel(sourceChannel);
            }
            existing.setMessageTime(parsedTime);
            existing.setUpdatedAt(now);
            mapper.updateById(existing);
            log.info("[短剧导入] 更新: {} | 夸克: {}", title, quarkLink);
            return existing;
        }

        Drama drama = new Drama();
        drama.setTitle(title);
        drama.setQuarkLink(quarkLink);
        drama.setBaiduLink(baiduLink);
        drama.setEpisodeCount(episodeCount);
        drama.setSourceChannel(sourceChannel);
        drama.setMessageTime(parsedTime);
        drama.setCoverImage(coverUrl);
        drama.setStatus(1);
        drama.setCreatedAt(now);
        drama.setUpdatedAt(now);
        mapper.insert(drama);
        log.info("[短剧导入] 新增: {} | 夸克: {} | 来源: {}", title, quarkLink, sourceChannel);
        return drama;
    }

    public int updateCoverByQuarkLink(String quarkLink, MultipartFile cover) throws IOException {
        String coverUrl = saveCoverImage(cover);
        return mapper.update(null, new LambdaUpdateWrapper<Drama>()
                .eq(Drama::getQuarkLink, quarkLink)
                .isNull(Drama::getCoverImage)
                .set(Drama::getCoverImage, coverUrl)
                .set(Drama::getUpdatedAt, LocalDateTime.now()));
    }

    /** 内容哈希文件名；已存在则跳过写入。 */
    String saveCoverImage(MultipartFile file) throws IOException {
        Path dir = Path.of(props.getCoverPath());
        Files.createDirectories(dir);

        byte[] bytes = file.getBytes();
        String filename = sha256Hex(bytes).substring(0, 16) + ".jpg";
        Path dest = dir.resolve(filename);
        if (!Files.exists(dest)) {
            Files.write(dest, bytes);
            log.debug("[短剧导入] 封面已保存: {}", dest.toAbsolutePath());
        } else {
            log.debug("[短剧导入] 封面已存在(哈希去重): {}", dest.toAbsolutePath());
        }
        String prefix = props.getCoverUrlPrefix();
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + "/" + filename;
    }

    private List<DramaVO> toVoList(List<Drama> list) {
        return list.stream().map(this::toVo).collect(Collectors.toList());
    }

    private DramaVO toVo(Drama d) {
        DramaVO vo = new DramaVO();
        vo.setId(d.getId());
        vo.setTitle(d.getTitle());
        vo.setEpisodeCount(d.getEpisodeCount());
        vo.setCoverImage(d.getCoverImage());
        try {
            if (StringUtils.hasText(d.getQuarkLink())) {
                vo.setQuarkLink(LinkEncryptUtil.encrypt(d.getQuarkLink(), "", "quark"));
            }
            if (StringUtils.hasText(d.getBaiduLink())) {
                String url = d.getBaiduLink();
                String password = "";
                int idx = url.indexOf("?pwd=");
                if (idx >= 0) {
                    password = url.substring(idx + 5);
                    url = url.substring(0, idx);
                }
                vo.setBaiduLink(LinkEncryptUtil.encrypt(url, password, "baidu"));
            }
        } catch (Exception e) {
            log.warn("[短剧] 链接加密失败 id={}: {}", d.getId(), e.getMessage());
        }
        return vo;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(bytes);
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("计算封面哈希失败", e);
        }
    }
}
