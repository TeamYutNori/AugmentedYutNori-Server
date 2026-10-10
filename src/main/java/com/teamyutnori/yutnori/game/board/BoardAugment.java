package com.teamyutnori.yutnori.game.board;

import java.util.List;

// 이동에 영향을 주는 증강 효과 (Unity IAugment의 서버판)
// BoardState가 말 이동을 계산/적용하는 단계마다 등록된 증강을 priority 순서로 호출한다
//   1. modifyMoveCount : 굴린 칸 수 보정       (예: 잔걸음 강화, 빠른 출발)
//   2. modifyPaths     : 계산된 경로 추가/제거  (예: 빽도 전환)
//   3. modifyCaptures  : 잡힐 말 목록 보정      (예: 방패, 튼튼한 업기)
//   4. onMoved         : 이동이 끝난 뒤 효과    (예: 1회용 증강 소모)
// 증강은 모든 팀의 이동에 호출된다. 내 말에만 적용하려면 context.isMine(this)로 확인할 것
// 서버와 클라 결과가 같아야 하므로 랜덤·시간을 쓰지 않는다
// TODO: Unity Core/Augment/Augments의 증강 5개(WeakBoost, QuickStart, BackDoSwitch, Shield, SturdyCarry)를 이 인터페이스로 옮기기
public interface BoardAugment {

    String id();         // augments.json의 id와 같음 (예: "Shield")
    int ownerTeam();     // 이 증강을 가진 팀
    default int priority() { return 0; }   // 낮을수록 먼저, 같으면 등록 순서

    default int modifyMoveCount(AugmentContext context, int moveCount) { return moveCount; }
    default void modifyPaths(AugmentContext context, List<BoardPath> paths) { }
    default void modifyCaptures(AugmentContext context, List<Piece> captured) { }
    default void onMoved(AugmentContext context, MoveResult result) { }
}
