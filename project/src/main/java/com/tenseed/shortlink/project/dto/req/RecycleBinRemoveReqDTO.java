package com.tenseed.shortlink.project.dto.req;

import lombok.Data;

/**
 * 回收站删除请求实体
 */
@Data
public class RecycleBinRemoveReqDTO {

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 完整短链接
     */
    private String fullShortUrl;
}
