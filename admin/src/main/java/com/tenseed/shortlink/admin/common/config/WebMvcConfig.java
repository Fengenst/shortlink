package com.tenseed.shortlink.admin.common.config;

import com.tenseed.shortlink.admin.common.biz.user.UserTransmitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册用户上下文拦截器
        registry.addInterceptor(new UserTransmitInterceptor(stringRedisTemplate))
                .addPathPatterns("/**") // 拦截所有请求
                .excludePathPatterns("/api/shortlink/admin/v1/user/login",
                        "/api/shortlink/admin/v1/user/is-username-available");
    }
}
