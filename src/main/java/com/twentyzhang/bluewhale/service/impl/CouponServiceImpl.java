package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.ClaimCouponResponse;
import com.twentyzhang.bluewhale.dto.MyCouponResponse;
import com.twentyzhang.bluewhale.entity.Coupon;
import com.twentyzhang.bluewhale.entity.CouponGroup;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.CouponGroupMapper;
import com.twentyzhang.bluewhale.mapper.CouponMapper;
import com.twentyzhang.bluewhale.service.CouponService;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.RedisLockUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements CouponService {

    private final CouponGroupMapper couponGroupMapper;
    private final RedisLockUtil redisLockUtil;

    /** 优惠券领取分布式锁过期秒数。 */
    private static final long CLAIM_LOCK_EXPIRE_SECONDS = 5;

    // ── 5. claimCoupon ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ClaimCouponResponse claimCoupon(Long userId, Long groupId) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);

        // 分布式锁：锁粒度为优惠券组级别（同组并发领取互斥，不同组互不影响）。
        // lockValue 用 UUID 标识持有者，解锁时据此防止误删他人的锁。
        String lockKey = "coupon_lock:groupId:" + groupId;
        String lockValue = UUID.randomUUID().toString();
        if (!redisLockUtil.tryLock(lockKey, lockValue, CLAIM_LOCK_EXPIRE_SECONDS)) {
            throw new BusinessException("系统繁忙，请稍后重试");
        }
        try {
            // 1. 校验优惠券组存在
            CouponGroup group = couponGroupMapper.selectById(groupId);
            if (group == null) {
                throw new BusinessException(Result.CODE_NOT_FOUND, "优惠券组不存在");
            }

            // 2. 校验未过期（startAt 已由前端/Controller 约束，此处只判断 expireAt）
            if (group.getExpireAt() != null && group.getExpireAt().isBefore(LocalDateTime.now())) {
                throw new BusinessException("优惠券已过期");
            }

            // 3. 双重检查：持锁后再查 remainCount，避免等锁期间库存已被其他请求消耗
            if (group.getRemainCount() <= 0) {
                throw new BusinessException("优惠券已被抢完");
            }

            // 4. 防止重复领取
            long alreadyClaimed = count(new LambdaQueryWrapper<Coupon>()
                    .eq(Coupon::getUserId, userId)
                    .eq(Coupon::getGroupId, groupId));
            if (alreadyClaimed > 0) {
                throw new BusinessException("您已领取过该优惠券");
            }

            // 5. remainCount 原子减 1，并以 remain_count > 0 守卫兜底：
            //    即使锁在事务提交前释放、他线程读到旧值，该条件也保证不会减成负数（防超发）。
            int rows = couponGroupMapper.update(null, new UpdateWrapper<CouponGroup>()
                    .eq("id", groupId)
                    .gt("remain_count", 0)
                    .setSql("remain_count = remain_count - 1"));
            if (rows == 0) {
                throw new BusinessException("优惠券已被抢完");
            }

            // 6. 创建优惠券记录
            Coupon coupon = Coupon.builder()
                    .groupId(groupId)
                    .userId(userId)
                    .status("UNUSED")
                    .build();
            save(coupon);

            return ClaimCouponResponse.builder().couponId(coupon.getId()).build();
        } finally {
            // 确保锁一定释放（Lua 脚本保证只删除本线程持有的锁）
            redisLockUtil.unlock(lockKey, lockValue);
        }
    }

    // ── 6. getMyCoupons ───────────────────────────────────────────────────────

    @Override
    public List<MyCouponResponse> getMyCoupons(Long userId, String status) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);

        // "EXPIRED" 可能来自两个来源：
        //   (a) DB status = "EXPIRED"（显式标记）
        //   (b) DB status = "UNUSED" 但 couponGroup.expireAt < now（自然过期）
        // 策略：拉全部券，Java 层计算有效状态后按 status 参数过滤。
        // 用户券数量通常极少（< 100），一次全量拉取性能可接受。
        List<Coupon> coupons = list(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getUserId, userId));

        if (coupons.isEmpty()) {
            return List.of();
        }

        // 批量查优惠券组信息（二次查询模式，决策文档第 16 条）
        List<Long> groupIds = coupons.stream()
                .map(Coupon::getGroupId).distinct().collect(Collectors.toList());
        Map<Long, CouponGroup> groupMap = new HashMap<>();
        couponGroupMapper.selectBatchIds(groupIds).forEach(g -> groupMap.put(g.getId(), g));

        LocalDateTime now = LocalDateTime.now();

        return coupons.stream()
                .map(c -> {
                    CouponGroup g = groupMap.get(c.getGroupId());
                    if (g == null) return null; // 优惠券组已被删除，跳过

                    // UNUSED 且已过期 → 显示为 EXPIRED（仅影响响应，不更新 DB）
                    String effectiveStatus = c.getStatus();
                    if ("UNUSED".equals(effectiveStatus)
                            && g.getExpireAt() != null
                            && g.getExpireAt().isBefore(now)) {
                        effectiveStatus = "EXPIRED";
                    }

                    // 按请求的 status 参数过滤（null 表示不过滤）
                    if (status != null && !status.equals(effectiveStatus)) {
                        return null;
                    }

                    return MyCouponResponse.builder()
                            .id(c.getId())
                            .groupName(g.getName())
                            .type(g.getType())
                            .value(g.getValue())
                            .minOrderAmount(g.getMinOrderAmount())
                            .expireAt(g.getExpireAt())
                            .status(effectiveStatus)
                            .storeId(g.getStoreId())
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }
}
