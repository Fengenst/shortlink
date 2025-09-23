package com.tenseed.shortlink.admin.service.Impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tenseed.shortlink.admin.common.biz.user.UserContext;
import com.tenseed.shortlink.admin.common.convention.exception.ServiceException;
import com.tenseed.shortlink.admin.common.convention.result.Result;
import com.tenseed.shortlink.admin.dao.entity.GroupDO;
import com.tenseed.shortlink.admin.dao.mapper.GroupMapper;
import com.tenseed.shortlink.admin.remote.ShortLinkRemoteService;
import com.tenseed.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.tenseed.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import com.tenseed.shortlink.admin.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {

    private final GroupMapper groupMapper;

    // TODO 后续重构为 SpringCloud Feign 调用
    ShortLinkRemoteService shortLinkRemoteService = new ShortLinkRemoteService() {
    };

    @Override
    public Result<IPage<ShortLinkPageRespDTO>> pageRecycleBinShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        // 构建查询条件：当前用户的未删除的短链接分组
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getDelFlag, 0);

        // 根据条件查询短链接分组列表
        List<GroupDO> groupDOList = groupMapper.selectList(queryWrapper);

        // 分组列表为空，则抛出异常
        if (CollUtil.isEmpty(groupDOList)) {
            throw new ServiceException("用户无分组信息");
        }

        // 将属于当前用户的短链接分组列表的 gidList 赋值给 requestParam 的 gidList 属性
        requestParam.setGidList(groupDOList.stream().map(GroupDO::getGid).toList());
        return shortLinkRemoteService.pageRecycleBinShortLink(requestParam);
    }
}
