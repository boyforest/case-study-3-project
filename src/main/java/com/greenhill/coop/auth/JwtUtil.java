package com.greenhill.coop.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtUtil(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expirationMillis) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (Exception e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(Long memberId) {
        Date now = new Date();
        return Jwts.builder()
            .subject(memberId.toString())
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expirationMillis))
            .signWith(signingKey)
            .compact();
    }

    public Long getMemberId(String token) {
        Claims claims = Jwts.parser().verifyWith(signingKey).build()
            .parseSignedClaims(token).getPayload();
        return Long.parseLong(claims.getSubject());
    }
}
