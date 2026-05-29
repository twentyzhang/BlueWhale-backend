package com.twentyzhang.bluewhale.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private static final String CLAIM_ROLE     = "role";
    private static final String CLAIM_STORE_ID = "storeId";

    /**
     * 生成 Token，Payload 包含 userId（subject）、role、storeId（Staff 才有值）
     */
    public String generateToken(Long userId, String role, Long storeId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_STORE_ID, storeId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key())
                .compact();
    }

    public Long getUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public String getRole(String token) {
        return parseClaims(token).get(CLAIM_ROLE, String.class);
    }

    /** Staff 返回所属商店 ID，其他角色返回 null */
    public Long getStoreId(String token) {
        Integer storeId = parseClaims(token).get(CLAIM_STORE_ID, Integer.class);
        return storeId != null ? storeId.longValue() : null;
    }

    /**
     * 校验 Token 是否合法（签名正确且未过期）
     * 合法返回 true，否则返回 false（不抛异常，由调用方决定如何响应）
     */
    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
