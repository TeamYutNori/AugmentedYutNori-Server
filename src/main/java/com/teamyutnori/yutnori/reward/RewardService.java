package com.teamyutnori.yutnori.reward;

import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RankingEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 게임 결과(rankings)대로 플레이어 보유 골드에 보상을 지급한다.
// GAME_ENDED를 보내는 쪽(finishGame, 끊김 처리)이 rankings를 만든 뒤 grant(rankings)를 부르면 된다
@Slf4j
@Service
@RequiredArgsConstructor
public class RewardService {

    private final PlayerRepository playerRepository;

    // rewardGold가 0보다 큰 플레이어에게만 지급. 한 트랜잭션이라 중간에 실패하면 아무에게도 지급되지 않는다
    // 반환: 실제로 지급한 총 골드
    @Transactional
    public int grant(List<RankingEntry> rankings) {
        if (rankings == null || rankings.isEmpty()) return 0;

        int total = 0;
        for (RankingEntry entry : rankings) {
            if (entry.rewardGold() <= 0) continue;

            Player player = playerRepository.findByPlayerId(entry.playerId()).orElse(null);
            if (player == null) {
                // 게임 중 계정이 사라진 경우 등. 다른 사람 지급은 계속한다
                log.warn("[Reward] 보상 받을 플레이어가 없습니다: playerId={}, gold={}", entry.playerId(), entry.rewardGold());
                continue;
            }

            player.addGold(entry.rewardGold());
            total += entry.rewardGold();
        }
        return total;
    }
}
