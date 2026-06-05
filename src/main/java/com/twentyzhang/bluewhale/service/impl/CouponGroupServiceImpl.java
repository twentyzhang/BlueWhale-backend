package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.CouponGroupResponse;
import com.twentyzhang.bluewhale.dto.CreateCouponGroupRequest;
import com.twentyzhang.bluewhale.entity.Coupon;
import com.twentyzhang.bluewhale.entity.CouponGroup;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.CouponGroupMapper;
import com.twentyzhang.bluewhale.mapper.CouponMapper;
import com.twentyzhang.bluewhale.mapper.StoreMapper;
import com.twentyzhang.bluewhale.service.CouponGroupService;
import com.twentyzhang.bluewhale.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponGroupServiceImpl extends ServiceImpl<CouponGroupMapper, CouponGroup>
        implements CouponGroupService {

    private final StoreMapper  storeMapper;
    private final CouponMapper couponMapper;

    // ── 1. listCouponGroups ───────────────────────────────────────────────────

    @Override
    public IPage<CouponGroupResponse> getAvailableCouponGroups(int page, int size) {
        // 无需登录。QueryWrapper（字符串列名）避免无 Spring 上下文时 lambda cache 缺失。
        IPage<CouponGroup> groupPage = this.page(
                new Page<>(page, size),
                new QueryWrapper<CouponGroup>()
                        .gt("remain_count", 0)
                        .gt("expire_at", LocalDateTime.now())
                        .orderByDesc("created_at"));

        List<CouponGroup> groups = groupPage.getRecords();
        if (groups.isEmpty()) {
            return groupPage.convert(g -> null);
        }

        // 批量查商店名（全局券 storeId=null 过滤掉）
        List<Long> storeIds = groups.stream()
                .map(CouponGroup::getStoreId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        Map<Long, String> storeNameMap = new HashMap<>();
        if (!storeIds.isEmpty()) {
            storeMapper.selectBatchIds(storeIds)
                    .forEach(s -> storeNameMap.put(s.getId(), s.getName()));
        }

        return groupPage.convert(g -> CouponGroupResponse.builder()
                .id(g.getId())
                .name(g.getName())
                .type(g.getType())
                .value(g.getValue())
                .minOrderAmount(g.getMinOrderAmount())
                .expireAt(g.getExpireAt())
                .totalCount(g.getTotalCount())
                .remainCount(g.getRemainCount())
                .storeId(g.getStoreId())
                .storeName(storeNameMap.get(g.getStoreId())) // null-safe：全局券 storeId=null → null
                .build());
    }

    // ── 2. createStoreCouponGroup ─────────────────────────────────────────────

    @Override
    public Long createStoreCouponGroup(Long storeId, CreateCouponGroupRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_STAFF);
        AuthUtil.requireStoreAccess(storeId);

        validateCouponGroupRequest(request);

        CouponGroup group = buildGroup(request, storeId);
        save(group);
        return group.getId();
    }

    // ── 3. createGlobalCouponGroup ────────────────────────────────────────────

    @Override
    public Long createGlobalCouponGroup(CreateCouponGroupRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_ADMIN);

        validateCouponGroupRequest(request);

        CouponGroup group = buildGroup(request, null); // storeId = null 表示全局
        save(group);
        return group.getId();
    }

    // ── 4. deleteCouponGroup ──────────────────────────────────────────────────

    @Override
    public void deleteCouponGroup(Long operatorStoreId, String operatorRole, Long groupId) {
        CouponGroup group = getById(groupId);
        if (group == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "优惠券组不存在");
        }

        // Staff 权限：只能删除本店优惠券组，不能删全局券或他店券
        if (AuthUtil.ROLE_STAFF.equals(operatorRole)) {
            if (group.getStoreId() == null || !group.getStoreId().equals(operatorStoreId)) {
                throw new BusinessException(Result.CODE_FORBIDDEN, "无权删除该优惠券组");
            }
        }

        // 已有用户领取过则不允许删除
        long claimedCount = couponMapper.selectCount(
                new QueryWrapper<Coupon>()
                        .eq("group_id", groupId));
        if (claimedCount > 0) {
            throw new BusinessException("该优惠券组已有 " + claimedCount + " 人领取，无法删除");
        }

        removeById(groupId);
    }

    // ── 私有辅助方法 ──────────────────────────────────────────────────────────

    /** 校验优惠券组创建请求的业务规则。 */
    private void validateCouponGroupRequest(CreateCouponGroupRequest req) {
        if (req.getStartAt().isAfter(req.getExpireAt())) {
            throw new BusinessException("开始时间不能晚于过期时间");
        }
        if (req.getTotalCount() <= 0) {
            throw new BusinessException("总数量必须大于 0");
        }
        switch (req.getType()) {
            case "DISCOUNT" -> {
                BigDecimal v = req.getValue();
                if (v.compareTo(BigDecimal.ZERO) <= 0 || v.compareTo(BigDecimal.ONE) >= 0) {
                    throw new BusinessException("DISCOUNT 类型 value 必须在 0 到 1 之间（不含两端）");
                }
            }
            case "FULL_REDUCTION" -> {
                if (req.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("FULL_REDUCTION 类型 value 必须大于 0");
                }
                if (req.getMinOrderAmount() == null
                        || req.getMinOrderAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("FULL_REDUCTION（满减）类型 minOrderAmount 必须大于 0");
                }
            }
            case "DIRECT_OFF" -> {
                if (req.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("DIRECT_OFF 类型 value 必须大于 0");
                }
                if (req.getMinOrderAmount() != null
                        && req.getMinOrderAmount().compareTo(BigDecimal.ZERO) > 0) {
                    throw new BusinessException("DIRECT_OFF（直减）类型无门槛，minOrderAmount 必须为 0");
                }
            }
            default -> { /* @Pattern 已拦截非法 type，此处不再重复 */ }
        }
    }

    /** 根据请求构建 CouponGroup 实体，remainCount 初始值等于 totalCount。 */
    private CouponGroup buildGroup(CreateCouponGroupRequest req, Long storeId) {
        return CouponGroup.builder()
                .storeId(storeId)
                .name(req.getName())
                .type(req.getType())
                .value(req.getValue())
                .minOrderAmount(req.getMinOrderAmount())
                .totalCount(req.getTotalCount())
                .remainCount(req.getTotalCount()) // 初始余量 = 总量
                .startAt(req.getStartAt())
                .expireAt(req.getExpireAt())
                .build();
    }
}
