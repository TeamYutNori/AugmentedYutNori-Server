package com.teamyutnori.yutnori.reconnect;

import com.teamyutnori.yutnori.config.GameProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TurnTimerServiceTest {

    private static final String ROOM = "ABCD";
    private static final Duration SHORT = Duration.ofMillis(100);

    private ThreadPoolTaskScheduler scheduler;
    private List<TurnTimeoutEvent> events;
    private TurnTimerService service;

    @BeforeEach
    void setUp() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        events = new CopyOnWriteArrayList<>();

        // 기본 턴 제한시간만 100ms로 짧게, 나머지는 실제 값과 비슷하게
        GameProperties properties = new GameProperties(
                2, 4, SHORT, 3, Duration.ofSeconds(20), Duration.ofSeconds(30));

        service = new TurnTimerService(properties, scheduler, Clock.systemUTC(),
                event -> events.add((TurnTimeoutEvent) event));
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    // 제한시간이 지나면 이벤트가 한 번 발행된다 (방, 팀 정보 포함)
    @Test
    void timeout() throws InterruptedException {
        service.start(ROOM, 1);

        waitFor(1, Duration.ofSeconds(1));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).roomCode()).isEqualTo(ROOM);
        assertThat(events.get(0).team()).isEqualTo(1);
        assertThat(service.remaining(ROOM)).isEmpty();
    }

    // 시간 전에 cancel하면 이벤트가 발행되지 않는다
    @Test
    void cancel() throws InterruptedException {
        service.start(ROOM, 0);
        service.cancel(ROOM);

        Thread.sleep(300);

        assertThat(events).isEmpty();
    }

    // 같은 방에서 다시 start하면 이전 타이머는 취소되고 새 타이머만 만료된다
    @Test
    void restart() throws InterruptedException {
        service.start(ROOM, 0, SHORT);
        service.start(ROOM, 1, Duration.ofMillis(200));

        Thread.sleep(400);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).team()).isEqualTo(1);
    }

    // 방마다 타이머가 따로 돈다
    @Test
    void separateRoom() throws InterruptedException {
        service.start("ROOM1", 0);
        service.start("ROOM2", 1);

        waitFor(2, Duration.ofSeconds(1));

        assertThat(events).extracting(TurnTimeoutEvent::roomCode)
                .containsExactlyInAnyOrder("ROOM1", "ROOM2");
    }

    // 남은 시간은 제한시간 이하이고, 취소하면 empty
    @Test
    void remaining() {
        service.start(ROOM, 0, Duration.ofSeconds(5));

        assertThat(service.remaining(ROOM)).get()
                .satisfies(left -> assertThat(left).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(5)));
        assertThat(service.currentTeam(ROOM)).contains(0);
        assertThat(service.deadline(ROOM)).isPresent();

        service.cancel(ROOM);

        assertThat(service.remaining(ROOM)).isEmpty();
        assertThat(service.currentTeam(ROOM)).isEmpty();
    }

    // 0 이하 또는 null 제한시간은 예외
    @Test
    void invalidLimit() {
        assertThatThrownBy(() -> service.start(ROOM, 0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.start(ROOM, 0, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.start(ROOM, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 없는 방을 cancel해도 예외가 나지 않는다
    @Test
    void cancelUnknown() {
        service.cancel("NONE");

        assertThat(service.remaining("NONE")).isEmpty();
    }

    // 이벤트가 count개 모일 때까지 최대 timeout만큼 기다린다
    private void waitFor(int count, Duration timeout) throws InterruptedException {
        long end = System.currentTimeMillis() + timeout.toMillis();
        while (events.size() < count && System.currentTimeMillis() < end) {
            Thread.sleep(10);
        }
    }
}