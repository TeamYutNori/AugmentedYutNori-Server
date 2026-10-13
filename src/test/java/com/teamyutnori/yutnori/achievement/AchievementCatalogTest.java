package com.teamyutnori.yutnori.achievement;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AchievementCatalogTest {

    private static AchievementDefinition def(String id, AchievementStat stat, int goal, int reward) {
        return new AchievementDefinition(id, id, id, stat, goal, reward);
    }

    @Test
    void 실제_json을_읽는다() {
        AchievementCatalog catalog = new AchievementCatalog(JsonMapper.builder().build());

        assertThat(catalog.all()).hasSize(13);
        assertThat(catalog.byStat(AchievementStat.LOGIN_DAYS)).extracting(AchievementDefinition::goal).containsExactly(1, 5, 10);
        assertThat(catalog.byStat(AchievementStat.GAMES_PLAYED)).extracting(AchievementDefinition::goal).containsExactly(1, 5, 10);
        assertThat(catalog.byStat(AchievementStat.GACHA_DRAWS)).extracting(AchievementDefinition::goal).containsExactly(10, 20, 50, 100);
        assertThat(catalog.byStat(AchievementStat.FIRST_PLACES)).extracting(AchievementDefinition::goal).containsExactly(1, 3, 5);
        assertThat(catalog.find("games_5")).isPresent();
        assertThat(catalog.find("nope")).isEmpty();
    }

    @Test
    void 잘못된_정의는_서버_시작에서_막는다() {
        assertThatThrownBy(() -> new AchievementCatalog(new AchievementDefinitionFile(1, List.of())))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AchievementCatalog(new AchievementDefinitionFile(1, List.of(
                def("a", AchievementStat.GAMES_PLAYED, 1, 10), def("a", AchievementStat.GAMES_PLAYED, 2, 10)))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AchievementCatalog(new AchievementDefinitionFile(1, List.of(
                def("a", AchievementStat.GAMES_PLAYED, 0, 10)))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AchievementCatalog(new AchievementDefinitionFile(1, List.of(
                def("a", null, 1, 10)))))
                .isInstanceOf(IllegalStateException.class);
    }
}
