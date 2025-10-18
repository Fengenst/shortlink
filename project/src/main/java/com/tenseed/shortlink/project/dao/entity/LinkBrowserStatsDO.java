package com.tenseed.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tenseed.shortlink.project.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 浏览器访问统计实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_link_browser_stats")
public class LinkBrowserStatsDO extends BaseDO {

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
     * 访问量
     */
    private Integer cnt;

    /**
     * 浏览器
     */
    private String browser;
}