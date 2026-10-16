package com.teamyutnori.yutnori.player.service;

import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.player.PlayerDataRemover;
import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import com.teamyutnori.yutnori.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;

// 회원 탈퇴 (구글·게스트 공통). 계정 데이터를 완전히 지우고 Firebase 계정도 삭제한다 (PlayerDataRemover).
// 같은 구글 계정으로 다시 로그인하면 새 계정이 만들어진다. 전적은 playerId만 남은 익명 통계로 유지
//  - 방·게임에 들어가 있으면 409 IN_ROOM (나간 뒤 탈퇴)
//  - 이미 발급된 로그인 토큰(JWT)은 남은 기간 동안 유효하지만, Player가 없으므로 이후 요청은 404·401로 막힌다
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountWithdrawalService {

    private final PlayerRepository playerRepository;
    private final RoomRepository roomRepository;
    private final PlayerDataRemover playerDataRemover;
    private final TransactionOperations transactions;

    public void withdraw(String playerId) {
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new NotFoundException(
                        "PLAYER_NOT_FOUND",
                        "플레이어를 찾을 수 없습니다."
                ));

        if (roomRepository.containsPlayer(playerId)) {
            throw new ConflictException(
                    "IN_ROOM",
                    "방이나 게임에 참가 중에는 탈퇴할 수 없습니다. 방에서 나간 뒤 다시 시도해 주세요."
            );
        }

        transactions.executeWithoutResult(status -> playerDataRemover.deletePlayerData(player));
        playerDataRemover.deleteFirebaseUsers(List.of(player.getFirebaseUid() == null ? "" : player.getFirebaseUid()));

        log.info("[Withdrawal] 탈퇴 완료: playerId={}, guest={}", playerId, player.isGuest());
    }
}
