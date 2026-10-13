package com.teamyutnori.yutnori.gacha;

import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.gacha.dto.GachaResultResponse;
import com.teamyutnori.yutnori.game.yut.SequenceRandomProvider;
import com.teamyutnori.yutnori.icon.IconCatalog;
import com.teamyutnori.yutnori.icon.PlayerIcon;
import com.teamyutnori.yutnori.icon.PlayerIconRepository;
import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GachaServiceTest {

    private PlayerRepository playerRepository;
    private PlayerIconRepository playerIconRepository;
    private final IconCatalog iconCatalog = new IconCatalog(JsonMapper.builder().build());   // icon_001 ~ icon_006
    private Player player;
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    @BeforeEach
    void setUp() {
        playerRepository = mock(PlayerRepository.class);
        playerIconRepository = mock(PlayerIconRepository.class);
        player = Player.createFirebase("p1", "uid-1", "a@example.com");
        when(playerRepository.findByPlayerId("p1")).thenReturn(Optional.of(player));
        when(playerIconRepository.save(any(PlayerIcon.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // 0.0이면 첫 번째(icon_001), 0.99면 마지막(icon_006)
    private GachaService service(double roll) {
        return new GachaService(playerRepository, playerIconRepository, iconCatalog, new SequenceRandomProvider(roll),
                eventPublisher);
    }

    @Test
    void 처음_뽑으면_골드를_빼고_아이콘을_저장한다() {
        player.addGold(500);

        GachaResultResponse result = service(0.0).draw("p1");

        assertThat(result.iconId()).isEqualTo("icon_001");
        assertThat(result.duplicate()).isFalse();
        assertThat(result.count()).isEqualTo(1);
        assertThat(result.gold()).isEqualTo(300);
        assertThat(result.price()).isEqualTo(GachaService.PRICE);
        assertThat(player.getGold()).isEqualTo(300);
        verify(playerIconRepository).save(any(PlayerIcon.class));
    }

    @Test
    void 뽑으면_업적용_뽑기_이벤트를_발행한다() {
        player.addGold(500);

        service(0.0).draw("p1");

        verify(eventPublisher).publishEvent(new GachaDrawnEvent("p1", "icon_001"));
    }

    @Test
    void 가진_아이콘이_또_나오면_count만_올리고_보상은_없다() {
        player.addGold(200);
        PlayerIcon owned = PlayerIcon.first("p1", "icon_006");
        when(playerIconRepository.findByPlayerIdAndIconId("p1", "icon_006")).thenReturn(Optional.of(owned));

        GachaResultResponse result = service(0.99).draw("p1");

        assertThat(result.iconId()).isEqualTo("icon_006");
        assertThat(result.duplicate()).isTrue();
        assertThat(result.count()).isEqualTo(2);
        assertThat(result.gold()).isZero();
        verify(playerIconRepository, never()).save(any());
    }

    @Test
    void 골드가_부족하면_409이고_아이콘도_안_준다() {
        player.addGold(199);

        assertThatThrownBy(() -> service(0.0).draw("p1"))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "NOT_ENOUGH_GOLD");
        assertThat(player.getGold()).isEqualTo(199);
        verifyNoInteractions(playerIconRepository);
    }

    @Test
    void 없는_플레이어는_404() {
        assertThatThrownBy(() -> service(0.0).draw("ghost"))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "PLAYER_NOT_FOUND");
    }
}
