package com.teamyutnori.yutnori.gacha;

import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.InvalidRequestException;
import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.gacha.dto.GachaDrawResult;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GachaServiceTest {

    private PlayerRepository playerRepository;
    private PlayerIconRepository playerIconRepository;
    private final IconCatalog iconCatalog = new IconCatalog(JsonMapper.builder().build());   // icon_001 ~ icon_022
    private Player player;
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    @BeforeEach
    void setUp() {
        playerRepository = mock(PlayerRepository.class);
        playerIconRepository = mock(PlayerIconRepository.class);
        player = Player.createFirebase("p1", "uid-1", "a@example.com");
        when(playerRepository.findByPlayerId("p1")).thenReturn(Optional.of(player));
    }

    // saveAll에 넘긴 아이콘 id 목록이 정확히 이것인지
    private static Iterable<PlayerIcon> savedIconIds(String... iconIds) {
        return argThat(icons -> {
            List<String> ids = new ArrayList<>();
            icons.forEach(icon -> ids.add(icon.getIconId()));
            return ids.equals(List.of(iconIds));
        });
    }

    // 0.0이면 첫 번째(icon_001), 0.99면 마지막(icon_022)
    private GachaService service(double... rolls) {
        return new GachaService(playerRepository, playerIconRepository, iconCatalog, new SequenceRandomProvider(rolls),
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
        verify(playerIconRepository).saveAll(savedIconIds("icon_001"));
    }

    @Test
    void 뽑으면_업적용_뽑기_이벤트를_발행한다() {
        player.addGold(500);

        service(0.0).draw("p1");

        verify(eventPublisher).publishEvent(new GachaDrawnEvent("p1", 1));
    }

    @Test
    void 가진_아이콘이_또_나오면_count만_올리고_보상은_없다() {
        player.addGold(200);
        PlayerIcon owned = PlayerIcon.first("p1", "icon_022");
        when(playerIconRepository.findAllByPlayerIdAndIconIdIn(eq("p1"), any())).thenReturn(List.of(owned));

        GachaResultResponse result = service(0.99).draw("p1");

        assertThat(result.iconId()).isEqualTo("icon_022");
        assertThat(result.duplicate()).isTrue();
        assertThat(result.count()).isEqualTo(2);
        assertThat(result.gold()).isZero();
        verify(playerIconRepository).saveAll(savedIconIds());   // 새로 저장할 것 없음
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

    @Test
    void 십연차는_골드를_한_번에_빼고_10개를_순서대로_준다() {
        player.addGold(2500);

        GachaResultResponse result = service(0.0, 0.99).draw("p1", 10);

        assertThat(result.results()).hasSize(10);
        assertThat(result.results()).extracting(GachaDrawResult::iconId)
                .containsExactly("icon_001", "icon_022", "icon_001", "icon_022", "icon_001",
                        "icon_022", "icon_001", "icon_022", "icon_001", "icon_022");
        assertThat(result.price()).isEqualTo(GachaService.PRICE * 10);
        assertThat(result.gold()).isEqualTo(500);
        assertThat(player.getGold()).isEqualTo(500);
    }

    @Test
    void 같은_십연차_안에서_두_번_나오면_두_번째부터_중복이고_저장은_한_번() {
        player.addGold(2000);

        GachaResultResponse result = service(0.0).draw("p1", 10);   // 10번 모두 icon_001

        assertThat(result.results().get(0)).isEqualTo(new GachaDrawResult("icon_001", false, 1));
        assertThat(result.results().get(1)).isEqualTo(new GachaDrawResult("icon_001", true, 2));
        assertThat(result.results().get(9)).isEqualTo(new GachaDrawResult("icon_001", true, 10));
        verify(playerIconRepository, times(1)).saveAll(savedIconIds("icon_001"));
        verify(playerIconRepository, times(1)).findAllByPlayerIdAndIconIdIn(eq("p1"), any());   // 조회는 한 번
    }

    @Test
    void 십연차는_업적용_이벤트를_한_번만_횟수_10으로_발행한다() {
        player.addGold(2000);

        service(0.0).draw("p1", 10);

        verify(eventPublisher).publishEvent(new GachaDrawnEvent("p1", 10));
        verifyNoMoreInteractions(eventPublisher);
    }

    @Test
    void 십연차_골드가_부족하면_409이고_하나도_안_뽑는다() {
        player.addGold(1999);

        assertThatThrownBy(() -> service(0.0).draw("p1", 10))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "NOT_ENOUGH_GOLD");
        assertThat(player.getGold()).isEqualTo(1999);
        verifyNoInteractions(playerIconRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 뽑기_횟수는_1과_10만_허용한다() {
        player.addGold(10000);

        for (int times : new int[]{0, 2, 5, 11, -1}) {
            assertThatThrownBy(() -> service(0.0).draw("p1", times))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("code", "INVALID_DRAW_COUNT");
        }
        assertThat(player.getGold()).isEqualTo(10000);
    }

    @Test
    void 일회_뽑기_응답의_첫_결과는_예전_필드와_같다() {
        player.addGold(200);

        GachaResultResponse result = service(0.0).draw("p1", 1);

        assertThat(result.results()).containsExactly(new GachaDrawResult("icon_001", false, 1));
        assertThat(result.iconId()).isEqualTo("icon_001");
        assertThat(result.price()).isEqualTo(GachaService.PRICE);
    }
}
