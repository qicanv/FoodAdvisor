package com.foodadvisor.backend.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus Mapper 扫描配置。
 * <p>
 * 将 {@code @MapperScan} 从主启动类移到独立的配置类，
 * 使 {@code @WebMvcTest} 等切片测试不会加载此配置，
 * 避免测试上下文因缺少 DataSource 而启动失败。
 */
@Configuration
@MapperScan({
        "com.foodadvisor.mapper",
        "com.foodadvisor.backend.mapper"
})
public class MybatisPlusConfig {
}
