package com.tenseed.shortlink.admin.common.config;

import com.tenseed.shortlink.admin.common.biz.user.UserFlowRiskControlFilter;
import com.tenseed.shortlink.admin.common.biz.user.UserTransmitFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class UserConfiguration {

    /**
     * 用户信息传递过滤器
     */
    @Bean
    public FilterRegistrationBean<UserTransmitFilter> globalUserTransmitFilter(StringRedisTemplate stringRedisTemplate) {
        FilterRegistrationBean<UserTransmitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new UserTransmitFilter(stringRedisTemplate));
        registration.addUrlPatterns("/*");
        registration.addInitParameter("Login", "/api/shortlink/admin/v1/user/login");
        registration.setOrder(0);
        return registration;
    }

    /**
     * 用户操作流量风控过滤器
     */
    @Bean
    @ConditionalOnProperty(name = "short-link.flow-limit.enable", havingValue = "true")
    public FilterRegistrationBean<UserFlowRiskControlFilter> globalUserFlowRiskControlFilter(
            StringRedisTemplate stringRedisTemplate,
            UserFlowRiskControlConfiguration userFlowRiskControlConfiguration) {
        FilterRegistrationBean<UserFlowRiskControlFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new UserFlowRiskControlFilter(stringRedisTemplate, userFlowRiskControlConfiguration));
        registration.addUrlPatterns("/*");
        registration.setOrder(10);
        return registration;
    }
}
