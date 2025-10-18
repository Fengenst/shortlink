package com.tenseed.shortlink.project.dto.biz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短链接统计增量 DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShortLinkStatsIncrementDTO {

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 完整的短链接
     */
    private String fullShortUrl;

    /**
     * pv增量
     */
    private Integer totalPv;

    /**
     * uv增量
     */
    private Integer totalUv;

    /**
     * uip增量
     */
    private Integer totalUip;
}