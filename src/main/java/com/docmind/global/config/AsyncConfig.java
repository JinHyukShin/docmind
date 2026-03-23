package com.docmind.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Virtual Threads 기반 비동기 설정.
 * 문서 파싱, 임베딩 생성 등 I/O 바운드 작업에 활용.
 *
 * @EnableAsync가 "taskExecutor" 이름의 빈을 기본으로 찾으므로
 * TaskExecutorAdapter로 래핑하여 Spring @Async와 호환시킨다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public Executor taskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
