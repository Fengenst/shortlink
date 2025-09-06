package com.tenseed.shortlink.admin.controller;

import com.tenseed.shortlink.admin.common.convention.result.Result;
import com.tenseed.shortlink.admin.common.convention.result.Results;
import com.tenseed.shortlink.admin.dto.req.ShortLinkGroupSaveReqDTO;
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
    @PostMapping("/api/shortlink/v1/group")
    public Result<Void> save(@RequestBody ShortLinkGroupSaveReqDTO requestParam) {
        groupService.save(requestParam.getName());
        return Results.success();
    }

    /**
     * 查询短链接分组集合
     */
    @GetMapping("/api/shortlink/v1/group")
    public Result<List<ShortLinkGroupRespDTO>> listGroup() {
        return Results.success(groupService.listGroup());
    }

    /**
     * 修改短链接分组名称
     */
    @PutMapping("/api/shortlink/v1/group")
    public Result<Void> update(@RequestBody ShortLinkGroupUpdateReqDTO requestParam) {
        groupService.update(requestParam);
        return Results.success();
    }
}
