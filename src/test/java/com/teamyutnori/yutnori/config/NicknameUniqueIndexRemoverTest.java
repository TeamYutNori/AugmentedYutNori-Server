package com.teamyutnori.yutnori.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class NicknameUniqueIndexRemoverTest {

    @Test
    void 닉네임_유니크_인덱스가_있으면_지운다() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(NicknameUniqueIndexRemover.FIND_INDEX_SQL, String.class))
                .thenReturn(List.of("UK6fw03el6uda0dcep04s4rwpcx"));

        new NicknameUniqueIndexRemover(jdbc).run(null);

        verify(jdbc).execute("ALTER TABLE players DROP INDEX `UK6fw03el6uda0dcep04s4rwpcx`");
    }

    @Test
    void 인덱스가_없으면_아무것도_하지_않는다() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(NicknameUniqueIndexRemover.FIND_INDEX_SQL, String.class)).thenReturn(List.of());

        new NicknameUniqueIndexRemover(jdbc).run(null);

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void 실패해도_서버_시작을_막지_않는다() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(NicknameUniqueIndexRemover.FIND_INDEX_SQL, String.class))
                .thenThrow(new RuntimeException("no permission"));

        assertThatCode(() -> new NicknameUniqueIndexRemover(jdbc).run(null)).doesNotThrowAnyException();
    }
}
