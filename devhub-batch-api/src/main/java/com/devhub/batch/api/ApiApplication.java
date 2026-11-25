package com.devhub.batch.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

/**
 * DevHub Batch API — Spring Boot 3 facade over devhub-batch-core.
 *
 * The core engine (Scheduler/TaskJob/JdbcTaskRepository) is used as a plain
 * library: the API shares the same DataSource and NEVER bypasses core logic
 * for writes. Reads (pagination, stats) go through a dedicated JdbcTemplate
 * read model on the same SYS_ tables.
 */
@SpringBootApplication
public class ApiApplication {

    public static void main(String[] args) {
        // Ops console contract: all wall-clock timestamps (NEXT_RUN_AT, EXECUTED_AT,
        // heartbeats) are stored as Asia/Ho_Chi_Minh local time — matching the
        // default task TIMEZONE (fix Y2). Override with -Duser.timezone=... if needed.
        String userTz = System.getProperty("user.timezone", "");
        if (userTz.isBlank()) {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        }
        SpringApplication.run(ApiApplication.class, args);
    }
}
