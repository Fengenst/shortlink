package com.tenseed.shortlink.admin.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tenseed.shortlink.admin.common.biz.user.UserContext;
import com.tenseed.shortlink.admin.common.convention.exception.ClientException;
import com.tenseed.shortlink.admin.common.enums.UserErrorCodeEnum;
import com.tenseed.shortlink.admin.dao.entity.UserDO;
import com.tenseed.shortlink.admin.dao.mapper.UserMapper;
import com.tenseed.shortlink.admin.dto.req.UserLoginReqDTO;
import com.tenseed.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.tenseed.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.tenseed.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.tenseed.shortlink.admin.dto.resp.UserRespDTO;
import com.tenseed.shortlink.admin.service.GroupService;
import com.tenseed.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.tenseed.shortlink.admin.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;
import static com.tenseed.shortlink.admin.common.constant.RedisCacheConstant.USER_LOGIN_KEY;
import static com.tenseed.shortlink.admin.common.enums.UserErrorCodeEnum.*;

/**
 * 用户接口实现层
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;

    private final RedissonClient redissonClient;

    private final StringRedisTemplate stringRedisTemplate;

    private final GroupService groupService;

    @Override
    public UserRespDTO getUserByUsername(String username) {
        // 根据用户名查询用户信息
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        // 如果用户不存在，抛出异常
        if (userDO == null) {
            throw new ClientException(UserErrorCodeEnum.USER_NULL);
        }
        // 将用户DO对象转换为响应DTO对象
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
    @Transactional
    public void register(UserRegisterReqDTO requestParam) {
        // 检查用户名是否已存在（布隆过滤器判断）
        if (!isUsernameAvailable(requestParam.getUsername())) {
            throw new ClientException(USER_NAME_EXIST);
        }

        // 获取分布式锁，防止并发注册
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY + requestParam.getUsername());
        // 获取锁失败，说明可能有其他线程正在注册相同用户名
        // 使用tryLock而非lock，避免阻塞等待，实现快速失败
        if (!lock.tryLock()) {
            throw new ClientException(USER_NAME_EXIST);
        }
        try {
            // 插入用户数据
            int inserted = baseMapper.insert(BeanUtil.toBean(requestParam, UserDO.class));
            if (inserted < 1) {
                throw new ClientException(USER_SAVE_ERROR);
            }
            // 更新布隆过滤器
            userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());
            groupService.saveGroup(requestParam.getUsername(), "默认分组");
            throw new ClientException(USER_NAME_EXIST);
        } catch (DuplicateKeyException ex) {
            throw new ClientException(USER_EXIST);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }


    @Override
    public void update(UserUpdateReqDTO requestParam) {
        if (!Objects.equals(requestParam.getUsername(), UserContext.getUsername())) {
            throw new ClientException("当前登录用户修改请求异常");
        }
        LambdaQueryWrapper<UserDO> updateWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername());
        baseMapper.update(BeanUtil.toBean(requestParam, UserDO.class), updateWrapper);
    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        // 构建查询条件，根据用户名、密码和未删除状态查询用户
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername())
                .eq(UserDO::getPassword, requestParam.getPassword());
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        // 用户不存在或密码错误时抛出异常
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
        Map<Object, Object> hasLoginMap = stringRedisTemplate.opsForHash().entries(USER_LOGIN_KEY + requestParam.getUsername());
        if (CollUtil.isNotEmpty(hasLoginMap)) {
            stringRedisTemplate.expire(USER_LOGIN_KEY + requestParam.getUsername(), 30L, TimeUnit.MINUTES);
            String token = hasLoginMap.keySet().stream()
                    .findFirst()
                    .map(Object::toString)
                    .orElseThrow(() -> new ClientException("用户登陆错误"));
            return new UserLoginRespDTO(token);
        }
        //将用户信息存入 Redis Hash
        String uuid = UUID.randomUUID().toString(); //生成一个唯一标识  ----uuid
        stringRedisTemplate.opsForHash().put(USER_LOGIN_KEY + requestParam.getUsername(), uuid, JSON.toJSONString(userDO));
        //设置过期时间
        stringRedisTemplate.expire(USER_LOGIN_KEY + requestParam.getUsername(), 30L, TimeUnit.MINUTES);
        return new UserLoginRespDTO(uuid);
    }

    @Override
    public Boolean checkLogin(String username, String token) {
        // 检查Redis中是否存在指定用户的登录token，验证用户是否已登录
        return stringRedisTemplate.opsForHash().get(USER_LOGIN_KEY + username, token) != null;
    }

    @Override
    public void logout(String username, String token) {
        // 验证用户是否已登录
        if (checkLogin(username, token)) {
            // 删除用户的登录会话信息，实现登出
            stringRedisTemplate.delete(USER_LOGIN_KEY + username);
            return;
        }
        // token不存在或用户未登录时抛出异常
        throw new ClientException("用户token不存在或用户未登录");
    }
}
