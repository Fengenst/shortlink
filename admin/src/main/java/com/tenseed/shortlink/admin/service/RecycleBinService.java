package com.tenseed.shortlink.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tenseed.shortlink.admin.common.convention.result.Result;
import com.tenseed.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.tenseed.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;

public interface RecycleBinService {

    /**
     * 分页查询回收站中的短链接
     *
     * @param requestParam 分页查询短链接请求参数
     * @return 短链接分页查询信息
     */
    Result<IPage<ShortLinkPageRespDTO>> pageRecycleBinShortLink(ShortLinkRecycleBinPageReqDTO requestParam);
}
