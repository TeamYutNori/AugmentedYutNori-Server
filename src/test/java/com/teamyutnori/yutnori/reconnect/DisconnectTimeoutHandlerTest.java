package com.teamyutnori.yutnori.reconnect;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DisconnectTimeoutHandlerTest {

    private static final String ROOM = "ABC123";

    // 게임 쪽 가짜 구현: 진행 여부, 팀 매핑, 제거·종료 기록만 한다
    static class FakeGame implements GameEndPort {
        boolean playing = true;
        final Map<String, Integer> teams = new HashMap<>(Map.of("p0", 0, "p1", 1));
        final List<String> removed = new ArrayList<>();
        Integer endedWinner;

        public boolean isPlaying(String roomCode) { return playing; }

        public Optional<Integer> findTeam(String roomCode, String playerId) {
            return Optional.ofNullable(teams.get(playerId));
        }

        public void endGame(String roomCode, int winnerTeam) {
            endedWinner = winnerTeam;
            playing = false;
        }

        // 게임 참가자면 제거 기록 후 true, 아니면 false (GameService.removePlayer와 같은 반환 규칙)
        @Override
        public boolean removePlayer(String roomCode, String playerId) {
            if (!teams.containsKey(playerId)) return false;
            removed.add(playerId);
            return true;
        }
    }

    private FakeGame game;
    private DisconnectTimeoutHandler handler;

    @BeforeEach
    void setUp() {
        game = new FakeGame();
        handler = new DisconnectTimeoutHandler(Optional.of(game));
    }

    // 대기 시간이 지나면 removePlayer로 그 플레이어를 뺀다
    @Test
    void removesPlayer() {
        handler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p0"));

        assertThat(game.removed).containsExactly("p0");
    }

    // 게임 종료(endGame)는 직접 하지 않는다 (남은 팀이 1개 이하면 GameService.finishGame이 처리)
    @Test
    void noDirectEnd() {
        handler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p1"));

        assertThat(game.endedWinner).isNull();
    }

    // 여러 명이 끊기면 각자 따로 제거된다
    @Test
    void bothDisconnected() {
        handler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p1"));
        handler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p0"));

        assertThat(game.removed).containsExactly("p1", "p0");
    }

    // 게임 중이 아니면 아무것도 안 한다
    @Test
    void notPlaying() {
        game.playing = false;

        handler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p0"));

        assertThat(game.removed).isEmpty();
    }

    // 게임 참가자가 아니면 제거되지 않고 예외도 없다 (로그만 남김)
    @Test
    void unknownPlayer() {
        assertThatCode(() -> handler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "ghost")))
                .doesNotThrowAnyException();

        assertThat(game.removed).isEmpty();
        assertThat(game.endedWinner).isNull();
    }

    // GameEndPort 구현이 없으면 건너뛴다 (서버는 정상 동작)
    @Test
    void noPort() {
        DisconnectTimeoutHandler noPortHandler = new DisconnectTimeoutHandler(Optional.empty());

        assertThatCode(() -> noPortHandler.onDisconnectTimeout(new DisconnectTimeoutEvent(ROOM, "p1")))
                .doesNotThrowAnyException();
    }
}