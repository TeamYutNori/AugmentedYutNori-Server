package com.teamyutnori.yutnori.icon;

import com.teamyutnori.yutnori.game.yut.RandomProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 프로필 아이콘 목록 저장소 (Unity ProfileIconCatalog의 서버판)
//
// 서버가 켜질 때 resources/profile-icons.json을 한 번 읽어 두고, 뽑기(pick)와 id 확인(exists)에 답한다.
// 주의: id 목록은 Unity Assets/Data/profile_icons.json과 같아야 한다.
//       아이콘을 추가할 땐 Unity에서 PNG를 넣고 '아이콘 json 동기화'를 실행한 뒤, 이 파일에도 같은 id를 추가할 것.
//       확률(weight)은 서버에만 있다.
@Component
public class IconCatalog {

    private static final String PATH = "profile-icons.json";

    private final int version;
    private final Map<String, IconDefinition> byId = new LinkedHashMap<>();
    private final int totalWeight;

    // 생성자가 두 개라 Spring이 쓸 생성자를 @Autowired로 지정한다 (없으면 기본 생성자를 찾다가 실패)
    @Autowired
    public IconCatalog(ObjectMapper objectMapper) {
        this(load(objectMapper));
    }

    // 테스트에서 json 파일 없이 만들 때 사용
    IconCatalog(IconDefinitionFile file) {
        if (file == null || file.icons() == null || file.icons().isEmpty()) {
            throw new IllegalStateException(PATH + "에 'icons' 배열이 없거나 비어 있습니다. 최상위 키 이름을 확인하세요.");
        }

        int sum = 0;
        for (IconDefinition definition : file.icons()) {
            if (definition == null || definition.id() == null || definition.id().isBlank()) {
                throw new IllegalStateException(PATH + "에 id가 비어 있는 아이콘이 있습니다.");
            }
            if (definition.weightOrDefault() <= 0) {
                throw new IllegalStateException(PATH + "의 '" + definition.id() + "' weight는 1 이상이어야 합니다.");
            }
            if (byId.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalStateException(PATH + "에 '" + definition.id() + "'가 두 번 있습니다.");
            }
            sum += definition.weightOrDefault();
        }

        this.version = file.version();
        this.totalWeight = sum;
    }

    private static IconDefinitionFile load(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(PATH).getInputStream()) {
            return objectMapper.readValue(in, IconDefinitionFile.class);
        } catch (IOException e) {
            throw new IllegalStateException(PATH + " 파일을 읽을 수 없습니다. src/main/resources에 있는지 확인하세요.", e);
        }
    }

    public int version() {
        return version;
    }

    public List<String> allIds() {
        return List.copyOf(byId.keySet());
    }

    public boolean exists(String id) {
        return id != null && byId.containsKey(id);
    }

    // weight 비율대로 아이콘 하나를 고른다. random.nextDouble()은 0.0 이상 1.0 미만
    // 예) weight가 모두 1이고 아이콘이 6개면 각 1/6
    public IconDefinition pick(RandomProvider random) {
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;
        IconDefinition last = null;
        for (IconDefinition definition : byId.values()) {
            cumulative += definition.weightOrDefault();
            if (roll < cumulative) return definition;
            last = definition;
        }
        return last;   // 부동소수점 오차로 끝까지 온 경우
    }
}
