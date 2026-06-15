package com.kkpp.catalog.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /**
     * "오늘"의 기준 시각을 결정하는 Clock. 농자재 상점 사용자는 국내 사용자이므로
     * 서버 타임존과 무관하게 한국 시간(KST) 기준으로 "오늘"을 판정한다.
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
