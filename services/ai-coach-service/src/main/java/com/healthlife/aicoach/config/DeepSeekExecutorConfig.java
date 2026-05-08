package com.healthlife.aicoach.config;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dedicated thread pool for blocking DeepSeek API calls.
 *
 * <p>DeepSeek responses can take up to 30 seconds. Running {@code .block()} on a Tomcat thread
 * would exhaust the servlet thread pool under moderate load. This executor isolates those
 * blocking calls so Tomcat threads are always free to accept new requests.
 *
 * <p>Sizing: 20 core threads handles ~20 concurrent AI requests. The queue depth of 100 provides
 * back-pressure before rejecting. Adjust via {@code AI_EXECUTOR_CORE_THREADS} env var if needed.
 */
@Configuration
public class DeepSeekExecutorConfig {

    @Bean(name = "deepseekExecutor")
    public Executor deepseekExecutor() {
        int coreThreads = Integer.parseInt(System.getenv().getOrDefault("AI_EXECUTOR_CORE_THREADS", "20"));
        int maxThreads = coreThreads * 2;
        return new ThreadPoolExecutor(
                coreThreads,
                maxThreads,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r, "deepseek-api-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
