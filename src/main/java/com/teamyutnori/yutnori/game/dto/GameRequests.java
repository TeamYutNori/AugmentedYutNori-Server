package com.teamyutnori.yutnori.game.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// 게임 도메인 클라 → 서버 payload. THROW_REQUEST는 내용 없음
public final class GameRequests {
    private GameRequests() {}

    // SELECT_AUGMENT
    public record SelectAugmentRequest(String augmentId) {}

    // RETHROW_REQUEST
    public record RethrowRequest(String augmentId) {}

    public record MoveRequest(        int pieceId,
                                      int moveCount,
                                      int destinationNodeId,
                                      int capturedCount,
                                      @JsonProperty("isFinished") boolean isFinished,
                                      boolean hasExtraThrow,
                                      boolean hasRemainingAction,
                                      String stateHash) {}


}
