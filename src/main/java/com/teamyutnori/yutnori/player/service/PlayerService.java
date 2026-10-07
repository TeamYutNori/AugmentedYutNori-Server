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

    // 게스트 플레이어 생성
    @Transactional
    public CreatedPlayerResponse createPlayer(CreatePlayerRequest request) {

        // 외부 식별용 playerId와 인증용 guestToken을 각각 생성
        String playerId = UUID.randomUUID().toString();
        String guestToken = UUID.randomUUID().toString();

        Player player = new Player(
                playerId,
                request.nickname(),
                guestToken
        );

        Player savedPlayer = playerRepository.save(player);

        return new CreatedPlayerResponse(
                savedPlayer.getPlayerId(),
                savedPlayer.getNickname(),
                savedPlayer.getGuestToken()
        );
    }
}