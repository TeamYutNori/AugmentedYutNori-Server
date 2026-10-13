package com.teamyutnori.yutnori.icon.service;

import com.teamyutnori.yutnori.icon.PlayerIcon;
import com.teamyutnori.yutnori.icon.PlayerIconRepository;
import com.teamyutnori.yutnori.icon.dto.OwnedIconsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 도감 보유 아이콘 조회: 가진 아이콘 id만, 없으면 빈 목록, JSON 이름은 iconIds
class IconServiceTest {

    private PlayerIconRepository repository;
    private IconService service;

    @BeforeEach
    void setUp() {
        repository = mock(PlayerIconRepository.class);
        service = new IconService(repository);
    }

    @Test
    void 가진_아이콘_id를_돌려준다() {
        when(repository.findAllByPlayerId("p1")).thenReturn(List.of(
                PlayerIcon.first("p1", "icon_001"),
                PlayerIcon.first("p1", "icon_003")));

        OwnedIconsResponse response = service.ownedIcons("p1");

        assertThat(response.iconIds()).containsExactlyInAnyOrder("icon_001", "icon_003");
    }

    @Test
    void 같은_아이콘을_여러_번_뽑아도_id는_한_번만_나온다() {
        PlayerIcon icon = PlayerIcon.first("p1", "icon_002");
        icon.addDuplicate();
        when(repository.findAllByPlayerId("p1")).thenReturn(List.of(icon));

        assertThat(service.ownedIcons("p1").iconIds()).containsExactly("icon_002");
    }

    @Test
    void 가진_아이콘이_없으면_빈_목록이다() {
        when(repository.findAllByPlayerId("p1")).thenReturn(List.of());

        OwnedIconsResponse response = service.ownedIcons("p1");

        assertThat(response.iconIds()).isNotNull().isEmpty();
    }

    @Test
    void JSON은_클라와_같은_iconIds_이름으로_나간다() {
        ObjectMapper objectMapper = JsonMapper.builder().build();

        JsonNode json = objectMapper.valueToTree(new OwnedIconsResponse(List.of("icon_001")));

        assertThat(json.get("iconIds").get(0).asText()).isEqualTo("icon_001");
    }
}