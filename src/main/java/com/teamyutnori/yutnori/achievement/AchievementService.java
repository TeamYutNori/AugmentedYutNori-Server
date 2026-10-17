package com.teamyutnori.yutnori.achievement;

import com.teamyutnori.yutnori.achievement.dto.AchievementListResponse;
import com.teamyutnori.yutnori.achievement.dto.AchievementResponse;
import com.teamyutnori.yutnori.achievement.dto.ClaimAchievementResponse;
import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 업적 집계·달성 판정·보상
//  - 횟수가 오르면(record) 기준을 넘은 업적을 '달성'으로 기록하고, 보상은 플레이어가 직접 받는다(claim)
//  - 횟수를 올리는 이벤트 연결은 AchievementEventListener
//  - 집계는 항상 자기 트랜잭션(REQUIRES_NEW)에서 한다. 실패해도 로그인·게임 종료·뽑기에 영향 없음
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementService {

    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final AchievementCatalog catalog;
    private final PlayerStatRepository statRepository;
    private final PlayerAchievementRepository achievementRepository;
    private final PlayerRepository playerRepository;
    private final Clock clock;

    // ═══════════ 집계 ═══════════

    // 오늘 처음 접속이면 +1 (서울 날짜 기준)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogin(String playerId) {
        PlayerStat stat = statOf(playerId);
        if (stat.recordLogin(LocalDate.now(clock.withZone(ZONE)))) {
            unlock(playerId, stat, AchievementStat.LOGIN_DAYS);
        }
    }

    // 횟수 +1 후 기준을 넘은 업적 달성 처리
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String playerId, AchievementStat which) {
        record(playerId, which, 1);
    }

    // 횟수 +amount 후 기준을 넘은 업적 달성 처리 (10연차 뽑기는 한 번에 +10)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String playerId, AchievementStat which, int amount) {
        PlayerStat stat = statOf(playerId);
        stat.increment(which, amount);
        unlock(playerId, stat, which);
    }

    // ═══════════ API ═══════════

    // 내 업적 목록 (진행도·달성·수령 여부). 업적이 새로 추가돼 이미 기준을 넘은 경우도 여기서 달성 처리
    @Transactional
    public AchievementListResponse list(String playerId) {
        PlayerStat stat = statRepository.findByPlayerId(playerId).orElse(null);
        if (stat != null) {
            for (AchievementStat which : AchievementStat.values()) unlock(playerId, stat, which);
        }

        Map<String, PlayerAchievement> mine = new HashMap<>();
        for (PlayerAchievement record : achievementRepository.findAllByPlayerId(playerId)) {
            mine.put(record.getAchievementId(), record);
        }

        List<AchievementResponse> result = new ArrayList<>();
        for (AchievementDefinition definition : catalog.all()) {
            int value = stat == null ? 0 : stat.get(definition.stat());
            PlayerAchievement record = mine.get(definition.id());
            result.add(new AchievementResponse(
                    definition.id(), definition.name(), definition.description(), definition.stat().name(),
                    definition.goal(), Math.min(value, definition.goal()), definition.rewardGold(),
                    record != null, record != null && record.isClaimed()));
        }
        return new AchievementListResponse(result);
    }

    // 보상 받기: 달성했고 아직 안 받았을 때만 골드 지급
    @Transactional
    public ClaimAchievementResponse claim(String playerId, String achievementId) {
        AchievementDefinition definition = catalog.find(achievementId)
                .orElseThrow(() -> new NotFoundException("ACHIEVEMENT_NOT_FOUND", "없는 업적입니다: " + achievementId));
        PlayerAchievement record = achievementRepository.findByPlayerIdAndAchievementId(playerId, achievementId)
                .orElseThrow(() -> new ConflictException("ACHIEVEMENT_NOT_ACHIEVED", "아직 달성하지 않은 업적입니다."));
        if (record.isClaimed()) {
            throw new ConflictException("ACHIEVEMENT_ALREADY_CLAIMED", "이미 보상을 받은 업적입니다.");
        }

        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new NotFoundException("PLAYER_NOT_FOUND", "플레이어를 찾을 수 없습니다."));
        player.addGold(definition.rewardGold());
        record.claim(clock.instant());

        return new ClaimAchievementResponse(achievementId, definition.rewardGold(), player.getGold());
    }

    // ═══════════ 내부 ═══════════

    private PlayerStat statOf(String playerId) {
        return statRepository.findByPlayerId(playerId)
                .orElseGet(() -> statRepository.save(PlayerStat.of(playerId)));
    }

    // 이 횟수 종류의 업적 중 기준을 넘었는데 아직 기록이 없는 것 → 달성 기록
    private void unlock(String playerId, PlayerStat stat, AchievementStat which) {
        int value = stat.get(which);
        for (AchievementDefinition definition : catalog.byStat(which)) {
            if (value < definition.goal()) continue;
            if (achievementRepository.findByPlayerIdAndAchievementId(playerId, definition.id()).isPresent()) continue;
            achievementRepository.save(PlayerAchievement.achieved(playerId, definition.id(), clock.instant()));
            log.info("[Achievement] 달성 player={} achievement={}", playerId, definition.id());
        }
    }
}
