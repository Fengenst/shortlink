package com.tenseed.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tenseed.shortlink.project.dao.entity.ShortLinkDO;
import com.tenseed.shortlink.project.dto.req.RecycleBinSaveReqDTO;
import com.tenseed.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.tenseed.shortlink.project.dto.resp.ShortLinkPageRespDTO;

/**
 * 回收站管理接口层
 */
public interface RecycleBinService extends IService<ShortLinkDO> {

    /**
     * 回收站保存
     *
     * @param requestParam 回收站保存请求实体
     */
    void saveRecycleBin(RecycleBinSaveReqDTO requestParam);

    /**
     * 分页查询回收站中的短链接
     *
     * @param requestParam 分页查询短链接请求参数
     * @return 短链接分页查询信息
     */
    IPage<ShortLinkPageRespDTO> pageRecycleBinShortLink(ShortLinkRecycleBinPageReqDTO requestParam);
}
