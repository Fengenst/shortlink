package com.tenseed.shortlink.admin.common.biz.user;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.tenseed.shortlink.admin.common.convention.exception.ClientException;
import com.tenseed.shortlink.admin.common.convention.result.Results;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import static com.tenseed.shortlink.admin.common.enums.UserErrorCodeEnum.USER_TOKEN_FAIL;

/**
 * 用户信息传输拦截器
 */
@Slf4j
@RequiredArgsConstructor
public class UserTransmitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    private static final List<String> IGNORE_URI = List.of(
            "/api/shortlink/admin/v1/user/login",
            "/api/shortlink/admin/v1/user/is-username-available"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("UserContextInterceptor preHandle executed");
        String requestURI = request.getRequestURI();

        // 检查是否需要忽略的URI
        if (!IGNORE_URI.contains(requestURI)) {
            String method = request.getMethod();
            // 特殊处理用户注册接口
            if (!(requestURI.equals("/api/shortlink/admin/v1/user") && "POST".equals(method))) {
                String username = request.getHeader("username");
                String token = request.getHeader("token");

                // 验证用户名和token是否存在
                if (!StrUtil.isAllNotBlank(username, token)) {
                    returnJson(response, JSON.toJSONString(Results.failure(new ClientException(USER_TOKEN_FAIL))));
                    return false;
                }

                Object userInfoJsonStr;
                try {
                    // 从Redis中获取用户信息
                    userInfoJsonStr = stringRedisTemplate.opsForHash().get("login_" + username, token);
                    if (userInfoJsonStr == null) {
                        throw new ClientException(USER_TOKEN_FAIL);
                    }
                } catch (Exception ex) {
                    returnJson(response, JSON.toJSONString(Results.failure(new ClientException(USER_TOKEN_FAIL))));
                    return false;
                }

                // 解析用户信息并设置到上下文
                UserInfoDTO userInfoDTO = JSON.parseObject(userInfoJsonStr.toString(), UserInfoDTO.class);
                UserContext.setUser(userInfoDTO);
            }
        }

        return true; // 继续执行后续操作
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清理用户上下文
        UserContext.removeUser();
    }

    private void returnJson(HttpServletResponse response, String json) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=utf-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.print(json);
        }
    }
}
