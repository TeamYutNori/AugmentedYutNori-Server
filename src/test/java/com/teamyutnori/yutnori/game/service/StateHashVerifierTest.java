package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.InvalidRequestException;
import com.teamyutnori.yutnori.game.service.StateHashVerifier.Result;
import com.teamyutnori.yutnori.game.service.StateHashVerifier.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateHashVerifierTest {

    private static final String ROOM = "ABCD";
    private static final String HASH_A = "0123456789abcdef";
    private static final String HASH_B = "fedcba9876543210";

    private StateHashVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new StateHashVerifier();
    }

    // 한 명만 해시를 보내면 PENDING (나머지 인원을 기다림)
    @Test
    void pending() {
        Result result = verifier.submit(ROOM, 1, "p1", HASH_A, 2);

        assertThat(result.status()).isEqualTo(Status.PENDING);
        assertThat(verifier.pendingCount(ROOM)).isEqualTo(1);
    }

    // 모두 같은 해시를 보내면 MATCH, 대기 목록에서 빠진다
    @Test
    void match() {
        verifier.submit(ROOM, 1, "p1", HASH_A, 2);
        Result result = verifier.submit(ROOM, 1, "p2", HASH_A, 2);

        assertThat(result.status()).isEqualTo(Status.MATCH);
        assertThat(verifier.pendingCount(ROOM)).isZero();
    }

    // 해시가 다르면 DESYNC, 결과에 플레이어별 해시가 담긴다
    @Test
    void desync() {
        verifier.submit(ROOM, 1, "p1", HASH_A, 2);
        Result result = verifier.submit(ROOM, 1, "p2", HASH_B, 2);

        assertThat(result.status()).isEqualTo(Status.DESYNC);
        assertThat(result.hashes()).containsEntry("p1", HASH_A).containsEntry("p2", HASH_B);
    }

    // 대문자로 온 해시도 소문자와 같은 값으로 본다
    @Test
    void ignoreCase() {
        verifier.submit(ROOM, 1, "p1", HASH_A, 2);
        Result result = verifier.submit(ROOM, 1, "p2", HASH_A.toUpperCase(), 2);

        assertThat(result.status()).isEqualTo(Status.MATCH);
    }

    // seq가 다르면 따로 모아서 비교한다 (순서가 뒤섞여 도착해도 됨)
    @Test
    void separateSeq() {
        verifier.submit(ROOM, 1, "p1", HASH_A, 2);
        verifier.submit(ROOM, 2, "p1", HASH_B, 2);

        assertThat(verifier.submit(ROOM, 2, "p2", HASH_B, 2).status()).isEqualTo(Status.MATCH);
        assertThat(verifier.submit(ROOM, 1, "p2", HASH_A, 2).status()).isEqualTo(Status.MATCH);
    }

    // 방이 다르면 데이터가 섞이지 않는다
    @Test
    void separateRoom() {
        verifier.submit("ROOM1", 1, "p1", HASH_A, 2);
        Result other = verifier.submit("ROOM2", 1, "p2", HASH_A, 2);

        assertThat(other.status()).isEqualTo(Status.PENDING);
    }

    // 같은 플레이어가 같은 해시를 다시 보내면 무시한다 (재전송 허용)
    @Test
    void duplicateSame() {
        verifier.submit(ROOM, 1, "p1", HASH_A, 2);
        Result result = verifier.submit(ROOM, 1, "p1", HASH_A, 2);

        assertThat(result.status()).isEqualTo(Status.PENDING);
        assertThat(result.hashes()).hasSize(1);
    }

    // 이미 판정이 끝난 seq에 늦게 온 해시는 기존 결과를 그대로 돌려준다
    @Test
    void lateSubmit() {
        verifier.submit(ROOM, 1, "p1", HASH_A, 2);
        verifier.submit(ROOM, 1, "p2", HASH_A, 2);

        Result late = verifier.submit(ROOM, 1, "p1", HASH_B, 2);

        assertThat(late.status()).isEqualTo(Status.MATCH);
        assertThat(verifier.pendingCount(ROOM)).isZero();
    }

    // clearRoom 후에는 대기 중이던 해시가 사라지고 처음부터 다시 모은다
    @Test
    void clearRoom() {
        verifier.submit(ROOM, 1, "p1", HASH_A, 2);
        verifier.clearRoom(ROOM);

        assertThat(verifier.pendingCount(ROOM)).isZero();
        assertThat(verifier.submit(ROOM, 1, "p2", HASH_A, 2).status()).isEqualTo(Status.PENDING);
    }

    // 같은 플레이어가 다른 해시를 다시 보내면 STATE_HASH_CONFLICT
    @Test
    void duplicateDifferent() {
        verifier.submit(ROOM, 1, "p1", HASH_A, 2);

        assertThatThrownBy(() -> verifier.submit(ROOM, 1, "p1", HASH_B, 2))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("STATE_HASH_CONFLICT");
    }

    // 16자리 hex가 아니거나 null이면 INVALID_STATE_HASH
    @Test
    void invalidHash() {
        assertThatThrownBy(() -> verifier.submit(ROOM, 1, "p1", "1234", 2))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_STATE_HASH");
        assertThatThrownBy(() -> verifier.submit(ROOM, 1, "p1", "zzzzzzzzzzzzzzzz", 2))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> verifier.submit(ROOM, 1, "p1", null, 2))
                .isInstanceOf(InvalidRequestException.class);
    }

    // 인원이 2 미만이면 서버 호출 실수라 기본 예외
    @Test
    void invalidExpectedPlayers() {
        assertThatThrownBy(() -> verifier.submit(ROOM, 1, "p1", HASH_A, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}