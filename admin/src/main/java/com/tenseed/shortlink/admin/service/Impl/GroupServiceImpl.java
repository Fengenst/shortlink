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
     * @return gid 存在返回 true，不存在返回 false
     */
    public boolean availableGid(String username, String gid) {
        // 构造查询条件：根据 gid 和用户名查询分组
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getGid, gid)
                // 如果 username 参数不为空则使用该用户名，否则从用户上下文获取当前登录用户名
                .eq(GroupDO::getUsername, Optional.ofNullable(username).orElse(UserContext.getUsername()));
        GroupDO groupDO = baseMapper.selectOne(queryWrapper);
        // 如果查询到结果说明 gid 已存在，返回 true；否则返回 false
        return groupDO != null;
    }

    @Override
    public void saveGroup(String groupName) {
        // 使用当前登录用户创建分组
        saveGroup(UserContext.getUsername(), groupName);
    }

    @Override
    public void saveGroup(String username, String groupName) {
        String gid;
        // 循环生成唯一 GID，直到找到未被使用的 GID
        do {
            gid = RandomGenerator.generateSixAlphaNumber();
        } while (availableGid(username, gid));

        // 构建分组对象并保存到数据库
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

        // 3. 提取分组 ID 列表，用于后续查询每个分组中的短链接数量
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
                .eq(GroupDO::getGid, requestParam.getGid())
                .eq(GroupDO::getDelFlag, 0);

        // 构建更新对象，只更新分组名称
        GroupDO groupDO = GroupDO.builder()
                .name(requestParam.getName())  // 新的分组名称
                .build();

        // 执行更新操作
        baseMapper.update(groupDO, updateWrapper);
    }


    @Override
    public void deleteGroup(String gid) {
        // 构造更新条件：根据当前用户、分组ID和未删除状态进行更新
        LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getDelFlag, 0);

        // 逻辑删除：设置删除标识为1，不实际删除数据
        GroupDO groupDO = new GroupDO();
        groupDO.setDelFlag(1);  // 标记为已删除

        // 执行更新操作
        baseMapper.update(groupDO, updateWrapper);
    }

    @Override
    public void sortGroup(List<ShortLinkGroupSortReqDTO> requestParam) {
        // 遍历排序参数列表，逐个更新分组的排序值
        requestParam.forEach(each -> {
            // 构造更新条件：根据当前用户、分组ID和未删除状态进行更新
            LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                    .eq(GroupDO::getUsername, UserContext.getUsername())
                    .eq(GroupDO::getGid, each.getGid())
                    .eq(GroupDO::getDelFlag, 0);

            // 构建更新对象，只更新排序字段
            GroupDO groupDO = GroupDO.builder()
                    .sortOrder(each.getSortOrder())  // 新的排序值
                    .build();

            // 执行更新操作
            baseMapper.update(groupDO, updateWrapper);
        });
    }

}
