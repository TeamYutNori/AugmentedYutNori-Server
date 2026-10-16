package com.teamyutnori.yutnori.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

// 닉네임 중복 허용으로 바꾸면서, 예전에 만들어진 players.nickname 유니크 인덱스를 서버 시작 때 지운다.
// ddl-auto=update는 엔티티에서 unique를 빼도 이미 있는 인덱스를 지우지 않기 때문 (로컬·배포 DB 모두 자동 처리).
// 인덱스가 없으면 아무것도 하지 않는다. 모든 DB에서 지워진 뒤에는 이 클래스를 삭제해도 된다
@Slf4j
@Component
@RequiredArgsConstructor
public class NicknameUniqueIndexRemover implements ApplicationRunner {

    // players.nickname 한 칸에만 걸린 유니크 인덱스 이름 (MySQL)
    static final String FIND_INDEX_SQL = """
            SELECT index_name
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'players'
              AND non_unique = 0
            GROUP BY index_name
            HAVING COUNT(*) = 1 AND MAX(column_name) = 'nickname'
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<String> indexes = jdbcTemplate.queryForList(FIND_INDEX_SQL, String.class);
            for (String index : indexes) {
                jdbcTemplate.execute("ALTER TABLE players DROP INDEX `" + index.replace("`", "") + "`");
                log.info("[NicknameUniqueIndexRemover] 닉네임 유니크 인덱스 삭제: {}", index);
            }
        } catch (RuntimeException e) {
            // 서버 시작은 막지 않는다. 남아 있으면 같은 닉네임 저장이 DB에서 거절되므로 로그로 알린다
            log.warn("[NicknameUniqueIndexRemover] 닉네임 유니크 인덱스를 지우지 못했습니다: {}", e.getMessage());
        }
    }
}
