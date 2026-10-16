package com.teamyutnori.yutnori.gacha;

import com.teamyutnori.yutnori.common.InvalidRequestException;
import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.gacha.dto.GachaDrawResult;
import com.teamyutnori.yutnori.gacha.dto.GachaResultResponse;
import com.teamyutnori.yutnori.game.yut.RandomProvider;
import com.teamyutnori.yutnori.icon.IconCatalog;
import com.teamyutnori.yutnori.icon.IconDefinition;
import com.teamyutnori.yutnori.icon.PlayerIcon;
import com.teamyutnori.yutnori.icon.PlayerIconRepository;
import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 프로필 아이콘 뽑기.
//  - 1회 PRICE 골드, 10연차는 PRICE × 10 (할인 없음). 부족하면 Player.spendGold가 409 NOT_ENOUGH_GOLD
//  - 모든 아이콘이 profile-icons.json의 weight 비율로 나온다 (지금은 모두 같은 확률)
//  - 이미 가진 아이콘도 나올 수 있고, 중복 보상은 없다 (count만 올라감)
@Service
@RequiredArgsConstructor
public class GachaService {

    public static final int PRICE = 200;
    public static final Set<Integer> ALLOWED_TIMES = Set.of(1, 10);
    public static final String INVALID_DRAW_COUNT = "INVALID_DRAW_COUNT";

    private final PlayerRepository playerRepository;
    private final PlayerIconRepository playerIconRepository;
    private final IconCatalog iconCatalog;
    private final RandomProvider randomProvider;
    private final ApplicationEventPublisher eventPublisher;

    // 골드 차감 → 뽑기 → 보유 목록 반영. 한 트랜잭션이라 중간에 실패하면 골드도 그대로다
    @Transactional
    public GachaResultResponse draw(String playerId) {
        return draw(playerId, 1);
    }

    // N회 뽑기 (1회 또는 10연차). 골드는 PRICE × N을 처음에 한 번에 뺀다 (할인 없음)
    // 부족하면 409 NOT_ENOUGH_GOLD이고 아무것도 뽑지 않는다
    @Transactional
    public GachaResultResponse draw(String playerId, int times) {
        if (!ALLOWED_TIMES.contains(times)) {
            throw new InvalidRequestException(INVALID_DRAW_COUNT, "뽑기 횟수는 1 또는 10만 가능합니다: " + times);
        }
        Player player = findPlayer(playerId);
        int price = PRICE * times;
        player.spendGold(price);

        // 같은 10연차 안에서 같은 아이콘이 또 나올 수 있어서, 이번에 다룬 보유 아이콘을 들고 다닌다
        Map<String, PlayerIcon> touched = new HashMap<>();
        List<GachaDrawResult> results = new ArrayList<>(times);
        for (int i = 0; i < times; i++) {
            IconDefinition picked = iconCatalog.pick(randomProvider);
            PlayerIcon owned = touched.get(picked.id());
            if (owned == null) {
                owned = playerIconRepository.findByPlayerIdAndIconId(playerId, picked.id()).orElse(null);
            }

            boolean duplicate = owned != null;
            if (duplicate) {
                owned.addDuplicate();
            } else {
                owned = playerIconRepository.save(PlayerIcon.first(playerId, picked.id()));
            }
            touched.put(picked.id(), owned);
            results.add(new GachaDrawResult(picked.id(), duplicate, owned.getCount()));

            // 업적(뽑기 횟수)용. 한 번 뽑을 때마다 1개 → 10연차면 +10. 이 트랜잭션이 커밋된 뒤에 집계된다
            eventPublisher.publishEvent(new GachaDrawnEvent(playerId, picked.id()));
        }

        return GachaResultResponse.of(results, player.getGold(), price);
    }

    private Player findPlayer(String playerId) {
        return playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new NotFoundException(
                        "PLAYER_NOT_FOUND",
                        "플레이어를 찾을 수 없습니다."
                ));
    }
}
