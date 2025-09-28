package com.tenseed.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tenseed.shortlink.project.dao.entity.LinkLocaleStatsDO;
import com.tenseed.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 地区访问统计持久层
 */
public interface LinkLocaleStatsMapper extends BaseMapper<LinkLocaleStatsDO> {

    /**
     * 记录访问地区监控数据
     */
    @Insert("""
            INSERT INTO t_link_locale_stats \
            (full_short_url, gid, date, cnt, province, city, adcode, country, create_time, update_time, del_flag)
            VALUES \
            (#{fullShortUrl}, #{gid}, #{date}, #{cnt}, #{province}, #{city}, #{adcode}, #{country}, NOW(), NOW(), 0)
            ON DUPLICATE KEY UPDATE \
            cnt  = cnt + #{cnt};""")
    void shortLinkLocaleStats(LinkLocaleStatsDO linkLocaleStatsDO);

    /**
     * 根据短链接获取指定日期内地区监控数据
     */
    @Select("""
            SELECT
                province,
                SUM(cnt) AS cnt
            FROM
                t_link_locale_stats
            WHERE
                full_short_url = #{fullShortUrl}
                AND gid = #{gid}
                AND del_flag = 0
                AND date BETWEEN #{startDate} AND #{endDate}
            GROUP BY
                full_short_url, gid, province;""")
    List<LinkLocaleStatsDO> listLocaleByShortLink(ShortLinkStatsReqDTO requestParam);
}
