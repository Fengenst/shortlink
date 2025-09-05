package com.tenseed.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tenseed.shortlink.admin.dao.entity.GroupDO;

/**
 * 短链接分组接口层
 */
public interface GroupService extends IService<GroupDO> {

    /**
     * 新增短链接分组
     *
     * @param name 分组名称
     */
    void save(String name);
}
