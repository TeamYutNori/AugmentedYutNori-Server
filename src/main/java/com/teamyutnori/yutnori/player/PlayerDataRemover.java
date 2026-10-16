package com.teamyutnori.yutnori.player;

import com.google.firebase.auth.DeleteUsersResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.teamyutnori.yutnori.achievement.PlayerAchievementRepository;
import com.teamyutnori.yutnori.achievement.PlayerStatRepository;
import com.teamyutnori.yutnori.icon.PlayerIconRepository;
import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

// 플레이어 계정 데이터 완전 삭제 (회원 탈퇴, 오래된 게스트 정리에서 같이 쓴다)
//  - 지우는 것: 업적 집계(PlayerStat), 달성 업적, 보유 아이콘, Player(닉네임·이메일·Firebase UID·골드), Firebase 계정
//  - 남기는 것: 전적(MatchParticipant) - playerId만 남아 누구 기록인지 알 수 없는 통계가 된다
// 같은 구글 계정으로 다시 로그인하면 Firebase UID가 없으므로 새 playerId로 새 계정이 만들어진다
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerDataRemover {

    private static final int FIREBASE_DELETE_BATCH = 1000;   // Firebase deleteUsers 한 번에 최대 1000명

    private final PlayerRepository playerRepository;
    private final PlayerStatRepository playerStatRepository;
    private final PlayerAchievementRepository playerAchievementRepository;
    private final PlayerIconRepository playerIconRepository;
    private final FirebaseAuth firebaseAuth;

    // DB 데이터 삭제. 부르는 쪽 트랜잭션 안에서 실행해야 한다
    public void deletePlayerData(Player player) {
        String playerId = player.getPlayerId();
        playerStatRepository.deleteByPlayerId(playerId);
        playerAchievementRepository.deleteByPlayerId(playerId);
        playerIconRepository.deleteByPlayerId(playerId);
        playerRepository.delete(player);
    }

    // Firebase 계정 삭제. DB 트랜잭션이 끝난 뒤 부른다.
    // 실패해도 예외를 던지지 않는다: DB에 계정이 없으므로 같은 Firebase 계정으로 다시 로그인하면 새 계정이 된다
    public void deleteFirebaseUsers(List<String> uids) {
        List<String> targets = uids.stream()
                .filter(uid -> uid != null && !uid.isBlank())
                .toList();

        for (int from = 0; from < targets.size(); from += FIREBASE_DELETE_BATCH) {
            List<String> batch = targets.subList(from, Math.min(from + FIREBASE_DELETE_BATCH, targets.size()));
            try {
                DeleteUsersResult result = firebaseAuth.deleteUsers(batch);
                if (result.getFailureCount() > 0) {
                    log.warn("[PlayerDataRemover] Firebase 계정 {}명 삭제 실패", result.getFailureCount());
                }
            } catch (FirebaseAuthException | RuntimeException e) {
                log.warn("[PlayerDataRemover] Firebase 계정 삭제 실패: {}", e.getMessage());
            }
        }
    }
}
