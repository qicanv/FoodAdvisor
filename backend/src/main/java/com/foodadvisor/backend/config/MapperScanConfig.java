package com.foodadvisor.backend.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Mapper 扫描配置。
 *
 * 独立于启动类，避免 @WebMvcTest 加载 MVC 测试环境时，
 * 同时创建需要 SqlSessionFactory 的 Mapper Bean。
 */
@Configuration(proxyBeanMethods = false)
@MapperScan({
        "com.foodadvisor.mapper",
        "com.foodadvisor.backend.mapper"
})
public class MapperScanConfig {
}