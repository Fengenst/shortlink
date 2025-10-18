package com.tenseed.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tenseed.shortlink.project.dao.entity.LinkLocaleStatsDO;
import com.tenseed.shortlink.project.dto.req.ShortLinkGroupStatsReqDTO;
import com.tenseed.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 地区访问统计持久层
 */
public interface LinkLocaleStatsMapper extends BaseMapper<LinkLocaleStatsDO> {

    /**
     * 记录访问地区监控数据
     */
    void shortLinkLocaleStats(@Param("linkLocaleStats") LinkLocaleStatsDO linkLocaleStatsDO);

    /**
     * 根据短链接获取指定日期内地区监控数据
     */
    List<LinkLocaleStatsDO> listLocaleByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 根据分组获取指定日期内地区监控数据
     */
    List<LinkLocaleStatsDO> listLocaleByGroup(@Param("param") ShortLinkGroupStatsReqDTO requestParam);
}
