package com.tenseed.shortlink.project.controller;

import com.tenseed.shortlink.project.common.convention.result.Result;
import com.tenseed.shortlink.project.common.convention.result.Results;
import com.tenseed.shortlink.project.dto.req.RecycleBinSaveReqDTO;
import com.tenseed.shortlink.project.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回收站管理控制层
 */
@RestController
@RequiredArgsConstructor
public class RecycleBinController {

    private final RecycleBinService recycleBinService;

    /**
     * 回收站保存
     */
    @PostMapping("/api/shortlink/v1/recycle-bin/save")
    public Result<Void> saveRecycleBin(@RequestBody RecycleBinSaveReqDTO requestParam) {
        recycleBinService.saveRecycleBin(requestParam);
        return Results.success();
    }
}
