package com.teamyutnori.yutnori.game.augment;

import com.teamyutnori.yutnori.game.augment.effect.AssassinAugment;
import com.teamyutnori.yutnori.game.augment.effect.ContrarianAugment;
import com.teamyutnori.yutnori.game.augment.effect.FriendShieldAugment;
import com.teamyutnori.yutnori.game.augment.effect.LonerAugment;
import com.teamyutnori.yutnori.game.augment.effect.MarathonerAugment;
import com.teamyutnori.yutnori.game.augment.effect.MonkAugment;
import com.teamyutnori.yutnori.game.augment.effect.PacifistAugment;
import com.teamyutnori.yutnori.game.augment.effect.SprinterAugment;
import com.teamyutnori.yutnori.game.board.BoardAugment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;

// 증강 id → 판에 등록할 효과 객체 (Unity AugmentFactories의 서버판)
//
// - 이동 규칙 증강 8종: 증강을 고르면 GameService가 create()로 만들어 BoardState에 등록한다
// - 던지기·턴 규칙 증강 3종(야바위꾼·사냥꾼·도박사): 판 효과가 없고, TurnManager·RethrowValidator가 보유 여부만 본다
//
// augments.json에 있는데 여기 없는 id가 있으면 서버 시작 때 예외 → Unity에 새 증강을 추가하면 서버도 같이 추가할 것
@Component
public class AugmentEffects {

    private static final Map<String, IntFunction<BoardAugment>> BOARD_EFFECTS = Map.of(
            AugmentIds.CONTRARIAN,    ContrarianAugment::new,
            AugmentIds.SPRINTER,      SprinterAugment::new,
            AugmentIds.MONK,          MonkAugment::new,
            AugmentIds.FRIEND_SHIELD, FriendShieldAugment::new,
            AugmentIds.PACIFIST,      PacifistAugment::new,
            AugmentIds.MARATHONER,    MarathonerAugment::new,
            AugmentIds.ASSASSIN,      AssassinAugment::new,
            AugmentIds.LONER,         LonerAugment::new
    );

    private static final Set<String> RULE_ONLY = Set.of(
            AugmentIds.RETHROW, AugmentIds.HUNTER, AugmentIds.GAMBLER
    );

    public AugmentEffects(AugmentCatalog catalog) {
        for (String id : catalog.allIds()) {
            if (!BOARD_EFFECTS.containsKey(id) && !RULE_ONLY.contains(id)) {
                throw new IllegalStateException("augments.json의 '" + id + "' 증강 효과가 서버에 없습니다. AugmentEffects에 추가하세요.");
            }
        }
    }

    // 이동 규칙 증강이면 효과 객체, 던지기·턴 규칙 증강이면 empty
    public Optional<BoardAugment> create(String id, int team) {
        IntFunction<BoardAugment> factory = BOARD_EFFECTS.get(id);
        return factory == null ? Optional.empty() : Optional.of(factory.apply(team));
    }
}
