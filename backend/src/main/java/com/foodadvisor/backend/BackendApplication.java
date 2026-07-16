package com.foodadvisor.backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FoodAdvisor 后端启动入口。
 * <p>
 * 启动时会自动加载项目根目录的 {@code .env} 文件，
 * 把里面的配置注入到系统属性中，Spring Boot 的
 * {@code ${VAR_NAME:default}} 占位符可以直接读取。
 * <p>
 * 如果命令行已经设置了同名配置，
 * 则以命令行配置为准，不会被 {@code .env} 覆盖。
 */
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