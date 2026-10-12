package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.common.InvalidRequestException;
import com.teamyutnori.yutnori.game.augment.AugmentIds;
import com.teamyutnori.yutnori.game.board.MoveResult;
import com.teamyutnori.yutnori.game.exception.GameErrorCode;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.yut.YutResult;
import org.springframework.stereotype.Component;

import java.util.List;

// 턴 규칙 (Unity TurnManager + GameRules 턴 처리 이식)
// GameSession 값만 바꾸고 메시지 전송은 하지 않는다
//
// 던지기·턴에 영향을 주는 증강도 여기서 처리한다 (Unity GameRules와 같은 규칙)
//  - 사냥꾼(Hunter): 잡으면 2번 더 던지고, 윷·모가 나와도 한 번 더 없음
//  - 도박사(Gambler): 도↔모가 바뀌어 저장되고, 바뀐 결과는 한 번 더 없음
//    THROW_RESULT에는 원래 결과를 보내고 클라가 직접 바꾼다 → 서버는 저장값만 바꿔 양쪽을 맞춘다
@Component
public class TurnManager {

    // 사냥꾼이 잡았을 때 받는 추가 던지기 수 (Unity HunterAugment.CaptureBonusThrows)
    static final int HUNTER_CAPTURE_BONUS = 2;

    // 증강 선택이 끝난 뒤 첫 턴 (나간 팀은 건너뜀)
    public void startFirstTurn(GameSession s) {
        s.setCurrentTeam(firstActiveFrom(s, 0));
        resetTurn(s);
    }

    // 던진 결과 반영 (Unity GameRules.ApplyThrow + ConsumeThrow)
    // rolled: 윷가락으로 나온 원래 결과. 도박사면 도↔모를 바꿔 저장한다
    public void applyThrow(GameSession s, YutResult rolled) {
        YutResult stored = hasAugment(s, AugmentIds.GAMBLER) ? swapForGambler(rolled) : rolled;
        boolean swapped = stored != rolled;

        s.setRemainingThrows(s.getRemainingThrows() - 1);
        if (!swapped && grantsExtraThrow(s, stored)) {
            s.setRemainingThrows(s.getRemainingThrows() + 1);   // 윷·모 = 한 번 더 (사냥꾼·도박사 변환 제외)
        }
        s.getStoredResults().add(stored);
        s.setLatestResult(stored);
    }

    // 재던지기: 마지막 결과를 교체하고 보너스 다시 계산 (Unity ReplaceThrowResult)
    public void applyRethrow(GameSession s, YutResult newResult) {
        List<YutResult> stored = s.getStoredResults();
        int last = stored.size() - 1;
        YutResult oldResult = stored.get(last);
        stored.set(last, newResult);
        s.setLatestResult(newResult);
        s.setRethrownThisTurn(true);   // 야바위꾼은 턴당 1번

        // 다시 던진 결과는 도박사 변환을 하지 않는다 (Unity ApplyRethrow와 동일)
        if (grantsExtraThrow(s, oldResult)) s.setRemainingThrows(s.getRemainingThrows() - 1);
        if (grantsExtraThrow(s, newResult)) s.setRemainingThrows(s.getRemainingThrows() + 1);
    }

    // 이동 확정 후 (Unity MovePiece 뒷부분 + TryEndTurnIfNoAction)
    // result: 서버가 BoardState.tryMove로 직접 계산한 이동 결과 (클라 보고값을 쓰지 않음)
    // 턴이 바뀌었으면 true
    public boolean afterMove(GameSession s, MoveResult result) {
        removeUsedResult(s, result.moveCount());
        boolean hasRemainingAction = hasAnyMove(s);   // 남은 윷 결과로 움직일 말이 있는지 (판으로 계산)
        return finishMove(s, result.capturedCount(), hasRemainingAction);
    }

    // 판 없이 턴 규칙만 확인하는 테스트용 (같은 패키지에서만 호출 가능)
    // 실제 게임에서는 위 afterMove(GameSession, MoveResult)만 쓴다 → 클라가 보낸 값이 들어갈 길을 막는다
    boolean afterMove(GameSession s, int moveCount, int capturedCount, boolean hasRemainingAction) {
        removeUsedResult(s, moveCount);
        return finishMove(s, capturedCount, hasRemainingAction);
    }

