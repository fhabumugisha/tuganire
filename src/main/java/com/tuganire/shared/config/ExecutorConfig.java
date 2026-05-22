package com.tuganire.shared.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfig {

    @Bean(name = "chatExecutorService")
    public ExecutorService chatExecutorService() {
        return Executors.newCachedThreadPool();
    }
}
