package com.teamyutnori.yutnori.player.service;

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

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final Clock clock;

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
    // anonymous: Firebase 익명 로그인("게스트로 시작")이면 true → 새 Player를 게스트로 만든다
    // 로그인할 때마다 마지막 접속 시각을 기록한다 (오래 안 들어온 게스트 정리 기준)
    public Player findOrCreateByFirebase(
            String firebaseUid,
            String email,
            boolean anonymous
    ) {

        Player player = playerRepository.findByFirebaseUid(firebaseUid)
                .orElseGet(() -> {

                    // 처음 로그인한 사용자가 게임에서 사용할 playerId 생성
                    String playerId = UUID.randomUUID().toString();

                    Player created = anonymous
                            ? Player.createFirebaseGuest(playerId, firebaseUid)
                            : Player.createFirebase(playerId, firebaseUid, email);

                    return playerRepository.save(created);
                });

        // 같은 Firebase UID에 구글 계정이 연결돼 익명이 아닌 로그인으로 들어오면 일반 계정으로 바꾼다
        if (player.isGuest() && !anonymous) {
            player.upgradeToGoogle(email);
        }

        player.recordLogin(clock.instant());
        return player;
    }

    // 플레이어의 계정 닉네임 변경
    @Transactional
    public void changeNickname(
            String playerId,
            ChangeNicknameRequest request
    ) {
        // playerId로 현재 로그인한 플레이어 조회
        Player player = getPlayerOrThrow(playerId);

        // 같은 닉네임을 그대로 다시 설정하는 경우는 그냥 둔다
        // 다른 플레이어와 닉네임이 같아도 된다 (플레이어 구분은 playerId)
        if (request.nickname().equals(player.getNickname())) {
            return;
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