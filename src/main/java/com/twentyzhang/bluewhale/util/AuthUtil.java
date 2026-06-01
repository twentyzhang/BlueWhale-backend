package com.twentyzhang.bluewhale.util;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;

public class AuthUtil {

    public static final String ROLE_CUSTOMER = "CUSTOMER";
    public static final String ROLE_STAFF    = "STAFF";
    public static final String ROLE_ADMIN    = "ADMIN";

    private AuthUtil() {}

    /**
     * 从 SecurityContext 获取当前登录用户，未登录则抛出 401。
     */
    public static AuthUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            throw new BusinessException(Result.CODE_UNAUTHORIZED, "请先登录");
        }
        return user;
    }

    /**
     * 返回当前登录用户 ID，未登录则抛出 401。
     */
    public static Long getCurrentUserId() {
        return getCurrentUser().userId();
    }

    /**
     * 校验当前用户角色是否在允许列表中，不满足则抛出 403。
     * 用法：AuthUtil.requireRole(AuthUtil.ROLE_ADMIN)
     */
    public static AuthUser requireRole(String... roles) {
        AuthUser user = getCurrentUser();
        if (Arrays.stream(roles).noneMatch(r -> r.equals(user.role()))) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "权限不足");
        }
        return user;
    }

    /**
     * 校验当前 Staff 用户是否有权操作指定商店，storeId 不匹配则抛出 403。
     */
    public static void requireStoreAccess(Long storeId) {
        AuthUser user = getCurrentUser();
        if (!storeId.equals(user.storeId())) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "无权操作该商店");
        }
    }
}