    // 던지기 직후 (Unity ThrowYut 끝의 TryEndTurnIfNoAction)
    // 더 던질 것도 없고, 저장된 결과로 움직일 말도 없으면 턴을 넘긴다 (예: 판에 말이 없는데 빽도)
    // 턴이 바뀌었으면 true
    public boolean endTurnIfNoAction(GameSession s) {
        if (s.getRemainingThrows() == 0 && !hasAnyMove(s)) {
            nextTurn(s);
            return true;
        }
        return false;
    }

    // 현재 팀이 저장된 윷 결과 중 하나로 움직일 수 있는 말이 있는지 (Unity HasAnyStoredMove)
    public boolean hasAnyMove(GameSession s) {
        List<Integer> steps = s.getStoredResults().stream().map(YutResult::steps).toList();
        return s.getBoardState().hasAnyMove(s.getCurrentTeam(), steps);
    }

    private boolean finishMove(GameSession s, int capturedCount, boolean hasRemainingAction) {
        s.setMovedThisTurn(true);

        if (capturedCount > 0) {
            int bonus = hasAugment(s, AugmentIds.HUNTER) ? HUNTER_CAPTURE_BONUS : 1;
            s.setRemainingThrows(s.getRemainingThrows() + bonus);   // 잡으면 한 번 더 (사냥꾼은 2번)
        }

        // 던질 것도 없고, 남은 결과로 움직일 수 있는 말도 없으면 턴 종료
        if (s.getRemainingThrows() == 0 && !hasRemainingAction) {
            nextTurn(s);
            return true;
        }
        return false;
    }

    // 다음 팀으로 (Unity NextTurn). 나간 팀은 건너뛴다
    public void nextTurn(GameSession s) {
        s.setCurrentTeam(firstActiveFrom(s, s.getCurrentTeam() + 1));
        resetTurn(s);
    }

    // start부터 차례로 보며 아직 남아 있는 첫 팀
    private int firstActiveFrom(GameSession s, int start) {
        int teamCount = s.getSetup().teamCount();
        for (int i = 0; i < teamCount; i++) {
            int team = (start + i) % teamCount;
            if (!s.isRemoved(team)) return team;
        }
        return start % teamCount;   // 모두 나간 경우 (게임은 GameService가 이미 끝낸다)
    }

    // ── 내부 ──

    // 이번 결과로 한 번 더 던지는지 (사냥꾼이면 윷·모여도 없음) (Unity GameRules.GrantsExtraThrow)
    private boolean grantsExtraThrow(GameSession s, YutResult result) {
        return result.grantsExtraThrow() && !hasAugment(s, AugmentIds.HUNTER);
    }

    // 현재 차례 팀이 이 증강을 가졌는지
    private boolean hasAugment(GameSession s, String augmentId) {
        return s.hasAugment(s.getCurrentTeam(), augmentId);
    }

    // 도박사: 도는 모로, 모는 도로. 나머지는 그대로 (Unity GamblerAugment.Swap)
    static YutResult swapForGambler(YutResult result) {
        return switch (result) {
            case Do -> YutResult.Mo;
            case Mo -> YutResult.Do;
            default -> result;
        };
    }

    // 새 턴 초기화 (Unity StartTurn)
    private void resetTurn(GameSession s) {
        s.setTurnNumber(s.getTurnNumber() + 1);   // 새 턴 표식 (턴 타이머 구분용)
        s.setRemainingThrows(1);
        s.getStoredResults().clear();
        s.setMovedThisTurn(false);
        s.setRethrownThisTurn(false);
        s.setLatestResult(null);
    }

    // 이동에 쓴 결과를 칸 수로 찾아 하나 제거 (예: moveCount 2 → Gae)
    // 던져서 저장된 적 없는 결과로 이동했다고 보고하면 거부한다
    private void removeUsedResult(GameSession s, int moveCount) {
        YutResult used = s.getStoredResults().stream()
                .filter(r -> r.steps() == moveCount)
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException(GameErrorCode.INVALID_MOVE_RESULT,
                        "저장된 윷 결과 중 " + moveCount + "칸 결과가 없습니다."));
        s.getStoredResults().remove(used);
    }
}
