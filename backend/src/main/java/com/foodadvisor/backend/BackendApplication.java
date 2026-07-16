package com.foodadvisor.backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication(scanBasePackages = "com.foodadvisor")
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
