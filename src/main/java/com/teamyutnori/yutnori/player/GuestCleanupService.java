package com.teamyutnori.yutnori.player;

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

// 오래 접속하지 않은 계정 정리 (구글·게스트 모두). 하루 한 번 실행
//  - 대상: lastLoginAt이 inactive-days일보다 오래된 계정 (기본 30일)
//    lastLoginAt이 비어 있는 예전 계정은 다음 로그인 때 시각이 기록된 뒤부터 대상이 된다
//  - 지금 방·게임에 들어가 있는 플레이어는 건너뛴다
//  - 삭제 범위는 회원 탈퇴와 같다 (PlayerDataRemover): 계정 데이터와 Firebase 계정 삭제, 전적은 유지
// 설정: guest.cleanup.inactive-days (기본 30), guest.cleanup.cron (기본 매일 04:00, 서울)
@Slf4j
@Service
public class GuestCleanupService {

    private final PlayerRepository playerRepository;
    private final RoomRepository roomRepository;
    private final PlayerDataRemover playerDataRemover;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final Duration inactivePeriod;

    public GuestCleanupService(
            PlayerRepository playerRepository,
            RoomRepository roomRepository,
            PlayerDataRemover playerDataRemover,
            TransactionOperations transactions,
            Clock clock,
            @Value("${guest.cleanup.inactive-days:30}") int inactiveDays
    ) {
        this.playerRepository = playerRepository;
        this.roomRepository = roomRepository;
        this.playerDataRemover = playerDataRemover;
        this.transactions = transactions;
        this.clock = clock;
        this.inactivePeriod = Duration.ofDays(inactiveDays);
    }

    @Scheduled(cron = "${guest.cleanup.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void scheduledCleanup() {
        try {
            cleanup();
        } catch (RuntimeException e) {
            log.error("[GuestCleanup] 오래된 계정 정리 실패", e);
        }
    }

    // 지운 계정 수를 돌려준다
    public int cleanup() {
        Instant cutoff = clock.instant().minus(inactivePeriod);

        List<Player> targets = playerRepository.findByLastLoginAtBefore(cutoff).stream()
                .filter(player -> !roomRepository.containsPlayer(player.getPlayerId()))
                .toList();
        if (targets.isEmpty()) return 0;

        // DB 삭제는 한 트랜잭션: 중간에 실패하면 아무것도 지워지지 않는다
        transactions.executeWithoutResult(status -> targets.forEach(playerDataRemover::deletePlayerData));

        // Firebase 계정 삭제는 DB가 지워진 뒤 (실패해도 DB에는 영향 없음)
        playerDataRemover.deleteFirebaseUsers(targets.stream().map(Player::getFirebaseUid).toList());

        log.info("[GuestCleanup] 오래된 계정 {}개 정리 (마지막 접속 {} 이전)", targets.size(), cutoff);
        return targets.size();
    }
}
