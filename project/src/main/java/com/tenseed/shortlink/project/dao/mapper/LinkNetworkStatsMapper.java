package com.tenseed.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tenseed.shortlink.project.dao.entity.LinkNetworkStatsDO;
import com.tenseed.shortlink.project.dto.req.ShortLinkGroupStatsReqDTO;
import com.tenseed.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.HashMap;
import java.util.List;

public interface LinkNetworkStatsMapper extends BaseMapper<LinkNetworkStatsDO> {

    /**
     * 记录访问设备监控数据
     */
    @Insert("""
            INSERT INTO t_link_network_stats \
            (full_short_url, gid, date, cnt, network, create_time, update_time, del_flag) \
            VALUES \
            (#{fullShortUrl}, #{gid}, #{date}, #{cnt}, #{network}, NOW(), NOW(), 0) \
            ON DUPLICATE KEY UPDATE \
            cnt = cnt +  #{cnt};
            """)
    void shortLinkNetworkState(LinkNetworkStatsDO linkNetworkStatsDO);

    /**
     * 根据短链接获取指定日期内网络监控数据
     */
    @Select("""
            SELECT
                network,
                SUM(cnt) AS count
            FROM
                t_link_network_stats
            WHERE
                full_short_url = #{fullShortUrl}
                AND gid = #{gid}
                AND date BETWEEN #{startDate} AND #{endDate}
                AND del_flag = 0
            GROUP BY
                full_short_url, gid, network;""")
    List<HashMap<String, Object>> listNetworkStatsByShortLink(ShortLinkStatsReqDTO requestParam);

    /**
     * 根据分组获取指定日期内访问网络监控数据
     */
    @Select("SELECT " +
            "    network, " +
            "    SUM(cnt) AS cnt " +
            "FROM " +
            "    t_link_network_stats " +
            "WHERE " +
            "    gid = #{param.gid} " +
            "    AND date BETWEEN #{param.startDate} and #{param.endDate} " +
            "GROUP BY " +
            "    gid, network;")
    List<LinkNetworkStatsDO> listNetworkStatsByGroup(@Param("param") ShortLinkGroupStatsReqDTO requestParam);
}
