package com.tenseed.shortlink.admin.controller;

import com.tenseed.shortlink.admin.common.convention.result.Result;
import com.tenseed.shortlink.admin.common.convention.result.Results;
import com.tenseed.shortlink.admin.dto.req.ShortLinkGroupSaveReqDTO;
import com.tenseed.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import com.tenseed.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.tenseed.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;
import com.tenseed.shortlink.admin.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 短链接分组控制层
 */
@RestController
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    /**
     * 新增短链接分组
     */
    @PostMapping("/api/shortlink/admin/v1/group")
    public Result<Void> saveGroup(@RequestBody ShortLinkGroupSaveReqDTO requestParam) {
        groupService.saveGroup(requestParam.getName());
        return Results.success();
    }

    /**
     * 查询短链接分组集合
     */
    @GetMapping("/api/shortlink/admin/v1/group")
    public Result<List<ShortLinkGroupRespDTO>> listGroup() {
        return Results.success(groupService.listGroup());
    }

    /**
     * 修改短链接分组名称
     */
    @PutMapping("/api/shortlink/admin/v1/group")
    public Result<Void> updateGroup(@RequestBody ShortLinkGroupUpdateReqDTO requestParam) {
        groupService.updateGroup(requestParam);
        return Results.success();
    }

    /**
     * 删除短链接分组
     */
    @DeleteMapping("/api/shortlink/admin/v1/group")
    public Result<Void> deleteGroup(@RequestParam("gid") String gid) {
        groupService.deleteGroup(gid);
        return Results.success();
    }

    /**
     * 短链接分组排序（修改当前用户的短链接分组的sortOrder）
     */
    @PostMapping("/api/shortlink/admin/v1/group/sort")
    public Result<Void> sortGroup(@RequestBody List<ShortLinkGroupSortReqDTO> requestParam) {
        groupService.sortGroup(requestParam);
        return Results.success();
    }


}
