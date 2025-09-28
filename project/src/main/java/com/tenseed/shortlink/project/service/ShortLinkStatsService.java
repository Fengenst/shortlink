package com.tenseed.shortlink.project.service;

import com.tenseed.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import com.tenseed.shortlink.project.dto.resp.ShortLinkStatsRespDTO;

public interface ShortLinkStatsService {

    /**
     * 获取单个短链接监控数据
     *
     * @param requestParam 获取短链接监控数据入参
     * @return 短链接监控数据
     */
    ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam);
}
