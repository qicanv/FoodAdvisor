package com.foodadvisor.backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.foodadvisor")
public class BackendApplication {

    public static void main(String[] args) {
        // mvn 从 backend/ 运行时，"../" 是项目根目录；
        // IDE 从项目根目录运行时，"./" 是项目根目录。
        Dotenv.configure()
                .directory("./")
                .ignoreIfMissing()
                .systemProperties()
                .load();

        Dotenv.configure()
                .directory("../")
                .ignoreIfMissing()
                .systemProperties()
                .load();

        SpringApplication.run(BackendApplication.class, args);
    }
}