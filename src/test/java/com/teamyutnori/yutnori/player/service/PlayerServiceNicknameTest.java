package com.teamyutnori.yutnori.player.service;

import com.teamyutnori.yutnori.player.dto.ChangeNicknameRequest;
import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PlayerServiceNicknameTest {

    @Test
    void 다른_플레이어와_같은_닉네임으로_바꿀_수_있다() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        Player first = Player.createFirebase("p1", "uid-1", null);
        Player second = Player.createFirebase("p2", "uid-2", null);
        first.changeNickname("윷돌이");
        when(playerRepository.findByPlayerId("p2")).thenReturn(Optional.of(second));
        PlayerService service = new PlayerService(playerRepository, Clock.systemUTC());

        service.changeNickname("p2", new ChangeNicknameRequest("윷돌이"));

        assertThat(second.getNickname()).isEqualTo("윷돌이");
        assertThat(first.getNickname()).isEqualTo("윷돌이");
    }
}
