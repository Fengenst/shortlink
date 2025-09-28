package com.tenseed.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tenseed.shortlink.project.dao.entity.LinkDeviceStatsDO;
import com.tenseed.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.HashMap;
import java.util.List;

public interface LinkDeviceStatsMapper extends BaseMapper<LinkDeviceStatsDO> {

    /**
     * 记录访问设备监控数据
     */
    @Insert("""
            INSERT INTO t_link_device_stats \
            (full_short_url, gid, date, cnt, device, create_time, update_time, del_flag) \
            VALUES \
            (#{fullShortUrl}, #{gid}, #{date}, #{cnt}, #{device}, NOW(), NOW(), 0) \
            ON DUPLICATE KEY UPDATE \
            cnt = cnt +  #{cnt};
            """)
    void shortLinkDeviceState(LinkDeviceStatsDO linkDeviceStatsDO);

    /**
     * 根据短链接获取指定日期内设备监控数据
     */
    @Select("""
            SELECT
                device,
                SUM(cnt) AS count
            FROM
                t_link_device_stats
            WHERE
                full_short_url = #{fullShortUrl}
                AND gid = #{gid}
                AND date BETWEEN #{startDate} AND #{endDate}
                AND del_flag = 0
            GROUP BY
                full_short_url, gid, device;""")
    List<HashMap<String, Object>> listDeviceStatsByShortLink(ShortLinkStatsReqDTO requestParam);
}
