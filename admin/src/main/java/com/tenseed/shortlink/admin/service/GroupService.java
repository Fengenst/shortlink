package com.tenseed.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tenseed.shortlink.admin.dao.entity.GroupDO;
import com.tenseed.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.tenseed.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;

import java.util.List;

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

    /**
     * 查询用户短链接分组集合
     *
     * @return 用户短链接分组集合
     */
    List<ShortLinkGroupRespDTO> listGroup();

    /**
     * 修改短链接分组名称
     *
     * @param requestParam 短链接分组参数
     */
    void updateGroup(ShortLinkGroupUpdateReqDTO requestParam);

    /**
     * 删除短链接分组
     *
     * @param gid 短链接分组标识
     */
    void deleteGroup(String gid);
}
