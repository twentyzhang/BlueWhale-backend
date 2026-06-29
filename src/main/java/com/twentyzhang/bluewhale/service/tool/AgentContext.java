package com.twentyzhang.bluewhale.service.tool;

/** Agent 执行上下文：入口由 AuthUser 构造，工具据此鉴权（不读 SecurityContext）。 */
public record AgentContext(Long userId, String role) {}
