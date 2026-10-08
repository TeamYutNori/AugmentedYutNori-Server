package com.teamyutnori.yutnori.player.service;

import com.teamyutnori.yutnori.player.dto.CreatePlayerRequest;
import com.teamyutnori.yutnori.player.dto.CreatedPlayerResponse;
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
}