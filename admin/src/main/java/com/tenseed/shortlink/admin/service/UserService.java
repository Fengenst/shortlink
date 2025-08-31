package com.tenseed.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tenseed.shortlink.admin.dao.entity.UserDO;
import com.tenseed.shortlink.admin.dto.req.UserRegisterReqDTO;
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
     *
     * @param username 用户名
     * @return 用户名存在返回false，不存在返回true
     */
    Boolean hasUsername(String username);

    /**
     *
     * @param requestParam 用户注册请求参数
     */
    void register(UserRegisterReqDTO requestParam);
}
