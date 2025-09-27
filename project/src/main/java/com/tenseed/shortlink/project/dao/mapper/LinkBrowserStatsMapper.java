package com.tenseed.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tenseed.shortlink.project.dao.entity.LinkBrowserStatsDO;
import com.tenseed.shortlink.project.dao.entity.LinkOsStatsDO;
import org.apache.ibatis.annotations.Insert;

/**
 * 浏览器访问统计持久层
 */
public interface LinkBrowserStatsMapper extends BaseMapper<LinkOsStatsDO> {

    /**
     * 记录访问系统监控数据
     */
    @Insert("""
            INSERT INTO t_link_browser_stats \
            (full_short_url, gid, date, cnt, browser, create_time, update_time, del_flag)
            VALUES \
            (#{fullShortUrl}, #{gid}, #{date}, #{cnt}, #{browser}, NOW(), NOW(), 0)
            ON DUPLICATE KEY UPDATE \
            cnt  = cnt + #{cnt};""")
    void shortLinkBrowserStats(LinkBrowserStatsDO linkBrowserStatsDO);
}
