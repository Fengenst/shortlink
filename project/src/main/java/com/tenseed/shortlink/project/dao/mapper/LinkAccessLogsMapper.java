package com.tenseed.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tenseed.shortlink.project.dao.entity.LinkAccessLogsDO;
import com.tenseed.shortlink.project.dao.entity.LinkAccessStatsDO;
import com.tenseed.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Select;

import java.util.HashMap;
import java.util.List;

/**
 * 访问日志监控持久层
 */
public interface LinkAccessLogsMapper extends BaseMapper<LinkAccessLogsDO> {

    /**
     * 根据短链接获取指定日期内PV、UV、UIP数据
     */
    @Select("""
            SELECT
                COUNT(*) AS pv,
                COUNT(DISTINCT user) AS uv,
                COUNT(DISTINCT ip) AS uip
            FROM
                t_link_access_logs t1
            WHERE
                full_short_url = #{fullShortUrl}
                AND gid = #{gid}
                AND del_flag = 0
                AND create_time BETWEEN #{startDate} AND #{endDate}
            GROUP BY
                full_short_url, gid;""")
    LinkAccessStatsDO queryPvUvUipByShortLink(ShortLinkStatsReqDTO requestParam);

    /**
     * 根据短链接获取指定日期内的高频访问 IP 数据
     */
    @Select("""
            SELECT
                ip,
                COUNT(ip) AS count
            FROM
                t_link_access_logs
            WHERE
                full_short_url = #{fullShortUrl}
                AND gid = #{gid}
                AND del_flag = 0
                AND create_time BETWEEN #{startDate} AND #{endDate}
            GROUP BY
                full_short_url, gid, ip
            ORDER BY
                count DESC
            LIMIT 5;""")
    List<HashMap<String, Object>> listTopIpByShortLink(ShortLinkStatsReqDTO requestParam);

    /**
     * 根据短链接获取指定日期内新旧访客数据
     */
    @Select("""
            SELECT
                SUM(old_user) AS oldUserCnt,
                SUM(new_user) AS newUserCnt
            FROM (
                SELECT
                    CASE
                        WHEN COUNT(DISTINCT DATE(create_time)) > 1
                        THEN 1
                        ELSE 0
                    END AS old_user,
                    CASE
                        WHEN COUNT(DISTINCT DATE(create_time)) = 1
                        AND MAX(create_time) >= #{startDate}
                        AND MAX(create_time) <= #{endDate}
                        THEN 1
                        ELSE 0
                    END AS new_user
                FROM
                    t_link_access_logs
                WHERE
                    full_short_url = #{fullShortUrl}
                    AND gid = #{gid}
                    AND del_flag = 0
                GROUP BY
                    user
            ) AS user_counts;""")
    HashMap<String, Object> findUvTypeCntByShortLink(ShortLinkStatsReqDTO requestParam);
}
