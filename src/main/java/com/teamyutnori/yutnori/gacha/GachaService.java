package com.teamyutnori.yutnori.gacha;

import com.teamyutnori.yutnori.common.NotFoundException;
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

// 프로필 아이콘 뽑기.
//  - 1회 PRICE 골드. 부족하면 Player.spendGold가 409 NOT_ENOUGH_GOLD
//  - 모든 아이콘이 profile-icons.json의 weight 비율로 나온다 (지금은 모두 같은 확률)
//  - 이미 가진 아이콘도 나올 수 있고, 중복 보상은 없다 (count만 올라감)
@Service
@RequiredArgsConstructor
public class GachaService {

    public static final int PRICE = 200;

    private final PlayerRepository playerRepository;
    private final PlayerIconRepository playerIconRepository;
    private final IconCatalog iconCatalog;
    private final RandomProvider randomProvider;
    private final ApplicationEventPublisher eventPublisher;

    // 골드 차감 → 뽑기 → 보유 목록 반영. 한 트랜잭션이라 중간에 실패하면 골드도 그대로다
    @Transactional
    public GachaResultResponse draw(String playerId) {
        Player player = findPlayer(playerId);
        player.spendGold(PRICE);

        IconDefinition picked = iconCatalog.pick(randomProvider);
        PlayerIcon owned = playerIconRepository.findByPlayerIdAndIconId(playerId, picked.id()).orElse(null);

        boolean duplicate = owned != null;
        if (duplicate) {
            owned.addDuplicate();
        } else {
            owned = playerIconRepository.save(PlayerIcon.first(playerId, picked.id()));
        }

        // 업적(뽑기 횟수)용. 이 트랜잭션이 커밋된 뒤에 집계된다
        eventPublisher.publishEvent(new GachaDrawnEvent(playerId, picked.id()));

        return new GachaResultResponse(picked.id(), duplicate, owned.getCount(), player.getGold(), PRICE);
    }

    private Player findPlayer(String playerId) {
        return playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new NotFoundException(
                        "PLAYER_NOT_FOUND",
                        "플레이어를 찾을 수 없습니다."
                ));
    }
}
