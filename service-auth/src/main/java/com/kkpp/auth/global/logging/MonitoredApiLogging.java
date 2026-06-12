package com.kkpp.auth.global.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MonitoredApiLogging {

    // 로그와 trace에서 API 흐름을 식별하기 위한 이벤트 이름입니다.
    String event();

    // 운영자가 로그를 볼 때 이해하기 쉬운 API 한글 이름입니다.
    String apiName();
}
