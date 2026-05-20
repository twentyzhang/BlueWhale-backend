package com.twentyzhang.bluewhale.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.twentyzhang.bluewhale.dto.CouponGroupResponse;
import com.twentyzhang.bluewhale.dto.CreateCouponGroupRequest;
import com.twentyzhang.bluewhale.entity.CouponGroup;

public interface CouponGroupService extends IService<CouponGroup> {

    /**
     * 分页获取当前可领取的优惠券组列表（remainCount > 0，且在有效期内）。
     * 无需登录。
     * 对应 GET /api/coupon-groups
     */
    IPage<CouponGroupResponse> getAvailableCouponGroups(int page, int size);

    /**
     * 为指定商店发布新优惠券组。仅 Staff 可调用，且只能为本店发布。
     * 对应 POST /api/stores/{storeId}/coupon-groups
     *
     * @return 新建优惠券组 ID
     */
    Long createStoreCouponGroup(Long storeId, CreateCouponGroupRequest request);

    /**
     * 发布全局优惠券组（storeId = null，全平台通用）。仅 Admin 可调用。
     * 对应 POST /api/coupon-groups
     *
     * @return 新建优惠券组 ID
     */
    Long createGlobalCouponGroup(CreateCouponGroupRequest request);

    /**
     * 逻辑删除优惠券组。
     * Staff 只能删除本店的券组；Admin 可删除任意券组（含全局券）。
     * 对应 DELETE /api/coupon-groups/{groupId}
     *
     * @param operatorStoreId 操作者所属商店 ID（Admin 操作时传 null）
     * @param operatorRole    操作者角色（STAFF / ADMIN）
     */
    void deleteCouponGroup(Long operatorStoreId, String operatorRole, Long groupId);
}
