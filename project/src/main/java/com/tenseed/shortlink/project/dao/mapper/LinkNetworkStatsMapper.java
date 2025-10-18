package com.tenseed.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tenseed.shortlink.project.dao.entity.LinkNetworkStatsDO;
import com.tenseed.shortlink.project.dto.req.ShortLinkGroupStatsReqDTO;
import com.tenseed.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Param;

import java.util.HashMap;
import java.util.List;

public interface LinkNetworkStatsMapper extends BaseMapper<LinkNetworkStatsDO> {

    /**
     * 记录访问设备监控数据
     */
    void shortLinkNetworkState(@Param("linkNetworkStats") LinkNetworkStatsDO linkNetworkStatsDO);

    /**
     * 根据短链接获取指定日期内网络监控数据
     */
    List<HashMap<String, Object>> listNetworkStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 根据分组获取指定日期内访问网络监控数据
     */
    List<LinkNetworkStatsDO> listNetworkStatsByGroup(@Param("param") ShortLinkGroupStatsReqDTO requestParam);
}
