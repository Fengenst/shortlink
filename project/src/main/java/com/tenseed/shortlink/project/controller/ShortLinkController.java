package com.tenseed.shortlink.project.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tenseed.shortlink.project.common.convention.result.Result;
import com.tenseed.shortlink.project.common.convention.result.Results;
import com.tenseed.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.tenseed.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.tenseed.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.tenseed.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.tenseed.shortlink.project.service.ShortLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短链接控制层
 */
@RestController
@RequiredArgsConstructor
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    /**
     * 短链接创建
     */
    @PostMapping("/api/shortlink/v1/create")
    public Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam) {
        ShortLinkCreateRespDTO shortLinkCreateRespDTO = shortLinkService.createShortLink(requestParam);
        return Results.success(shortLinkCreateRespDTO);
    }

    /**
     * 短链接分页查询
     */
    @GetMapping("/api/shortlink/v1/page")
    public Result<IPage<ShortLinkPageRespDTO>> pageShortLink(@RequestBody ShortLinkPageReqDTO requestParam) {
        return Results.success(shortLinkService.pageShortLink(requestParam));
    }
}
