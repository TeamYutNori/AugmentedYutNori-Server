package com.teamyutnori.yutnori.game.augment;

import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.game.yut.SequenceRandomProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// 실제 src/main/resources/augments.json(6종)을 읽어서 후보 뽑기를 확인한다
class AugmentDraftServiceTest {

    private final AugmentCatalog catalog = new AugmentCatalog(JsonMapper.builder().build());

    private GameSession newSession() {
        return new GameSession("ROOM", new GameSetup(2, 2, "default", false), Map.of("p0", 0, "p1", 1));
    }

    @Test
    @DisplayName("augments.json을 읽어 6종을 json 순서대로 가진다")
    void catalogLoadsJson() {
        assertEquals(List.of("BackDoSwitch", "QuickStart", "Rethrow", "Shield", "SturdyCarry", "WeakBoost"),
                catalog.allIds());
        assertTrue(catalog.isConsumable("Rethrow"));
        assertFalse(catalog.isConsumable("WeakBoost"));
    }

    @Test
    @DisplayName("요청한 개수만큼 중복 없이 뽑는다")
    void drawsDistinct() {
        AugmentDraftService draft = new AugmentDraftService(catalog, new SequenceRandomProvider(0.3, 0.7, 0.1));
        for (int k = 0; k < 20; k++) {
            List<String> picked = draft.draft(newSession(), 0, 3);
            assertEquals(3, picked.size());
            assertEquals(3, new HashSet<>(picked).size(), "중복: " + picked);
        }
    }

    @Test
    @DisplayName("이미 가진 증강은 후보에서 빠진다")
    void excludesOwned() {
        GameSession session = newSession();
        session.addAugment(0, "Shield");
        session.addAugment(0, "Rethrow");
        AugmentDraftService draft = new AugmentDraftService(catalog, new SequenceRandomProvider(0.0));
        List<String> picked = draft.draft(session, 0, 6);
        assertEquals(4, picked.size());
        assertFalse(picked.contains("Shield"));
        assertFalse(picked.contains("Rethrow"));
    }

    @Test
    @DisplayName("0개 요청은 빈 목록")
    void zeroCount() {
        AugmentDraftService draft = new AugmentDraftService(catalog, new SequenceRandomProvider(0.5));
        assertTrue(draft.draft(newSession(), 0, 0).isEmpty());
    }
}
