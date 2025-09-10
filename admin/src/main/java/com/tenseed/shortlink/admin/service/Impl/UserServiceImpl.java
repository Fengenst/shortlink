package com.tenseed.shortlink.admin.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tenseed.shortlink.admin.common.convention.exception.ClientException;
import com.tenseed.shortlink.admin.common.enums.UserErrorCodeEnum;
import com.tenseed.shortlink.admin.dao.entity.UserDO;
import com.tenseed.shortlink.admin.dao.mapper.UserMapper;
import com.tenseed.shortlink.admin.dto.req.UserLoginReqDTO;
import com.tenseed.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.tenseed.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.tenseed.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.tenseed.shortlink.admin.dto.resp.UserRespDTO;
import com.tenseed.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.tenseed.shortlink.admin.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;

/**
 * 用户接口实现层
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;

    private final RedissonClient redissonClient;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public UserRespDTO getUserByUsername(String username) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if (userDO == null) {
            throw new ClientException(UserErrorCodeEnum.USER_NULL);
        }
        UserRespDTO result = new UserRespDTO();
        BeanUtils.copyProperties(userDO, result);
        return result;
    }

    @Override
    public Boolean isUsernameAvailable(String username) {
        // 使用布隆过滤器检查用户名是否存在
        return !userRegisterCachePenetrationBloomFilter.contains(username);
    }

    @Override
    public void register(UserRegisterReqDTO requestParam) {
        // 检查用户名是否已存在（布隆过滤器判断）
        if (!isUsernameAvailable(requestParam.getUsername())) {
            throw new ClientException(UserErrorCodeEnum.USER_NAME_EXIST);
        }

        // 获取分布式锁，防止并发注册
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY + requestParam.getUsername());
        try {
            // 使用tryLock而非lock，避免阻塞等待，实现快速失败
            if (lock.tryLock()) {
                try {
                    // 插入用户数据
                    int insert = baseMapper.insert(BeanUtil.toBean(requestParam, UserDO.class));
                    if (insert < 1) {
                        throw new ClientException(UserErrorCodeEnum.USER_SAVE_ERROR);
                    }
                } catch (DuplicateKeyException ex) {
                    throw new ClientException(UserErrorCodeEnum.USER_EXIST);
                }
                // 更新布隆过滤器
                userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());
                return;
            }
            // 获取锁失败，说明可能有其他线程正在注册相同用户名
            throw new ClientException(UserErrorCodeEnum.USER_NAME_EXIST);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }


    @Override
    public void update(UserUpdateReqDTO requestParam) {
        // TODO 验证当前用户名是否为登录用户
        LambdaQueryWrapper<UserDO> updateWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername());
        baseMapper.update(BeanUtil.toBean(requestParam, UserDO.class), updateWrapper);
    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername())
                .eq(UserDO::getPassword, requestParam.getPassword())
                .eq(UserDO::getDelFlag, 0);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if (userDO == null) {
            throw new ClientException(UserErrorCodeEnum.USER_NULL);
        }
        /*
          为了确保同一个用户名在同一时间只能有一个活跃的登录会话（即不允许重复登录）
          使用 Redis Hash
          Key: login_用户名
          Value:
           HashKey: token标识, 即下面的uuid
           HashValue: JSON字符串, 即用户信息
         */
        //查询Redis中是否存在一个 key为"login_用户名"的记录
        Boolean hasLogin = stringRedisTemplate.hasKey("login_" + requestParam.getUsername());
        if (hasLogin) {
            //如果存在，说明该用户已经登录过，不允许再次登录，抛出异常
            throw new ClientException(UserErrorCodeEnum.USER_HAS_LOGIN);
        }
        //将用户信息存入 Redis Hash
        String uuid = UUID.randomUUID().toString(); //生成一个唯一标识  ----uuid
        stringRedisTemplate.opsForHash().put("login_" + requestParam.getUsername(), uuid, JSON.toJSONString(userDO));
        //设置过期时间
        stringRedisTemplate.expire("login_" + requestParam.getUsername(), 30L, TimeUnit.DAYS);
        return new UserLoginRespDTO(uuid);
    }

    @Override
    public Boolean checkLogin(String username, String token) {
        return stringRedisTemplate.opsForHash().get("login_" + username, token) != null;
    }

    @Override
    public void logout(String username, String token) {
        if (checkLogin(username, token)) {
            stringRedisTemplate.delete("login_" + username);
            return;
        }
        throw new ClientException("用户token不存在或用户未登录");
    }
}
