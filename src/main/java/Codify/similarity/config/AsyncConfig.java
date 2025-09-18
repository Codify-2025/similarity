package Codify.similarity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("analysisExecutor")
    public ThreadPoolTaskExecutor analysisExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("analysis-");
        ex.initialize();
        return ex;
    }

    //비동기 처리 중 데이터 유실을 막기 위해 CallerRunsPolicy정책 사용
    //Interface 반환
    @Bean("similarityExecutor")
    public Executor similarityExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);        // 핵심 스레드 10개
        executor.setMaxPoolSize(20);         // 최대 20개 (피크 시)
        executor.setQueueCapacity(50);       // 대기열 50개
        executor.setThreadNamePrefix("similarity-");
        executor.setRejectedExecutionHandler(new
                ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
