package com.teamyutnori.yutnori.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// @Scheduled 작업 사용 (GuestCleanupService: 하루 한 번 게스트 정리)
// 스케줄러는 TurnTimerConfig의 turnTimerScheduler를 함께 쓴다 (TaskScheduler 빈이 하나뿐이라 자동 선택).
// 정리 작업은 하루 한 번, 짧게 끝나서 턴 타이머에 영향이 거의 없다
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
