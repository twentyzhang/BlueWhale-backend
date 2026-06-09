package com.twentyzhang.bluewhale.util;

/** 实时客服 Redis key 集中定义（在线状态）。 */
public final class ChatKeys {

    private ChatKeys() {}

    /** 在线买家集合：SADD/SREM userId */
    public static final String ONLINE_CUSTOMERS = "cs:online:customers";

    /** 某店在线客服集合：SADD/SREM userId */
    public static String onlineStoreStaff(Long storeId) {
        return "cs:online:store:" + storeId;
    }
}
