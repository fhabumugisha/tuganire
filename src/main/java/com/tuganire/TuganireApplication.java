package com.tuganire;

import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {PgVectorStoreAutoConfiguration.class})
public class TuganireApplication {

    public static void main(String[] args) {
        SpringApplication.run(TuganireApplication.class, args);
    }
}
