package com.teamyutnori.yutnori.icon;

import com.teamyutnori.yutnori.game.yut.SequenceRandomProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IconCatalogTest {

    private static IconCatalog catalog(IconDefinition... icons) {
        return new IconCatalog(new IconDefinitionFile(1, List.of(icons)));
    }

    @Test
    void 실제_json을_읽는다() {
        IconCatalog catalog = new IconCatalog(JsonMapper.builder().build());

        assertThat(catalog.allIds()).containsExactly(
                "icon_001", "icon_002", "icon_003", "icon_004", "icon_005", "icon_006");
        assertThat(catalog.exists("icon_001")).isTrue();
        assertThat(catalog.exists("nope")).isFalse();
    }

    @Test
    void 같은_확률이면_구간을_똑같이_나눈다() {
        IconCatalog catalog = catalog(
                new IconDefinition("a", "A", null),
                new IconDefinition("b", "B", null),
                new IconDefinition("c", "C", null));

        assertThat(catalog.pick(new SequenceRandomProvider(0.0)).id()).isEqualTo("a");
        assertThat(catalog.pick(new SequenceRandomProvider(0.33)).id()).isEqualTo("a");
        assertThat(catalog.pick(new SequenceRandomProvider(0.34)).id()).isEqualTo("b");
        assertThat(catalog.pick(new SequenceRandomProvider(0.67)).id()).isEqualTo("c");
        assertThat(catalog.pick(new SequenceRandomProvider(0.9999)).id()).isEqualTo("c");
    }

    @Test
    void weight_비율대로_고른다() {
        IconCatalog catalog = catalog(
                new IconDefinition("common", "일반", 3),
                new IconDefinition("rare", "희귀", 1));

        assertThat(catalog.pick(new SequenceRandomProvider(0.74)).id()).isEqualTo("common");
        assertThat(catalog.pick(new SequenceRandomProvider(0.75)).id()).isEqualTo("rare");
    }

    @Test
    void 잘못된_json이면_서버_시작을_막는다() {
        assertThatThrownBy(() -> new IconCatalog(new IconDefinitionFile(1, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> catalog(new IconDefinition(" ", "빈 id", 1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> catalog(new IconDefinition("a", "A", 1), new IconDefinition("a", "A2", 1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> catalog(new IconDefinition("a", "A", 0)))
                .isInstanceOf(IllegalStateException.class);
    }
}
