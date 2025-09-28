package com.tenseed.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tenseed.shortlink.project.dao.entity.LinkBrowserStatsDO;
import com.tenseed.shortlink.project.dao.entity.LinkOsStatsDO;
import com.tenseed.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.HashMap;
import java.util.List;

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

    /**
     * 根据短链接获取指定日期内浏览器监控数据
     */
    @Select("""
            SELECT
                browser,
                SUM(cnt) AS count
            FROM
                t_link_browser_stats
            WHERE
                full_short_url = #{fullShortUrl}
                AND gid = #{gid}
                AND del_flag = 0
                AND date BETWEEN #{startDate} AND #{endDate}
            GROUP BY
                full_short_url, gid, browser;""")
    List<HashMap<String, Object>> listBrowserStatsByShortLink(ShortLinkStatsReqDTO requestParam);
}
