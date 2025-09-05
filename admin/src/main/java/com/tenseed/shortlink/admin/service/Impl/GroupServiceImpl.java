package com.tenseed.shortlink.admin.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tenseed.shortlink.admin.dao.entity.GroupDO;
import com.tenseed.shortlink.admin.dao.mapper.GroupMapper;
import com.tenseed.shortlink.admin.service.GroupService;
import com.tenseed.shortlink.admin.toolkit.RandomGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 短链接分组接口实现层
 */
@Slf4j
@Service
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {

    @Override
    public void save(String groupName) {
        String gid;
        do {
            gid = RandomGenerator.generateSixAlphaNumber();
        } while (availableGid(gid));

        GroupDO groupDO = GroupDO.builder()
                .gid(gid)
                .name(groupName)
                .build();
        baseMapper.insert(groupDO);
    }

    /**
     * 判断GID是否可用
     *
     * @param gid 分组表示
     * @return gid存在返回true，不存在返回false
     */
    public boolean availableGid(String gid) {
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getGid, gid)
                // TODO 设置用户名
                .eq(GroupDO::getUsername, null);
        GroupDO groupDO = baseMapper.selectOne(queryWrapper);
        return groupDO != null;
    }
}
