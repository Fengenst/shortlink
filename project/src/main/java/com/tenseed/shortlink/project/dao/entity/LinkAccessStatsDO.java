package com.tenseed.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tenseed.shortlink.project.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 短链接基础访问监控实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_link_access_stats")
public class LinkAccessStatsDO extends BaseDO {

    /**
     * ID
     */
    private Long id;

    /**
     * 完整短链接
     */
    private String fullShortUrl;

    /**
     * 日期
     */
    private LocalDate date;

    /**
     * 页面浏览量
     */
    private Integer pv;

    /**
     * 独立访客数
     */
    private Integer uv;

    /**
     * 独立IP数
     */
    private Integer uip;

    /**
     * 小时
     */
    private Integer hour;

    /**
     * 星期（1-7）
     */
    private Integer weekday;
}