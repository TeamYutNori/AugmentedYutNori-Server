package com.teamyutnori.yutnori.achievement;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 업적 목록 저장소. 서버가 켜질 때 resources/achievements.json을 한 번 읽어 둔다
// 업적 추가·보상 조정은 json만 고치면 된다 (클라는 목록 API로 이름·설명까지 받아 그린다)
@Component
public class AchievementCatalog {

    private static final String PATH = "achievements.json";

    private final Map<String, AchievementDefinition> byId = new LinkedHashMap<>();

    @Autowired
    public AchievementCatalog(ObjectMapper objectMapper) {
        this(load(objectMapper));
    }

    // 테스트에서 json 파일 없이 만들 때 사용
    AchievementCatalog(AchievementDefinitionFile file) {
        if (file == null || file.achievements() == null || file.achievements().isEmpty()) {
            throw new IllegalStateException(PATH + "에 'achievements' 배열이 없거나 비어 있습니다.");
        }
        for (AchievementDefinition definition : file.achievements()) {
            if (definition == null || definition.id() == null || definition.id().isBlank()) {
                throw new IllegalStateException(PATH + "에 id가 비어 있는 업적이 있습니다.");
            }
            if (definition.stat() == null) {
                throw new IllegalStateException(PATH + "의 '" + definition.id() + "' stat이 비어 있거나 잘못됐습니다.");
            }
            if (definition.goal() <= 0) {
                throw new IllegalStateException(PATH + "의 '" + definition.id() + "' goal은 1 이상이어야 합니다.");
            }
            if (definition.rewardGold() < 0) {
                throw new IllegalStateException(PATH + "의 '" + definition.id() + "' rewardGold는 0 이상이어야 합니다.");
            }
            if (byId.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalStateException(PATH + "에 '" + definition.id() + "'가 두 번 있습니다.");
            }
        }
    }

    private static AchievementDefinitionFile load(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(PATH).getInputStream()) {
            return objectMapper.readValue(in, AchievementDefinitionFile.class);
        } catch (IOException e) {
            throw new IllegalStateException(PATH + " 파일을 읽을 수 없습니다. src/main/resources에 있는지 확인하세요.", e);
        }
    }

    // json 순서 그대로
    public List<AchievementDefinition> all() {
        return List.copyOf(byId.values());
    }

    public Optional<AchievementDefinition> find(String id) {
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    public List<AchievementDefinition> byStat(AchievementStat stat) {
        List<AchievementDefinition> result = new ArrayList<>();
        for (AchievementDefinition definition : byId.values()) {
            if (definition.stat() == stat) result.add(definition);
        }
        return result;
    }
}
