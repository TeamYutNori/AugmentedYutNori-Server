package com.teamyutnori.yutnori.auth;

import com.teamyutnori.yutnori.common.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

// 서버 로그인 토큰(JWT) 발급·검증.
//
// 예전에는 토큰을 서버 메모리에 저장해서, 서버를 재시작(배포)하면 모든 토큰이 무효가 됐다.
// 지금은 비밀키로 서명한 JWT라 서버가 저장하지 않고 서명만 확인한다 → 같은 비밀키면 재시작 후에도 그대로 유효.
//   - 비밀키: 환경변수 JWT_SECRET (32바이트 이상, 서버마다 같은 값). 없으면 실행할 때마다 임의 키 → 재시작하면 무효 (예전과 같음)
//   - 유효 기간: 환경변수 JWT_EXPIRATION (기본 7d)
//   - 토큰 내용: sub = playerId, iat/exp = 발급·만료 시각
// 주의: 서버가 토큰을 저장하지 않으므로, 발급한 토큰을 만료 전에 개별로 무효화할 수는 없다.
@Slf4j
@Component
public class TokenProvider {

    private static final String ISSUER = "augmented-yutnori";
    private static final int MIN_SECRET_BYTES = 32;   // HS256은 256비트 이상 키가 필요

    private final SecretKey key;
    private final Duration expiration;
    private final Clock clock;

    @Autowired
    public TokenProvider(
            @Value("${JWT_SECRET:}") String secret,
            @Value("${JWT_EXPIRATION:7d}") String expiration
    ) {
        // "7d", "12h", "PT30M" 같은 형식 (application.properties의 Duration과 같은 규칙)
        this(secret, DurationStyle.detectAndParse(expiration), Clock.systemUTC());
    }

    // 테스트에서 시간을 고정할 때 사용
    TokenProvider(String secret, Duration expiration, Clock clock) {
        this.key = createKey(secret);
        this.expiration = expiration;
        this.clock = clock;
    }

    private static SecretKey createKey(String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("[TokenProvider] JWT_SECRET이 없어 임시 키를 사용합니다. 서버를 재시작하면 모든 로그인 토큰이 무효가 됩니다.");
            return Jwts.SIG.HS256.key().build();
        }

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다. (현재 " + bytes.length + "바이트)");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    // 로그인 성공 후 사용할 서버 전용 토큰 발급
    public String createToken(String playerId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(playerId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    // 전달받은 토큰의 서명·만료를 확인하고 playerId를 반환
    // 서명이 다르거나(다른 서버 키, 위조) 형식이 틀리거나(예전 UUID 토큰, 게스트 토큰) 만료되면 401
    public String getPlayerId(String token) {
        if (token == null || token.isBlank()) throw invalid();

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String playerId = claims.getSubject();
            if (playerId == null || playerId.isBlank()) throw invalid();
            return playerId;
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException(
                    "EXPIRED_TOKEN",
                    "로그인이 만료되었습니다. 다시 로그인해 주세요."
            );
        } catch (JwtException | IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static UnauthorizedException invalid() {
        return new UnauthorizedException(
                "INVALID_TOKEN",
                "유효하지 않은 인증 토큰입니다."
        );
    }
}
