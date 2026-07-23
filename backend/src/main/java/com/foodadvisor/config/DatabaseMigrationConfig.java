package com.foodadvisor.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DatabaseMigrationConfig {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    public CommandLineRunner migrateDatabase() {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE reviews ADD COLUMN IF NOT EXISTS moderation_operator VARCHAR(100)");
                log.info("Successfully added moderation_operator column to reviews table");
                
                int updated = jdbcTemplate.update("UPDATE reviews SET moderation_operator = '演示管理员' WHERE moderation_operator IS NULL AND moderation_status IN ('APPROVED', 'REJECTED')");
                log.info("Updated {} records with default moderation operator", updated);
            } catch (Exception e) {
                log.warn("Failed to migrate database: {}", e.getMessage());
            }
        };
    }
}