package com.tenseed.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tenseed.shortlink.project.dao.entity.LinkAccessStatsDO;
import com.tenseed.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 短链接基础访问监控持久层
 */
public interface LinkAccessStatsMapper extends BaseMapper<LinkAccessStatsDO> {

    /**
     * 记录基础访问监控数据
     */
    @Insert("""
            INSERT INTO t_link_access_stats
                (full_short_url, gid, date, pv, uv, uip, hour, weekday, create_time, update_time, del_flag)
            VALUES
                (#{fullShortUrl}, #{gid}, #{date}, #{pv}, #{uv}, #{uip}, #{hour}, #{weekday}, NOW(), NOW(), 0)
            ON DUPLICATE KEY UPDATE
                pv  = pv + #{pv}, uv  = uv + #{uv}, uip = uip + #{uip};""")
    void shortLinkStats(LinkAccessStatsDO linkAccessStatsDO);

    /**
     * 根据短链接获取指定日期内的基础监控数据
     */
    @Select("""
            SELECT
                date,
                SUM(pv) AS pv,
                SUM(uv) AS uv,
                SUM(uip) AS uip
            FROM
                t_link_access_stats
            WHERE
                full_short_url = #{fullShortUrl}
                AND gid = #{gid}
                AND del_flag = 0
                AND date BETWEEN #{startDate} AND #{endDate}
            GROUP BY
                full_short_url, gid, date;""")
    List<LinkAccessStatsDO> listStatsByShortLink(ShortLinkStatsReqDTO requestParam);

    /**
     * 根据短链接获取指定日期内小时基础监控数据
     */
    @Select("""
            SELECT
                hour,
                SUM(pv) AS pv,
                SUM(uv) AS uv,
                SUM(uip) AS uip
            FROM
                t_link_access_stats
            WHERE
                full_short_url = #{fullShortUrl}
                AND gid = #{gid}
                AND del_flag = 0
                AND date BETWEEN #{startDate} AND #{endDate}
            GROUP BY
                full_short_url, gid, hour;""")
    List<LinkAccessStatsDO> listHourStatsByShortLink(ShortLinkStatsReqDTO requestParam);

    /**
     * 根据短链接获取指定日期内小时基础监控数据
     */
    @Select("""
            SELECT
                weekday,
                SUM(pv) AS pv,
                SUM(uv) AS uv,
                SUM(uip) AS uip
            FROM
                t_link_access_stats
            WHERE
                full_short_url = #{fullShortUrl}
                AND gid = #{gid}
                AND date BETWEEN #{startDate} AND #{endDate}
                AND del_flag = 0
            GROUP BY
                full_short_url, gid, weekday;""")
    List<LinkAccessStatsDO> listWeekdayStatsByShortLink(ShortLinkStatsReqDTO requestParam);
}
