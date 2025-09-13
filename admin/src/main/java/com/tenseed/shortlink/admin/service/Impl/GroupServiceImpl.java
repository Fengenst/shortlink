package com.tenseed.shortlink.admin.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tenseed.shortlink.admin.common.biz.user.UserContext;
import com.tenseed.shortlink.admin.common.convention.result.Result;
import com.tenseed.shortlink.admin.dao.entity.GroupDO;
import com.tenseed.shortlink.admin.dao.mapper.GroupMapper;
import com.tenseed.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import com.tenseed.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.tenseed.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;
import com.tenseed.shortlink.admin.remote.ShortLinkRemoteService;
import com.tenseed.shortlink.admin.remote.dto.resp.ShortLinkGroupCountQueryRespDTO;
import com.tenseed.shortlink.admin.service.GroupService;
import com.tenseed.shortlink.admin.toolkit.RandomGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 短链接分组接口实现层
 */
@Slf4j
@Service
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {

    // TODO 后续重构为 SpringCloud Feign 调用
    ShortLinkRemoteService shortLinkRemoteService = new ShortLinkRemoteService() {
    };

    /**
     * 判断GID是否可用
     *
     * @param gid 分组表示
     * @return gid存在返回true，不存在返回false
     */
    public boolean availableGid(String username, String gid) {
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getUsername, Optional.ofNullable(username).orElse(UserContext.getUsername()));
        GroupDO groupDO = baseMapper.selectOne(queryWrapper);
        return groupDO != null;
    }

    @Override
    public void saveGroup(String groupName) {
        saveGroup(UserContext.getUsername(), groupName);
    }

    @Override
    public void saveGroup(String username, String groupName) {
        String gid;
        do {
            gid = RandomGenerator.generateSixAlphaNumber();
        } while (availableGid(username, gid));

        GroupDO groupDO = GroupDO.builder()
                .gid(gid)
                .name(groupName)
                .username(username)
                .sortOrder(0)
                .build();
        baseMapper.insert(groupDO);
    }

    @Override
    public List<ShortLinkGroupRespDTO> listGroup() {
        // 1. 构建查询条件：查询当前用户未删除的分组，按排序字段和创建时间倒序排列
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getDelFlag, 0)
                .orderByDesc(List.of(GroupDO::getSortOrder, GroupDO::getCreateTime));
        List<GroupDO> groupDOList = baseMapper.selectList(queryWrapper);

        // 2. 如果没有查询到分组，直接返回空列表
        if (CollectionUtils.isEmpty(groupDOList)) {
            return Collections.emptyList();
        }

        // 3. 提取分组ID列表，用于后续查询每个分组中的短链接数量
        List<String> gidList = groupDOList
                .stream()
                .map(GroupDO::getGid)
                .filter(Objects::nonNull)
                .toList();

        // 4. 远程调用获取每个分组中的短链接数量
        Result<List<ShortLinkGroupCountQueryRespDTO>> remoteResult = shortLinkRemoteService.listGroupShortLinkCount(gidList);
        List<ShortLinkGroupCountQueryRespDTO> shortlinkCounts = (remoteResult == null || remoteResult.getData() == null)
                ? Collections.emptyList()
                : remoteResult.getData();

        // 5. 构建分组ID与短链接数量的映射关系，处理可能的空值情况
        Map<String, Integer> counts = shortlinkCounts
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        ShortLinkGroupCountQueryRespDTO::getGid,
                        dto -> Optional.ofNullable(dto.getShortLinkCount()).orElse(0),
                        (existing, replacement) -> existing // 遇到重复 key 时保留第一个（也可改为 Integer::sum）
                ));

        // 6. 将分组DO对象转换为返回DTO对象，并设置每个分组的短链接数量
        List<ShortLinkGroupRespDTO> shortLinkGroupRespDTOList = BeanUtil.copyToList(groupDOList, ShortLinkGroupRespDTO.class);
        shortLinkGroupRespDTOList.forEach(g -> g.setShortLinkCount(counts.getOrDefault(g.getGid(), 0)));

        return shortLinkGroupRespDTOList;
    }


    @Override
    public void updateGroup(ShortLinkGroupUpdateReqDTO requestParam) {
        LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getGid, requestParam.getGid())
                .eq(GroupDO::getDelFlag, 0);
        GroupDO groupDO = GroupDO.builder()
                .name(requestParam.getName())
                .build();
        baseMapper.update(groupDO, updateWrapper);
    }

    @Override
    public void deleteGroup(String gid) {
        LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getDelFlag, 0);
        GroupDO groupDO = new GroupDO();
        groupDO.setDelFlag(1);
        baseMapper.update(groupDO, updateWrapper);
    }

    @Override
    public void sortGroup(List<ShortLinkGroupSortReqDTO> requestParam) {
        requestParam.forEach(each -> {
            LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                    .eq(GroupDO::getUsername, UserContext.getUsername())
                    .eq(GroupDO::getGid, each.getGid())
                    .eq(GroupDO::getDelFlag, 0);

            GroupDO groupDO = GroupDO.builder()
                    .sortOrder(each.getSortOrder())
                    .build();
            baseMapper.update(groupDO, updateWrapper);
        });
    }
}
