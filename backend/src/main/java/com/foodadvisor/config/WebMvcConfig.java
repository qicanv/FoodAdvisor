package com.foodadvisor.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(
            JwtInterceptor jwtInterceptor,
            ObjectProvider<RateLimitInterceptor> rateLimitInterceptorProvider
    ) {
        this.jwtInterceptor = jwtInterceptor;
        // RateLimitInterceptor may be absent when Redis is unavailable (e.g. in tests);
        // in that case the bean is never created and we skip rate-limit registration.
        this.rateLimitInterceptor = rateLimitInterceptorProvider.getIfAvailable();
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
        if (rateLimitInterceptor != null) {
            registry.addInterceptor(rateLimitInterceptor)
                    .addPathPatterns("/api/**")
                    .excludePathPatterns(
                            "/api/health",
                            "/error"
                    )
                    .order(1);
        }
    }
}
