package com.teamyutnori.yutnori.game.augment;

import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.yut.YutResult;
import org.springframework.stereotype.Component;

// 재던지기 사용 가능 여부 검사 (Unity RethrowAugment.CanUse + GameRules.TryRethrow 조건과 동일)
//
// "지금 이 팀의 차례인지"는 GameService가 먼저 확인하므로 여기서는 보지 않는다.
// 증강 소모(보유 목록에서 제거)와 실제 다시 던지기는 GameService가 한다. 여기서는 판단만 한다.
@Component
public class RethrowValidator {

    // augments.json의 재던지기 증강 id. 클라이언트도 같은 문자열을 쓴다
    public static final String RETHROW_AUGMENT_ID = "Rethrow";

    // augments.json에 "Rethrow"가 없으면 이 기능이 영영 동작하지 않으므로 서버 시작 때 바로 알린다
    public RethrowValidator(AugmentCatalog catalog) {
        if (!catalog.exists(RETHROW_AUGMENT_ID)) {
            throw new IllegalStateException("augments.json에 '" + RETHROW_AUGMENT_ID + "' 증강이 없습니다.");
        }
    }

    // 아래 조건을 모두 만족하면 true
    public boolean canRethrow(GameSession session, int team) {
        // 1) 재던지기 증강을 갖고 있어야 한다 (1회용이라 한 번 쓰면 GameService가 지운다)
        if (!session.hasAugment(team, RETHROW_AUGMENT_ID)) {
            return false;
        }

        // 2) 이번 턴에 말을 한 번이라도 움직였으면 불가 (Unity: state.HasMovedThisTurn)
        if (session.isMovedThisTurn()) {
            return false;
        }

        // 3) 다시 던질 결과가 있어야 한다.
        //    TurnManager.applyRethrow가 storedResults의 마지막 결과를 바꾸므로 목록도 비어 있으면 안 된다
        YutResult latest = session.getLatestResult();
        if (latest == null || session.getStoredResults().isEmpty()) {
            return false;
        }

        // 4) 윷·모는 다시 던질 수 없다 (Unity RethrowAugment.CanUse: !latestResult.GrantsExtraThrow())
        return !latest.grantsExtraThrow();
    }
}
