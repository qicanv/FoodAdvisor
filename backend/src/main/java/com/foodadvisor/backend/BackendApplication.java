package com.foodadvisor.backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FoodAdvisor 后端启动入口。
 * <p>
 * 启动时会自动加载项目根目录的 {@code .env} 文件，
 * 把里面的配置注入到系统环境变量，Spring Boot 的
 * {@code ${VAR_NAME:default}} 占位符就能直接读到。
 * <p>
 * 如果命令行已经设了同名环境变量（比如 {@code POSTGRES_PASSWORD=xxx}），
 * 则以命令行的为准，不会被 {@code .env} 覆盖。
 */
@SpringBootApplication(scanBasePackages = "com.foodadvisor")
@MapperScan({
        "com.foodadvisor.mapper",
        "com.foodadvisor.backend.mapper"
})
public class BackendApplication {

    public static void main(String[] args) {
        // ---- 加载项目根目录的 .env 文件 ----
        // mvn 从 backend/ 运行 → "../" 就是项目根目录
        // IDE 从项目根运行 → "./" 就是项目根目录
        // 两个都试，至少命中一个；ignoreIfMissing 保证找不到也不会报错
        Dotenv.configure().directory("./").ignoreIfMissing().systemProperties().load();
        Dotenv.configure().directory("../").ignoreIfMissing().systemProperties().load();

        SpringApplication.run(BackendApplication.class, args);
    }
}
