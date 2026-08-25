package com.jyinshi.transfer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.transfer.dto.PanPointerSaveRequest;
import com.jyinshi.transfer.dto.PanPointerVO;
import com.jyinshi.transfer.entity.TransferAccount;
import com.jyinshi.transfer.entity.TransferPanPointer;
import com.jyinshi.transfer.mapper.TransferAccountMapper;
import com.jyinshi.transfer.mapper.TransferPanPointerMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 每盘两个当前号（追更号 / 片库号）。不要用账号行上互斥的 transfer/monitor 标签选号。
 *
 * <p>未配置时：该盘只有一个可用号则回退用它；多个号却没配片库号 → 自营录入失败。</p>
 */
@Slf4j
@Service
public class TransferPanPointerService {

    public static final List<String> POINTER_PANS = List.of("quark", "baidu", "xunlei");

    private static final java.util.Map<String, String> PAN_LABEL = java.util.Map.of(
            "quark", "夸克", "baidu", "百度", "xunlei", "迅雷");

    private final TransferPanPointerMapper pointerMapper;
    private final TransferAccountMapper accountMapper;
    private final JdbcTemplate jdbc;

    public TransferPanPointerService(TransferPanPointerMapper pointerMapper,
                                     TransferAccountMapper accountMapper,
                                     JdbcTemplate jdbc) {
        this.pointerMapper = pointerMapper;
        this.accountMapper = accountMapper;
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void ensureTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS `transfer_pan_pointer` (
                  `pan_type` VARCHAR(16) NOT NULL,
                  `follow_account_name` VARCHAR(64) DEFAULT NULL,
                  `library_account_name` VARCHAR(64) DEFAULT NULL,
                  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (`pan_type`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每盘追更号/片库号指针'
                """);
    }

    public List<PanPointerVO> list() {
        List<PanPointerVO> out = new ArrayList<>();
        for (String pan : POINTER_PANS) {
            out.add(toVo(pan));
        }
        return out;
    }

    public void save(PanPointerSaveRequest req) {
        if (req == null || !StringUtils.hasText(req.getPanType())) {
            throw new BizException("panType 不能为空");
        }
        String pan = req.getPanType().toLowerCase(Locale.ROOT);
        if (!POINTER_PANS.contains(pan)) {
            throw new BizException("仅支持夸克/百度/迅雷配置当前号");
        }
        String follow = blankToNull(req.getFollowAccountName());
        String library = blankToNull(req.getLibraryAccountName());
        if (follow != null) {
            requireUsableName(pan, follow);
        }
        if (library != null) {
            requireUsableName(pan, library);
        }
        TransferPanPointer row = pointerMapper.selectById(pan);
        if (row == null) {
            row = new TransferPanPointer();
            row.setPanType(pan);
            row.setFollowAccountName(follow);
            row.setLibraryAccountName(library);
            row.setUpdatedAt(LocalDateTime.now());
            pointerMapper.insert(row);
        } else {
            row.setFollowAccountName(follow);
            row.setLibraryAccountName(library);
            row.setUpdatedAt(LocalDateTime.now());
            pointerMapper.updateById(row);
        }
        log.info("[账号] 更新指针 pan={} 追更号={} 片库号={}", pan, follow, library);
    }

    /**
     * 追更号。优先已配置指针；否则该盘唯一可用号；再否则历史上 role=monitor 的号（迁移）。
     */
    public String followAccountName(String panType) {
        String pan = norm(panType);
        if (pan == null) {
            return null;
        }
        TransferPanPointer row = pointerMapper.selectById(pan);
        if (row != null && usableName(pan, row.getFollowAccountName())) {
            return row.getFollowAccountName();
        }
        String only = onlyUsableName(pan);
        if (only != null) {
            return only;
        }
        return legacyMonitorName(pan);
    }

    /**
     * 片库号。优先已配置指针；否则该盘唯一可用号。多号未配则返回 null（调用方报错）。
     */
    public String libraryAccountName(String panType) {
        String pan = norm(panType);
        if (pan == null) {
            return null;
        }
        TransferPanPointer row = pointerMapper.selectById(pan);
        if (row != null && usableName(pan, row.getLibraryAccountName())) {
            return row.getLibraryAccountName();
        }
        return onlyUsableName(pan);
    }

    public String requireLibraryAccount(String panType) {
        String name = libraryAccountName(panType);
        if (StringUtils.hasText(name)) {
            return name;
        }
        String pan = panType == null ? "" : panType;
        throw new BizException("「" + label(pan) + "」未指定片库号，且该盘不止一个账号。请到转存 → 网盘账号页指定片库号，不要占用每日更新的追更号");
    }

    /** 被追更号 / 片库号占用的账号名（用于临时转存池排除）。 */
    public Set<String> reservedAccountNames(String panType) {
        String pan = norm(panType);
        Set<String> out = new HashSet<>();
        if (pan == null) {
            return out;
        }
        TransferPanPointer row = pointerMapper.selectById(pan);
        if (row == null) {
            return out;
        }
        if (StringUtils.hasText(row.getFollowAccountName())) {
            out.add(row.getFollowAccountName());
        }
        if (StringUtils.hasText(row.getLibraryAccountName())) {
            out.add(row.getLibraryAccountName());
        }
        return out;
    }

    public void clearAccount(String panType, String accountName) {
        String pan = norm(panType);
        if (pan == null || !StringUtils.hasText(accountName)) {
            return;
        }
        TransferPanPointer row = pointerMapper.selectById(pan);
        if (row == null) {
            return;
        }
        boolean changed = false;
        if (accountName.equals(row.getFollowAccountName())) {
            row.setFollowAccountName(null);
            changed = true;
        }
        if (accountName.equals(row.getLibraryAccountName())) {
            row.setLibraryAccountName(null);
            changed = true;
        }
        if (changed) {
            pointerMapper.updateById(row);
        }
    }

    private PanPointerVO toVo(String pan) {
        PanPointerVO vo = new PanPointerVO();
        vo.setPanType(pan);
        vo.setPanLabel(label(pan));
        TransferPanPointer row = pointerMapper.selectById(pan);
        if (row != null) {
            vo.setFollowAccountName(row.getFollowAccountName());
            vo.setLibraryAccountName(row.getLibraryAccountName());
        }
        List<String> names = new ArrayList<>();
        for (TransferAccount a : usableAccounts(pan)) {
            names.add(a.getAccountName());
        }
        vo.setAccountNames(names);
        return vo;
    }

    private List<TransferAccount> usableAccounts(String pan) {
        return accountMapper.selectList(new LambdaQueryWrapper<TransferAccount>()
                .eq(TransferAccount::getPanType, pan)
                .eq(TransferAccount::getEnabled, true)
                .isNotNull(TransferAccount::getCredential)
                .ne(TransferAccount::getCredential, "")
                .orderByAsc(TransferAccount::getAccountName));
    }

    private String onlyUsableName(String pan) {
        List<TransferAccount> list = usableAccounts(pan);
        return list.size() == 1 ? list.get(0).getAccountName() : null;
    }

    private boolean usableName(String pan, String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        return usableAccounts(pan).stream().anyMatch(a -> name.equals(a.getAccountName()));
    }

    private void requireUsableName(String pan, String name) {
        if (!usableName(pan, name)) {
            throw new BizException("账号不存在或不可用：" + name);
        }
    }

    private String legacyMonitorName(String pan) {
        TransferAccount a = accountMapper.selectOne(new LambdaQueryWrapper<TransferAccount>()
                .eq(TransferAccount::getPanType, pan)
                .eq(TransferAccount::getRole, "monitor")
                .eq(TransferAccount::getEnabled, true)
                .isNotNull(TransferAccount::getCredential)
                .ne(TransferAccount::getCredential, "")
                .orderByDesc(TransferAccount::getHealthy)
                .last("limit 1"));
        return a != null ? a.getAccountName() : null;
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static String norm(String panType) {
        return StringUtils.hasText(panType) ? panType.toLowerCase(Locale.ROOT) : null;
    }

    private static String label(String pan) {
        return PAN_LABEL.getOrDefault(pan == null ? "" : pan.toLowerCase(Locale.ROOT), pan);
    }
}
