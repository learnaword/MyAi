package com.interview.agent.auth;

import com.interview.agent.config.AppConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final AppConfig appConfig;

    public String generateToken(Long userId, String username, String role, int passwordVersion) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + appConfig.getJwt().getExpiration());
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("role", role)
                .claim("pv", passwordVersion)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long userId(Claims claims) {
        Object uid = claims.get("uid");
        if (uid instanceof Number n) {
            return n.longValue();
        }
        return uid == null ? null : Long.parseLong(uid.toString());
    }

    public String role(Claims claims) {
        Object role = claims.get("role");
        return role == null ? UserRole.USER.name() : role.toString();
    }

    public int passwordVersion(Claims claims) {
        Object pv = claims.get("pv");
        if (pv instanceof Number n) {
            return n.intValue();
        }
        return pv == null ? 0 : Integer.parseInt(pv.toString());
    }

    private SecretKey key() {
        byte[] bytes = appConfig.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            return Keys.hmacShaKeyFor(padded);
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
