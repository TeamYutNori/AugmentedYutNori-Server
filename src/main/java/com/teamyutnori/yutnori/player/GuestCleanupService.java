package com.teamyutnori.yutnori.player;

import com.google.firebase.auth.DeleteUsersResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.teamyutnori.yutnori.achievement.PlayerAchievementRepository;
import com.teamyutnori.yutnori.achievement.PlayerStatRepository;
import com.teamyutnori.yutnori.icon.PlayerIconRepository;
import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import com.teamyutnori.yutnori.room.repository.RoomRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

// 오래 접속하지 않은 게스트(Firebase 익명 로그인) 계정 정리. 하루 한 번 실행
//  - 대상: Player.guest = true 이고 lastLoginAt이 inactive-days일보다 오래된 계정
//  - 지금 방·게임에 들어가 있는 플레이어는 건너뛴다
//  - 지우는 것: 업적 집계(PlayerStat), 달성 업적, 보유 아이콘, Player, Firebase 익명 계정
//  - 남기는 것: 전적(MatchParticipant) - 게임 기록은 통계로 남긴다
// 설정: guest.cleanup.inactive-days (기본 30), guest.cleanup.cron (기본 매일 04:00, 서울)
@Slf4j
@Service
public class GuestCleanupService {

    private static final int FIREBASE_DELETE_BATCH = 1000;   // Firebase deleteUsers 한 번에 최대 1000명

    private final PlayerRepository playerRepository;
    private final PlayerStatRepository playerStatRepository;
    private final PlayerAchievementRepository playerAchievementRepository;
    private final PlayerIconRepository playerIconRepository;
    private final RoomRepository roomRepository;
    private final FirebaseAuth firebaseAuth;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final Duration inactivePeriod;

    public GuestCleanupService(
            PlayerRepository playerRepository,
            PlayerStatRepository playerStatRepository,
            PlayerAchievementRepository playerAchievementRepository,
            PlayerIconRepository playerIconRepository,
            RoomRepository roomRepository,
            FirebaseAuth firebaseAuth,
            TransactionOperations transactions,
            Clock clock,
            @Value("${guest.cleanup.inactive-days:30}") int inactiveDays
    ) {
        this.playerRepository = playerRepository;
        this.playerStatRepository = playerStatRepository;
        this.playerAchievementRepository = playerAchievementRepository;
        this.playerIconRepository = playerIconRepository;
        this.roomRepository = roomRepository;
        this.firebaseAuth = firebaseAuth;
        this.transactions = transactions;
        this.clock = clock;
        this.inactivePeriod = Duration.ofDays(inactiveDays);
    }

    @Scheduled(cron = "${guest.cleanup.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void scheduledCleanup() {
        try {
            cleanup();
        } catch (RuntimeException e) {
            log.error("[GuestCleanup] 게스트 정리 실패", e);
        }
    }

    // 지운 게스트 수를 돌려준다
    public int cleanup() {
        Instant cutoff = clock.instant().minus(inactivePeriod);

        List<Player> targets = playerRepository.findByGuestTrueAndLastLoginAtBefore(cutoff).stream()
                .filter(player -> !roomRepository.containsPlayer(player.getPlayerId()))
                .toList();
        if (targets.isEmpty()) return 0;

        // DB 삭제는 한 트랜잭션: 중간에 실패하면 아무것도 지워지지 않는다
        transactions.executeWithoutResult(status -> targets.forEach(this::deletePlayerData));

        // Firebase 익명 계정 삭제는 DB가 지워진 뒤. 실패해도 DB에는 영향 없음 (남은 익명 계정은 로그인해도 새 게스트가 됨)
        deleteFirebaseUsers(targets.stream()
                .map(Player::getFirebaseUid)
                .filter(uid -> uid != null && !uid.isBlank())
                .toList());

        log.info("[GuestCleanup] 게스트 {}명 정리 (마지막 접속 {} 이전)", targets.size(), cutoff);
        return targets.size();
    }

    private void deletePlayerData(Player player) {
        String playerId = player.getPlayerId();
        playerStatRepository.deleteByPlayerId(playerId);
        playerAchievementRepository.deleteByPlayerId(playerId);
        playerIconRepository.deleteByPlayerId(playerId);
        playerRepository.delete(player);
    }

    private void deleteFirebaseUsers(List<String> uids) {
        for (int from = 0; from < uids.size(); from += FIREBASE_DELETE_BATCH) {
            List<String> batch = uids.subList(from, Math.min(from + FIREBASE_DELETE_BATCH, uids.size()));
            try {
                DeleteUsersResult result = firebaseAuth.deleteUsers(batch);
                if (result.getFailureCount() > 0) {
                    log.warn("[GuestCleanup] Firebase 익명 계정 {}명 삭제 실패", result.getFailureCount());
                }
            } catch (FirebaseAuthException | RuntimeException e) {
                log.warn("[GuestCleanup] Firebase 익명 계정 삭제 실패: {}", e.getMessage());
            }
        }
    }
}
