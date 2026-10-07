package com.teamyutnori.yutnori.game.augment;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 증강 목록 저장소 (Unity AugmentCatalog의 서버판)
//
// 서버가 켜질 때 resources/augments.json을 한 번 읽어 두고, 다른 클래스의 질문에 답한다.
//  - AugmentDraftService : allIds()로 전체 목록을 받아 후보를 뽑는다
//  - RethrowValidator    : exists()로 "Rethrow"가 실제로 있는지 확인한다
//
// Unity와 달리 증강 효과를 만드는 생성 함수(factories)는 없다.
// 말 이동·증강 효과 계산은 클라이언트가 하고, 서버는 "어떤 증강이 있는지"만 알면 되기 때문이다.
//
// 주의: augments.json은 Unity Assets/Data/augments.json을 원본으로 복사해서 쓴다.
//       id를 바꾸거나 추가할 땐 Unity 쪽을 먼저 고치고 이 파일에 다시 복사할 것.
@Component
public class AugmentCatalog {

    // src/main/resources 기준 경로
    private static final String PATH = "augments.json";

    private final int version;

    // id → 정의. LinkedHashMap이라 json에 적힌 순서가 그대로 유지된다
    private final Map<String, AugmentDefinition> byId = new LinkedHashMap<>();

    // Spring이 서버를 시작할 때 이 생성자를 한 번 실행한다.
    // 여기서 예외가 나면 서버가 아예 켜지지 않는다 → 잘못된 json을 게임 도중이 아니라 시작 시점에 바로 알 수 있다.
    // ObjectMapper는 Spring Boot가 만들어 둔 Jackson 3 객체를 주입받는다 (MessageRouter가 쓰는 것과 같음)
    public AugmentCatalog(ObjectMapper objectMapper) {
        AugmentDefinitionFile file = load(objectMapper);

        // 최상위 키가 "augments"가 아니면 null이 된다 (오타 확인용)
        if (file.augments() == null) {
            throw new IllegalStateException("augments.json에 'augments' 배열이 없습니다. 최상위 키 이름을 확인하세요.");
        }

        for (AugmentDefinition definition : file.augments()) {
            if (definition == null || definition.id() == null || definition.id().isBlank()) {
                throw new IllegalStateException("augments.json에 id가 비어 있는 증강이 있습니다.");
            }
            // putIfAbsent: 이미 같은 id가 있으면 넣지 않고 기존 값을 돌려준다 → null이 아니면 중복
            if (byId.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalStateException("augments.json에 '" + definition.id() + "'가 두 번 있습니다.");
            }
        }

        this.version = file.version();
    }

    // classpath(= src/main/resources, 빌드 후엔 jar 안)에서 파일을 읽어 객체로 변환한다.
    // 파일 경로(File) 대신 ClassPathResource를 쓰는 이유: jar나 Docker로 실행해도 똑같이 찾을 수 있어서
    private static AugmentDefinitionFile load(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(PATH).getInputStream()) {
            return objectMapper.readValue(in, AugmentDefinitionFile.class);
        } catch (IOException e) {
            throw new IllegalStateException(PATH + " 파일을 읽을 수 없습니다. src/main/resources에 있는지 확인하세요.", e);
        }
    }

    // json의 version 값 (나중에 클라와 증강 목록 버전이 같은지 비교할 때 사용 가능)
    public int version() {
        return version;
    }

    // 전체 증강 id 목록. 바깥에서 수정하지 못하도록 복사본(수정 불가)을 돌려준다
    public List<String> allIds() {
        return List.copyOf(byId.keySet());
    }

    // 이 id의 증강이 존재하는지
    public boolean exists(String id) {
        return byId.containsKey(id);
    }

    // id로 정의 찾기. 없는 id면 예외 (호출하는 쪽에서 exists()로 먼저 확인하거나, 이미 검증된 id만 넣을 것)
    public AugmentDefinition find(String id) {
        AugmentDefinition definition = byId.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("없는 증강 id입니다: " + id);
        }
        return definition;
    }

    // 1회용 증강인지 (사용 후 보유 목록에서 지워야 하는지)
    public boolean isConsumable(String id) {
        return find(id).consumable();
    }
}
