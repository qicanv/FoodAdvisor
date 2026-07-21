package com.foodadvisor;

import com.foodadvisor.config.RateLimitProperties;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RateLimitProperties.class)
public class FoodAdvisorApplication {

    public static void main(String[] args) {
        // Load .env when running from the project root directory.
        Dotenv.configure()
                .directory("./")
                .ignoreIfMissing()
                .systemProperties()
                .load();

        // Load the root .env when Maven runs from the backend directory.
        Dotenv.configure()
                .directory("../")
                .ignoreIfMissing()
                .systemProperties()
                .load();

        SpringApplication.run(FoodAdvisorApplication.class, args);
    }
}
