package com.teamyutnori.yutnori.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;

// 턴 타이머 전용 스케줄러, 시계
@Configuration
public class TurnTimerConfig {

    // 타이머 만료 콜백 실행 스레드 풀
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler turnTimerScheduler(){
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("turn-timer-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock clock(){
        return Clock.systemUTC();
    }
}
