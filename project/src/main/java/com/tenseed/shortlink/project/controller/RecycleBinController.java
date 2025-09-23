package com.tenseed.shortlink.project.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tenseed.shortlink.project.common.convention.result.Result;
import com.tenseed.shortlink.project.common.convention.result.Results;
import com.tenseed.shortlink.project.dto.req.RecycleBinSaveReqDTO;
import com.tenseed.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.tenseed.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.tenseed.shortlink.project.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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

    /**
     * 对处于回收站的短链接分页查询
     */
    @GetMapping("/api/shortlink/v1/recycle-bin/page")
    public Result<IPage<ShortLinkPageRespDTO>> pageRecycleBinShortLink(ShortLinkPageReqDTO requestParam) {
        return Results.success(recycleBinService.pageRecycleBinShortLink(requestParam));
    }
}
