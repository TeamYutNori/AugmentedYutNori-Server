package com.teamyutnori.yutnori.game.exception;

// 게임 도메인 오류 코드. 클라 ErrorMessage.code로 그대로 나가므로 Unity 쪽과 문자열이 같아야 한다.
// 예외 종류는 common 패키지 것을 쓴다 (괄호 = 사용할 예외)
public final class GameErrorCode {
    private GameErrorCode() {}

    public static final String GAME_NOT_FOUND           = "GAME_NOT_FOUND";            // NotFound
    public static final String PLAYER_NOT_IN_GAME       = "PLAYER_NOT_IN_GAME";        // Forbidden
    public static final String NOT_YOUR_TURN            = "NOT_YOUR_TURN";             // Forbidden
    public static final String GAME_ALREADY_STARTED     = "GAME_ALREADY_STARTED";      // Conflict
    public static final String INVALID_PHASE            = "INVALID_PHASE";             // Conflict
    public static final String AUGMENT_ALREADY_SELECTED = "AUGMENT_ALREADY_SELECTED";  // Conflict
    public static final String NO_THROWS_LEFT           = "NO_THROWS_LEFT";            // InvalidRequest
    public static final String INVALID_AUGMENT          = "INVALID_AUGMENT";           // InvalidRequest
    public static final String RETHROW_NOT_ALLOWED      = "RETHROW_NOT_ALLOWED";       // InvalidRequest
    public static final String INVALID_MOVE_RESULT      = "INVALID_MOVE_RESULT";       // InvalidRequest
    public static final String CANNOT_PASS_TURN         = "CANNOT_PASS_TURN";          // InvalidRequest
    public static final String INVALID_BOARD_LAYOUT     = "INVALID_BOARD_LAYOUT";      // InvalidRequest
}
