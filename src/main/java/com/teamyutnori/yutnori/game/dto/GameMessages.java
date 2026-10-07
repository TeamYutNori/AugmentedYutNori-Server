package com.teamyutnori.yutnori.game.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.teamyutnori.yutnori.game.yut.YutResult;

import java.util.List;

// 게임 도메인 서버 → 클라 payload. 필드 이름은 Unity Messages/*.cs와 1:1로 맞춘다
public final class GameMessages {
    private GameMessages() {}

    // THROW_RESULT  던지기·재던지기 공통. sticks: 길이 4, true = 평평한 면
    // isRethrow: boolean 이름이 is로 시작하면 Jackson이 "rethrow"로 바꿔 내보낼 수 있어서 JSON 이름을 고정한다 (Unity 필드명 isRethrow)
    public record ThrowResultMessage(int team, boolean[] sticks, YutResult result,
                                     @JsonProperty("isRethrow") boolean isRethrow) {}

    // TURN_CHANGED  turnNumber: 새 턴마다 1씩 증가하는 턴 번호
    public record TurnChangedMessage(int team, int remainingThrows, int turnTimeLimitSec, int turnNumber) {}

    // AUGMENT_CHOICES  해당 팀 플레이어에게만 보낸다
    public record AugmentChoicesMessage(int team, List<String> augmentIds, int timeLimitSec) {}

    // AUGMENT_SELECTED  방 전체에 보낸다
    public record AugmentSelectedMessage(int team, String augmentId, boolean autoSelected) {}

    // GAME_ENDED는 재접속 담당과 같이 쓰도록 ws/dto/WsMessages.GameEndedMessage를 사용한다
}
