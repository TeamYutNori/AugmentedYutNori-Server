package com.teamyutnori.yutnori.game.board;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// 판 정의 저장소 (구조는 AugmentCatalog와 같음: json 읽기 → 검증 → 보관)
//
// 서버가 켜질 때 resources/boards/*.json을 전부 읽어 둔다. 파일 이름이 판 id
//   default.json → "default", long-diagonal.json → "long-diagonal"
// GameService.startGame이 exists()로 판이 있는지 확인하고, create()로 게임마다 새 판을 만든다
//
// 주의: json은 Unity Assets/Data/BoardLayouts/*.asset의 definition을 옮긴 것이다.
//       Unity에서 판을 고치면 이 json도 다시 내보내야 서버와 판이 같아진다 (칸 번호가 같아야 함)
@Component
public class BoardLayoutRepository {

    private static final String PATTERN = "classpath:boards/*.json";
    public static final String DEFAULT_ID = "default";

    private final Map<String, BoardDefinition> definitions = new LinkedHashMap<>();

    // 여기서 예외가 나면 서버가 켜지지 않는다 → 잘못된 판 json을 게임 도중이 아니라 시작할 때 바로 알 수 있다
    public BoardLayoutRepository(ObjectMapper objectMapper) {
        Resource[] files;
        try {
            files = new PathMatchingResourcePatternResolver().getResources(PATTERN);
        } catch (IOException e) {
            throw new IllegalStateException("판 json 목록을 읽을 수 없습니다: " + PATTERN, e);
        }

        for (Resource file : files) {
            String fileName = file.getFilename();
            if (fileName == null) continue;
            String id = fileName.substring(0, fileName.length() - ".json".length());

            BoardDefinition definition;
            try (InputStream in = file.getInputStream()) {
                definition = objectMapper.readValue(in, BoardDefinition.class);
            } catch (IOException | JacksonException e) {
                throw new IllegalStateException("판 json을 읽을 수 없습니다: " + fileName, e);
            }

            try {
                definition.validate();
            } catch (IllegalStateException e) {
                throw new IllegalStateException("판 json이 잘못됐습니다 (" + fileName + "): " + e.getMessage(), e);
            }
            definitions.put(id, definition);
        }

        if (!definitions.containsKey(DEFAULT_ID)) {
            throw new IllegalStateException("기본 판(boards/" + DEFAULT_ID + ".json)이 없습니다.");
        }
    }

    public boolean exists(String id)  { return id != null && definitions.containsKey(id); }
    public Set<String> ids()          { return Set.copyOf(definitions.keySet()); }

    // 게임마다 새로 만든다. 같은 판 객체를 여러 게임이 같이 쓰면 안 됨
    // 없는 id면 예외 (호출하는 쪽에서 exists()로 먼저 확인할 것)
    public BoardGraph create(String id, boolean allowSkipShortcut) {
        BoardDefinition definition = definitions.get(id);
        if (definition == null) throw new IllegalArgumentException("없는 판 id입니다: " + id);

        BoardGraph graph = BoardGraph.from(definition);
        graph.setAllowSkipShortcut(allowSkipShortcut);
        return graph;
    }
}
