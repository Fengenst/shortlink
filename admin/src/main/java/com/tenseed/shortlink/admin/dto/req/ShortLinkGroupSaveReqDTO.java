package com.tenseed.shortlink.admin.dto.req;

import lombok.Data;

/**
 * 短链接分组新增参数
 */
@Data
public class ShortLinkGroupSaveReqDTO {

    /**
     * 分组名称
     */
    private String name;
}
