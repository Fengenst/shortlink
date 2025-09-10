package com.tenseed.shortlink.admin.dto.resp;

import lombok.Data;

/**
 * 短链接分组返回参数
 */
@Data
public class ShortLinkGroupRespDTO {

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 分组名称
     */
    private String name;

    /**
     * 分组排序序号
     */
    private Integer sortOrder;
}
