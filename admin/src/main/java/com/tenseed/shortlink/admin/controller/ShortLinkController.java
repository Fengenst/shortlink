package com.tenseed.shortlink.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tenseed.shortlink.admin.common.convention.result.Result;
import com.tenseed.shortlink.admin.common.convention.result.Results;
import com.tenseed.shortlink.admin.remote.ShortLinkRemoteService;
import com.tenseed.shortlink.admin.remote.dto.req.ShortLinkCreateReqDTO;
import com.tenseed.shortlink.admin.remote.dto.req.ShortLinkPageReqDTO;
import com.tenseed.shortlink.admin.remote.dto.req.ShortLinkUpdateReqDTO;
import com.tenseed.shortlink.admin.remote.dto.resp.ShortLinkCreateRespDTO;
import com.tenseed.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import org.springframework.web.bind.annotation.*;

/**
 * 短链接后管控制层
 */
@RestController
public class ShortLinkController {

    // TODO 后续重构为 SpringCloud Feign 调用
    ShortLinkRemoteService shortLinkRemoteService = new ShortLinkRemoteService() {
    };

    /**
     * 短链接创建
     */
    @PostMapping("/api/shortlink/admin/v1/create")
    public Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam) {
        return shortLinkRemoteService.createShortLink(requestParam);
    }

    /**
     * 短链接修改
     */
    @PostMapping("/api/shortlink/admin/v1/update")
    public Result<Void> updateShortLink(@RequestBody ShortLinkUpdateReqDTO requestParam) {
        shortLinkRemoteService.updateShortLink(requestParam);
        return Results.success();
    }

    /**
     * 短链接分页查询
     */
    @GetMapping("/api/shortlink/admin/v1/page")
    public Result<IPage<ShortLinkPageRespDTO>> pageShortLink(ShortLinkPageReqDTO requestParam) {
        return shortLinkRemoteService.pageShortLink(requestParam);
    }
}
