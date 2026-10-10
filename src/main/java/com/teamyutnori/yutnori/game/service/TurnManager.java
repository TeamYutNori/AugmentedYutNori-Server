package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.common.InvalidRequestException;
import com.teamyutnori.yutnori.game.board.MoveResult;
import com.teamyutnori.yutnori.game.exception.GameErrorCode;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.yut.YutResult;
import org.springframework.stereotype.Component;

import java.util.List;

// 턴 규칙 (Unity TurnManager + GameRules 턴 처리 이식)
// GameSession 값만 바꾸고 메시지 전송은 하지 않는다
@Component
public class TurnManager {

    // 증강 선택이 끝난 뒤 첫 턴
    public void startFirstTurn(GameSession s) {
        s.setCurrentTeam(0);
        resetTurn(s);
    }

    // 던진 결과 반영 (Unity ConsumeThrow)
    public void applyThrow(GameSession s, YutResult result) {
        s.setRemainingThrows(s.getRemainingThrows() - 1);
        if (result.grantsExtraThrow()) {
            s.setRemainingThrows(s.getRemainingThrows() + 1);   // 윷·모 = 한 번 더
        }
        s.getStoredResults().add(result);
        s.setLatestResult(result);
    }

    // 재던지기: 마지막 결과를 교체하고 보너스 다시 계산 (Unity ReplaceThrowResult)
    public void applyRethrow(GameSession s, YutResult newResult) {
        List<YutResult> stored = s.getStoredResults();
        int last = stored.size() - 1;
        YutResult oldResult = stored.get(last);
        stored.set(last, newResult);
        s.setLatestResult(newResult);

        if (oldResult.grantsExtraThrow()) s.setRemainingThrows(s.getRemainingThrows() - 1);
        if (newResult.grantsExtraThrow()) s.setRemainingThrows(s.getRemainingThrows() + 1);
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
            s.setRemainingThrows(s.getRemainingThrows() + 1);   // 잡으면 한 번 더
        }

        // 던질 것도 없고, 남은 결과로 움직일 수 있는 말도 없으면 턴 종료
        if (s.getRemainingThrows() == 0 && !hasRemainingAction) {
            nextTurn(s);
            return true;
        }
        return false;
    }

    // 다음 팀으로 (Unity NextTurn)
    public void nextTurn(GameSession s) {
        int next = (s.getCurrentTeam() + 1) % s.getSetup().teamCount();
        s.setCurrentTeam(next);
        resetTurn(s);
    }

    // ── 내부 ──

    // 새 턴 초기화 (Unity StartTurn)
    private void resetTurn(GameSession s) {
        s.setTurnNumber(s.getTurnNumber() + 1);   // 새 턴 표식 (턴 타이머 구분용)
        s.setRemainingThrows(1);
        s.getStoredResults().clear();
        s.setMovedThisTurn(false);
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
