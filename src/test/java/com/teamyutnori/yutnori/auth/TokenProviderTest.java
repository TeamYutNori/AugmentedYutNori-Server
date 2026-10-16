package com.teamyutnori.yutnori.auth;

import com.teamyutnori.yutnori.common.UnauthorizedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenProviderTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-long!!";
    private static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");

    private static TokenProvider provider(String secret, Instant now) {
        return new TokenProvider(secret, Duration.ofDays(7), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void 발급한_토큰에서_playerId를_꺼낸다() {
        TokenProvider provider = provider(SECRET, NOW);

        String token = provider.createToken("player-1");

        assertThat(provider.getPlayerId(token)).isEqualTo("player-1");
    }

    @Test
    void 같은_비밀키면_서버를_다시_띄워도_토큰이_유효하다() {
        String token = provider(SECRET, NOW).createToken("player-1");

        // 재시작 = 새 TokenProvider. 메모리에 저장된 것이 없어도 서명만으로 검증된다
        TokenProvider restarted = provider(SECRET, NOW.plusSeconds(60));

        assertThat(restarted.getPlayerId(token)).isEqualTo("player-1");
    }

    @Test
    void 다른_비밀키로_서명한_토큰은_거절한다() {
        String token = provider(SECRET, NOW).createToken("player-1");
        TokenProvider other = provider("another-secret-at-least-32-bytes-long", NOW);

        assertThatThrownBy(() -> other.getPlayerId(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_TOKEN");
    }

    @Test
    void 내용을_고친_토큰은_거절한다() {
        TokenProvider provider = provider(SECRET, NOW);
        String token = provider.createToken("player-1");
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA." + parts[2];

        assertThatThrownBy(() -> provider.getPlayerId(forged))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 만료된_토큰은_EXPIRED_TOKEN으로_거절한다() {
        String token = provider(SECRET, NOW).createToken("player-1");
        TokenProvider eightDaysLater = provider(SECRET, NOW.plus(Duration.ofDays(8)));

        assertThatThrownBy(() -> eightDaysLater.getPlayerId(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasFieldOrPropertyWithValue("code", "EXPIRED_TOKEN");
    }

    @Test
    void 예전_UUID_토큰이나_빈_값은_INVALID_TOKEN() {
        TokenProvider provider = provider(SECRET, NOW);

        // WebSocket 접속은 INVALID_TOKEN이면 게스트 토큰(UUID)으로 다시 찾으므로 같은 예외여야 한다
        assertThatThrownBy(() -> provider.getPlayerId(UUID.randomUUID().toString()))
                .isInstanceOf(UnauthorizedException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_TOKEN");
        assertThatThrownBy(() -> provider.getPlayerId(""))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> provider.getPlayerId(null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 비밀키가_없으면_임시_키로_동작하고_재시작하면_무효() {
        TokenProvider first = provider("", NOW);
        String token = first.createToken("player-1");

        assertThat(first.getPlayerId(token)).isEqualTo("player-1");
        assertThatThrownBy(() -> provider("", NOW).getPlayerId(token))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 비밀키가_32바이트보다_짧으면_서버_시작을_막는다() {
        assertThatThrownBy(() -> provider("too-short", NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
