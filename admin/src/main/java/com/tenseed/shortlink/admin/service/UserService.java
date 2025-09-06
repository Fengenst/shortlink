package com.tenseed.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tenseed.shortlink.admin.dao.entity.UserDO;
import com.tenseed.shortlink.admin.dto.req.UserLoginReqDTO;
import com.tenseed.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.tenseed.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.tenseed.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.tenseed.shortlink.admin.dto.resp.UserRespDTO;

/**
 * 用户接口层
 */
public interface UserService extends IService<UserDO> {
    /**
     * 根据用户名查询用户信息
     *
     * @param username 用户名
     * @return 用户返回实体
     */
    UserRespDTO getUserByUsername(String username);

    /**
     * 判断用户名是否可用
     *
     * @param username 用户名
     * @return 用户名可用返回true，不可用返回false
     */
    Boolean isUsernameAvailable(String username);

    /**
     *
     * @param requestParam 用户注册请求参数
     */
    void register(UserRegisterReqDTO requestParam);

    /**
     * 根据用户名修改用户信息
     *
     * @param requestParam 修改用户信息请求参数
     */
    void update(UserUpdateReqDTO requestParam);

    /**
     * 用户登录
     *
     * @param requestParam 用户登录请求参数
     * @return 用户登录返回实体 Token
     */
    UserLoginRespDTO login(UserLoginReqDTO requestParam);

    /**
     * 检查用户是否登录
     *
     * @param username 用户名
     * @param token 用户登录token
     * @return 用户是否登录标识
     */
    Boolean checkLogin(String username, String token);

    /**
     * 用户登出
     *
     * @param username 用户名
     * @param token    用户登录token
     */
    void logout(String username, String token);
}
