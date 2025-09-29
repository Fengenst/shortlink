package com.tenseed.shortlink.project.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 短链接监控用户类型查询 DTO
 */
@Data
public class ShortLinkUvTypeQueryDTO {

    /**
     * 完整短链接
     */
    private String fullShortUrl;

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 开始日期
     */
    private String startDate;

    /**
     * 结束日期
     */
    private String endDate;

    /**
     * 访问用户列表 (用于 IN 查询)
     */
    private List<String> userAccessLogsList;
}
