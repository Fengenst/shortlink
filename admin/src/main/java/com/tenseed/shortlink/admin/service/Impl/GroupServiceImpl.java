package com.tenseed.shortlink.admin.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tenseed.shortlink.admin.dao.entity.GroupDO;
import com.tenseed.shortlink.admin.dao.mapper.GroupMapper;
import com.tenseed.shortlink.admin.service.GroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 短链接分组接口实现层
 */
@Slf4j
@Service
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {
}
