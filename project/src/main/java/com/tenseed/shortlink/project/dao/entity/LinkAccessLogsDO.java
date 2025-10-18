package com.tenseed.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tenseed.shortlink.project.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 访问日志监控实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_link_access_logs")
public class LinkAccessLogsDO extends BaseDO {

    /**
     * ID
     */
    private Long id;

    /**
     * 完整短链接
     */
    private String fullShortUrl;

    /**
     * 用户信息
     */
    private String user;

    /**
     * IP
     */
    private String ip;

    /**
     * 浏览器
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

    /**
     * 访问网络
     */
    private String network;

    /**
     * 访问设备
     */
    private String device;

    /**
     * 访问地区
     */
    private String locale;
}