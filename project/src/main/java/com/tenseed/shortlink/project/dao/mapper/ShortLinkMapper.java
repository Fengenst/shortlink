package com.tenseed.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tenseed.shortlink.project.dao.entity.ShortLinkDO;
import com.tenseed.shortlink.project.dto.biz.ShortLinkStatsIncrementDTO;
import com.tenseed.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.tenseed.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;

/**
 * 短链接持久层
 */
public interface ShortLinkMapper extends BaseMapper<ShortLinkDO> {

    /**
     * 短链接访问统计自增
     */
    void incrementStats(ShortLinkStatsIncrementDTO linkStatsIncrementDTO);

    /**
     * 分页统计短链接
     */
    IPage<ShortLinkDO> pageLink(ShortLinkPageReqDTO requestParam);

    /**
     * 分页统计回收站短链接
     */
    IPage<ShortLinkDO> pageRecycleBinLink(ShortLinkRecycleBinPageReqDTO requestParam);
}
