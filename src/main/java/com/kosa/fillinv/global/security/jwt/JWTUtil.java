package com.kosa.fillinv.global.security.jwt;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JWTUtil {

    private final SecretKey secretKey;
    private final JwtParser jwtParser;

    public JWTUtil(@Value("${jwt.secret}") String secret) {
        this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),
                Jwts.SIG.HS256.key().build().getAlgorithm());
        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .build();
    }

    public String getEmail(String token) {
        return jwtParser
                .parseSignedClaims(token)
                .getPayload()
                .get("email", String.class);
    }

    public Boolean isTokenExpired(String token) {
        return jwtParser
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration()
                .before(new Date());
    }

    public String getMemberId(String token) {
        return jwtParser
                .parseSignedClaims(token)
                .getPayload()
                .get("memberId", String.class);
    }

    public String getSubject(String token) {
        return jwtParser
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public String getRole(String token) {
        return jwtParser
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }

    public String createJwt(String email, String memberId, Long expiredMs) {
        return Jwts.builder()
                .subject(memberId)
                .claim("email", email)
                .claim("memberId", memberId)
                .claim("role", "USER")
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiredMs))
                .signWith(secretKey)
                .compact();
    }
}
