package com.foodadvisor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(
            JwtInterceptor jwtInterceptor,
            RateLimitInterceptor rateLimitInterceptor
    ) {
        this.jwtInterceptor = jwtInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/health",
                        "/api/password/**",
                        "/api/merchants/**",
                        "/api/hot-words/**",
                        "/api/reviews/tags",
                        "/api/reviews/merchants/*/issue-stats",
                        "/api/reviews/merchants/*/issue-categories/*/reviews",
                        "/api/reviews/issue-categories"
                )
                .order(0);
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/health",
                        "/error"
                )
                .order(1);
    }
}
