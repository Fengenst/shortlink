package com.tenseed.shortlink.project.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tenseed.shortlink.project.dao.entity.ShortLinkDO;
import com.tenseed.shortlink.project.dto.req.RecycleBinSaveReqDTO;

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
}
