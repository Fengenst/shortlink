package com.tenseed.shortlink.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tenseed.shortlink.admin.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短链接分组实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_group")
public class GroupDO extends BaseDO {

    /**
     * id
     */
    private Long id;

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 分组名称
     */
    private String name;

    /**
     * 创建该分组的用户名
     */
    private String username;

    /**
     * 分组排序序号
     */
    private Integer sortOrder;
}