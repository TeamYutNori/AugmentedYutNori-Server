package com.teamyutnori.yutnori.game.augment;

// augments.json의 증강 id 모음. Unity AugmentFactories와 같은 문자열이어야 한다
public final class AugmentIds {
    private AugmentIds() {}

    // 이동 규칙 증강 (BoardAugment로 판에 등록)
    public static final String CONTRARIAN    = "Contrarian";    // 청개구리
    public static final String SPRINTER      = "Sprinter";      // 스프린터
    public static final String MONK          = "Monk";          // 수행자
    public static final String FRIEND_SHIELD = "FriendShield";  // 프렌드쉴드
    public static final String PACIFIST      = "Pacifist";      // 평화주의자
    public static final String MARATHONER    = "Marathoner";    // 마라토너
    public static final String ASSASSIN      = "Assassin";      // 암살자
    public static final String LONER         = "Loner";         // 외톨이
    public static final String VETERAN       = "Veteran";       // 노장 (말 수도 하나 적다 → GameSession.piecesFor)

    // 던지기·턴 규칙 증강 (TurnManager / RethrowValidator가 보유 여부만 확인)
    public static final String RETHROW       = "Rethrow";       // 야바위꾼
    public static final String HUNTER        = "Hunter";        // 사냥꾼
    public static final String GAMBLER       = "Gambler";       // 도박사
}
