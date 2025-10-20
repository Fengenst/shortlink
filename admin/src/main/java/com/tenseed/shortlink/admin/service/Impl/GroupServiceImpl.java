package com.tenseed.shortlink.admin.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tenseed.shortlink.admin.common.biz.user.UserContext;
import com.tenseed.shortlink.admin.common.convention.exception.ClientException;
import com.tenseed.shortlink.admin.common.convention.exception.ServiceException;
import com.tenseed.shortlink.admin.common.convention.result.Result;
import com.tenseed.shortlink.admin.dao.entity.GroupDO;
import com.tenseed.shortlink.admin.dao.entity.GroupUniqueDO;
import com.tenseed.shortlink.admin.dao.mapper.GroupMapper;
import com.tenseed.shortlink.admin.dao.mapper.GroupUniqueMapper;
import com.tenseed.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import com.tenseed.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.tenseed.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;
import com.tenseed.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.tenseed.shortlink.admin.remote.dto.resp.ShortLinkGroupCountQueryRespDTO;
import com.tenseed.shortlink.admin.service.GroupService;
import com.tenseed.shortlink.admin.toolkit.RandomGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.tenseed.shortlink.admin.common.constant.RedisCacheConstant.LOCK_GROUP_CREATE_KEY;

/**
 * 短链接分组接口实现层
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {

    private final RBloomFilter<String> gidRegisterCachePenetrationBloomFilter;
    private final GroupUniqueMapper groupUniqueMapper;
    private final ShortLinkActualRemoteService shortLinkActualRemoteService;
    private final RedissonClient redissonClient;

    @Value("${short-link.group.max-num}")
    private Integer groupMaxNum;

    private String saveGroupUniqueReturnGid() {
        String gid = RandomGenerator.generateSixAlphaNumber();
        if (gidRegisterCachePenetrationBloomFilter.contains(gid)) {
            return null;
        }
        GroupUniqueDO groupUniqueDO = GroupUniqueDO.builder()
                .gid(gid)
                .build();
        try {
            groupUniqueMapper.insert(groupUniqueDO);
        } catch (DuplicateKeyException e) {
            return null;
        }
        return gid;
    }

    @Override
    public void saveGroup(String groupName) {
        // 使用当前登录用户创建分组
        saveGroup(UserContext.getUsername(), groupName);
    }

    @Override
    public void saveGroup(String username, String groupName) {
        RLock lock = redissonClient.getLock(String.format(LOCK_GROUP_CREATE_KEY, username));
        lock.lock();
        try {
            LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                    .eq(GroupDO::getUsername, username);
            List<GroupDO> groupDOList = baseMapper.selectList(queryWrapper);
            if (CollUtil.isNotEmpty(groupDOList) && groupDOList.size() == groupMaxNum) {
                throw new ClientException(String.format("已超出最大分组数： %d", groupMaxNum));
            }
            int retryCount = 0;
            int maxRetries = 10;
            String gid = null;
            while (retryCount < maxRetries) {
                gid = saveGroupUniqueReturnGid();
                if (StrUtil.isNotEmpty(gid)) {
                    GroupDO groupDO = GroupDO.builder()
                            .gid(gid)
                            .sortOrder(0)
                            .username(username)
                            .name(groupName)
                            .build();
                    baseMapper.insert(groupDO);
                    gidRegisterCachePenetrationBloomFilter.add(gid);
                    break;
                }
                retryCount++;
            }
            if (StrUtil.isEmpty(gid)) {
                throw new ServiceException("生成分组标识频繁");
            }
        } finally {
            lock.unlock();
        }
    }


    @Override
    public List<ShortLinkGroupRespDTO> listGroup() {
        // 1. 构建查询条件：查询当前用户未删除的分组，按排序字段和创建时间倒序排列
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .orderByDesc(List.of(GroupDO::getSortOrder, GroupDO::getCreateTime));
        List<GroupDO> groupDOList = baseMapper.selectList(queryWrapper);

        // 2. 如果没有查询到分组，直接返回空列表
        if (CollectionUtils.isEmpty(groupDOList)) {
            return Collections.emptyList();
        }

        // 3. 提取分组 ID 列表，用于后续查询每个分组中的短链接数量
        List<String> gidList = groupDOList
                .stream()
                .map(GroupDO::getGid)
                .filter(Objects::nonNull)
                .toList();

        // 4. 远程调用获取每个分组中的短链接数量
        Result<List<ShortLinkGroupCountQueryRespDTO>> listResult = shortLinkActualRemoteService.listGroupShortLinkCount(gidList);
        List<ShortLinkGroupCountQueryRespDTO> shortlinkCounts = (listResult == null || listResult.getData() == null)
                ? Collections.emptyList()
                : listResult.getData();

        // 5. 构建分组 ID与短链接数量的映射关系，处理可能的空值情况
        Map<String, Integer> counts = shortlinkCounts
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        ShortLinkGroupCountQueryRespDTO::getGid,
                        dto -> Optional.ofNullable(dto.getShortLinkCount()).orElse(0),
                        (existing, replacement) -> existing // 遇到重复 key 时保留第一个（也可改为 Integer::sum）
                ));

        // 6. 将分组 DO 对象转换为返回 DTO 对象，并设置每个分组的短链接数量
        List<ShortLinkGroupRespDTO> shortLinkGroupRespDTOList = BeanUtil.copyToList(groupDOList, ShortLinkGroupRespDTO.class);
        shortLinkGroupRespDTOList.forEach(g -> g.setShortLinkCount(counts.getOrDefault(g.getGid(), 0)));

        return shortLinkGroupRespDTOList;
    }


    @Override
    public void updateGroup(ShortLinkGroupUpdateReqDTO requestParam) {
        // 构造更新条件：根据当前用户、分组ID和未删除状态进行更新
        LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getGid, requestParam.getGid());

        // 构建更新对象，只更新分组名称
        GroupDO groupDO = GroupDO.builder()
                .name(requestParam.getName())  // 新的分组名称
                .build();

        // 执行更新操作
        baseMapper.update(groupDO, updateWrapper);
    }


    @Override
    public void deleteGroup(String gid) {
        // 构造查询条件（DELETE 语句的 WHERE 条件）
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getGid, gid);

        // 调用 delete 方法，MP 拦截器会自动转换为 UPDATE ... SET del_flag = 1
        baseMapper.delete(queryWrapper);
    }

    @Override
    public void sortGroup(List<ShortLinkGroupSortReqDTO> requestParam) {
        // 遍历排序参数列表，逐个更新分组的排序值
        requestParam.forEach(each -> {
            // 构造更新条件：根据当前用户、分组ID和未删除状态进行更新
            LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                    .eq(GroupDO::getUsername, UserContext.getUsername())
                    .eq(GroupDO::getGid, each.getGid());

            // 构建更新对象，只更新排序字段
            GroupDO groupDO = GroupDO.builder()
                    .sortOrder(each.getSortOrder())  // 新的排序值
                    .build();

            // 执行更新操作
            baseMapper.update(groupDO, updateWrapper);
        });
    }

}
