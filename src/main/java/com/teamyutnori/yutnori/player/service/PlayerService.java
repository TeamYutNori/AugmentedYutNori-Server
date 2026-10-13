package com.teamyutnori.yutnori.player.service;

import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.player.dto.ChangeNicknameRequest;
import com.teamyutnori.yutnori.player.dto.CreatePlayerRequest;
import com.teamyutnori.yutnori.player.dto.CreatedPlayerResponse;
import com.teamyutnori.yutnori.player.dto.ChangeIconRequest;
import com.teamyutnori.yutnori.player.dto.PlayerProfileResponse;
import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;

    // Unity 에디터 테스트용 게스트 플레이어 생성
    @Transactional
    public CreatedPlayerResponse createPlayer(CreatePlayerRequest request) {

        String playerId = UUID.randomUUID().toString();
        String guestToken = UUID.randomUUID().toString();

        Player player = Player.createGuest(
                playerId,
                request.nickname(),
                guestToken
        );

        Player saved = playerRepository.save(player);

        return new CreatedPlayerResponse(
                saved.getPlayerId(),
                saved.getNickname(),
                saved.getGuestToken()
        );
    }

    // Firebase 로그인 사용자를 조회하고,
    // 처음 로그인한 사용자라면 새로운 Player 생성
    @Transactional
    public Player findOrCreateByFirebase(
            String firebaseUid,
            String email
    ) {

        return playerRepository.findByFirebaseUid(firebaseUid)
                .orElseGet(() -> {

                    // 처음 로그인한 사용자가 게임에서 사용할 playerId 생성
                    String playerId = UUID.randomUUID().toString();

                    Player player = Player.createFirebase(
                            playerId,
                            firebaseUid,
                            email
                    );

                    return playerRepository.save(player);
                });
    }

    // 플레이어의 계정 닉네임 변경
    @Transactional
    public void changeNickname(
            String playerId,
            ChangeNicknameRequest request
    ) {
        // playerId로 현재 로그인한 플레이어 조회
        Player player = getPlayerOrThrow(playerId);

        // 같은 닉네임을 그대로 다시 설정하는 경우는 허용
        if (request.nickname().equals(player.getNickname())) {
            return;
        }

        // 이미 다른 계정에서 사용 중인 닉네임인지 확인
        if (playerRepository.existsByNickname(request.nickname())) {
            throw new ConflictException(
                    "DUPLICATE_NICKNAME",
                    "이미 사용 중인 닉네임입니다."
            );
        }
        // Player 엔티티의 닉네임 변경
        player.changeNickname(request.nickname());
    }

    // 현재 로그인한 플레이어의 프로필 조회
    @Transactional(readOnly = true)
    public PlayerProfileResponse getProfile(String playerId) {
        Player player = getPlayerOrThrow(playerId);
        return PlayerProfileResponse.from(player);
    }

    // 현재 로그인한 플레이어의 프로필 아이콘 변경
    @Transactional
    public void changeIcon(
            String playerId,
            ChangeIconRequest request
    ) {
        Player player = getPlayerOrThrow(playerId);
        // TODO: 아이콘 보유 기능 연결 후 선택 가능한 아이콘인지 검증
        // 선택한 아이콘 저장
        player.selectIcon(request.iconId());
    }

    // ROOM_STATE 등에 사용할 현재 선택 아이콘 조회
    @Transactional(readOnly = true)
    public String getSelectedIconId(String playerId) {
        return getPlayerOrThrow(playerId)
                .getSelectedIconId();
    }

    // playerId 조회 공통 처리
    private Player getPlayerOrThrow(String playerId) {
        return playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new NotFoundException(
                        "PLAYER_NOT_FOUND",
                        "플레이어를 찾을 수 없습니다."
                ));
    }
}