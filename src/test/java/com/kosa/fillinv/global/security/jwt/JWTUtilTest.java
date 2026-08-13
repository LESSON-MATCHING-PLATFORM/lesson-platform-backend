package com.kosa.fillinv.global.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JWTUtilTest {

    private JWTUtil jwtUtil;
    private final String secret = "vmfhaltmskdlstkfkdgodyroqkfwkdbalroqkfwkdbalaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"; // 32byte
    private final Long expiredMs = 60000L; // 1분

    @BeforeEach
    void setUp() {
        jwtUtil = new JWTUtil(secret);
    }

    @Test
    @DisplayName("토큰 생성 및 검증 테스트")
    void createAndVerifyToken() {
        // given
        String email = "test@example.com";
        String memberId = "test-member-uuid";

        // when
        String token = jwtUtil.createJwt(email, memberId, expiredMs);

        // then
        assertNotNull(token);
        assertFalse(jwtUtil.isTokenExpired(token));
        assertEquals(email, jwtUtil.getEmail(token));
        assertEquals(memberId, jwtUtil.getMemberId(token));
        assertEquals(memberId, jwtUtil.getSubject(token));
        assertEquals("USER", jwtUtil.getRole(token));
    }

    @Test
    @DisplayName("토큰에서 memberId 추출 테스트")
    void getMemberIdTest() {
        // given
        String email = "user@test.com";
        String memberId = "unique-member-id-123";
        String token = jwtUtil.createJwt(email, memberId, expiredMs);

        // when
        String extractedId = jwtUtil.getMemberId(token);

        // then
        assertEquals(memberId, extractedId);
    }
}
