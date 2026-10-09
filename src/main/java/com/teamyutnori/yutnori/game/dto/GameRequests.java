package com.teamyutnori.yutnori.game.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 게임 도메인 클라 → 서버 payload. THROW_REQUEST는 내용 없음
public final class GameRequests {
    private GameRequests() {}

    // SELECT_AUGMENT
    public record SelectAugmentRequest(String augmentId) {}

    // RETHROW_REQUEST
    public record RethrowRequest(String augmentId) {}

    // MOVE  "어떤 말을, 어떤 윷 결과(칸 수)로, 어느 칸으로"만 받는다
    //  - moveCount: 사용할 윷 결과의 칸 수 (증강 보정 전, 빽도 = -1)
    //  - destinationNodeId: 지름길·빽도 전환처럼 갈 길이 여러 개일 때 어디로 갈지
    //  - stateHash: 보낸 쪽이 이동 후 계산한 상태 해시. 그대로 MOVE_APPLIED에 실어 다른 기기가 비교한다
    // 잡은 수·완주 여부 같은 결과값은 클라가 보내도 무시하고 서버가 판으로 계산한다 (ignoreUnknown)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MoveRequest(int pieceId, int moveCount, int destinationNodeId, String stateHash) {}
}
