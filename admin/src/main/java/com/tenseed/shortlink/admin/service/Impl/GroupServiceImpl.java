package com.tenseed.shortlink.admin.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    public boolean availableGid(String gid) {
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getUsername, UserContext.getUsername());
        GroupDO groupDO = baseMapper.selectOne(queryWrapper);
        return groupDO != null;
    }

    @Override
    public void save(String groupName) {
        String gid;
        do {
            gid = RandomGenerator.generateSixAlphaNumber();
        } while (availableGid(gid));

        GroupDO groupDO = GroupDO.builder()
                .gid(gid)
                .name(groupName)
                .username(UserContext.getUsername())
                .sortOrder(0)
                .build();
        baseMapper.insert(groupDO);
    }

    @Override
    public List<ShortLinkGroupRespDTO> listGroup() {
        // 构建查询条件
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getDelFlag, 0)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                // 原来是 .orderByDesc(GroupDO::getSortOrder, GroupDO::getUpdateTime)
                // 改成 .orderByDesc(List.of(GroupDO::getSortOrder, GroupDO::getUpdateTime)) 就不会报警告了
                .orderByDesc(List.of(GroupDO::getSortOrder, GroupDO::getUpdateTime));

        // 先查到满足queryWrapper条件的GroupDO短链接分组集合，也就是当前用户创建的短链接分组
        List<GroupDO> groupDOList = baseMapper.selectList(queryWrapper);

        // 根据该用户创建的短链接分组集合（即groupDOList），进而获取每个分组的gid
        // 然后根据 gid集合 调用 shortLinkRemoteService.listGroupShortLinkCount() 查询出每个分组的短链接数量
        // 最后封装为 Result<List<ShortLinkGroupCountQueryRespDTO>>
        Result<List<ShortLinkGroupCountQueryRespDTO>> listResult = shortLinkRemoteService
                .listGroupShortLinkCount(groupDOList.stream() // 将 List<GroupDO> 对象转化为 Stream<GroupDO> 对象
                        // .map()方法会把流中的每个元素（一个 GroupDO 对象）映射成另外一种类型
                        // 把 Stream<GroupDO> 转换成 Stream<String> 即 Stream 流中的元素就从 GroupDO 对象 变成了 gid 字符串
                        .map(GroupDO::getGid)
                        .toList()); // 把流 Stream<String> 收集为一个 List<String>

        // 将当前用户创建的短链接分组集合从 List<GroupDO> 转换为 List<ShortLinkGroupRespDTO>
        List<ShortLinkGroupRespDTO> shortLinkGroupRespDTOList = BeanUtil.copyToList(groupDOList, ShortLinkGroupRespDTO.class);

        // 接下来处理 shortLinkGroupRespDTOList
        // forEach 遍历列表中的每一个 ShortLinkGroupRespDTO，这里用 each 表示当前正在处理的分组 DTO
        shortLinkGroupRespDTOList.forEach(each -> {
            // listResult 是根据 gid 查询出来的各个短链接分组的数量，由于一个用户可以创建多个短链接分组，因而需要找到每一个分组下的短链接数量
            // 所以就需要像现在这个代码一样进行遍历，相当于进行两层的for循环，匹配条件为gid相同，因为一个用户创建的多个短链接分组的gid不会重复，所以取第一个即可
            // listResult.getData() 是为了获取 List<ShortLinkGroupCountQueryRespDTO>
            // first就是每一个短链接分组对应的分组数量信息
            Optional<ShortLinkGroupCountQueryRespDTO> first = listResult.getData()
                    .stream()
                    .filter(item -> Objects.equals(item.getGid(), each.getGid()))
                    .findFirst();
            // 若first不为空，那么就执行接下来的代码，为每个短链接分组的shortLinkCount赋值
            first.ifPresent(item -> each.setShortLinkCount(item.getShortLinkCount()));
        });

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
