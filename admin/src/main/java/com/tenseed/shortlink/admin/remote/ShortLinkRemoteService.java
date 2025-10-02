package com.tenseed.shortlink.admin.remote;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tenseed.shortlink.admin.common.convention.result.Result;
import com.tenseed.shortlink.admin.dto.req.ShortLinkGroupStatsAccessRecordReqDTO;
import com.tenseed.shortlink.admin.dto.req.ShortLinkGroupStatsReqDTO;
import com.tenseed.shortlink.admin.remote.dto.req.*;
import com.tenseed.shortlink.admin.remote.dto.resp.*;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 短链接中台远程调用服务
 */
public interface ShortLinkRemoteService {

    /**
     * 创建短链接
     *
     * @param requestParam 创建短链接请求参数
     * @return 短链接创建响应
     */
    default Result<ShortLinkCreateRespDTO> createShortLink(ShortLinkCreateReqDTO requestParam) {
        String resultBodyStr = HttpUtil.post("http://127.0.0.1:8004/api/shortlink/v1/create", JSON.toJSONString(requestParam));
        return JSON.parseObject(resultBodyStr, new TypeReference<>() {
        });
    }

    /**
     * 修改短链接
     *
     * @param requestParam 修改短链接请求参数
     */
    default void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        HttpUtil.post("http://127.0.0.1:8004/api/shortlink/v1/update", JSON.toJSONString(requestParam));
    }

    /**
     * 分页查询短链接
     *
     * @param requestParam 分页短链接请求参数
     * @return 分页短链接请求响应
     */
    default Result<IPage<ShortLinkPageRespDTO>> pageShortLink(ShortLinkPageReqDTO requestParam) {
        HashMap<String, Object> requestMap = new HashMap<>();
        requestMap.put("gid", requestParam.getGid());
        requestMap.put("orderTag", requestParam.getOrderTag());
        requestMap.put("current", requestParam.getCurrent());
        requestMap.put("size", requestParam.getSize());
        String resultPageStr = HttpUtil.get("http://127.0.0.1:8004/api/shortlink/v1/page", requestMap);
        return JSON.parseObject(resultPageStr, new TypeReference<>() {
        });
    }

    /**
     * 查询短链接分组内短链接数量
     *
     * @param requestParam 查询短链接分组内短链接数量请求参数
     * @return 查询短链接分组内短链接数量响应
     */
    default Result<List<ShortLinkGroupCountQueryRespDTO>> listGroupShortLinkCount(List<String> requestParam) {
        HashMap<String, Object> requestMap = new HashMap<>();
        requestMap.put("requestParam", requestParam);
        String resultPageStr = HttpUtil.get("http://127.0.0.1:8004/api/shortlink/v1/count", requestMap);
        return JSON.parseObject(resultPageStr, new TypeReference<>() {
        });
    }


    /**
     * 根据 URL 获取标题
     *
     * @param url 目标网站地址
     * @return 网站标题
     */
    default Result<String> getTitleByUrl(@RequestParam("url") String url) {
        String resultStr = HttpUtil.get("http://127.0.0.1:8004/api/shortlink/v1/title?url=" + url);
        return JSON.parseObject(resultStr, new TypeReference<>() {
        });
    }

    /**
     * 回收站保存
     *
     * @param requestParam 回收站保存请求实体
     */
    default void saveRecycleBin(RecycleBinSaveReqDTO requestParam) {
        HttpUtil.post("http://127.0.0.1:8004/api/shortlink/v1/recycle-bin/save", JSON.toJSONString(requestParam));
    }

    /**
     * 分页查询处于回收站中的短链接
     *
     * @param requestParam 分页短链接请求参数
     * @return 分页短链接请求响应
     */
    default Result<IPage<ShortLinkPageRespDTO>> pageRecycleBinShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        HashMap<String, Object> requestMap = new HashMap<>();
        requestMap.put("gidList", requestParam.getGidList());
        requestMap.put("current", requestParam.getCurrent());
        requestMap.put("size", requestParam.getSize());
        String resultPageStr = HttpUtil.get("http://127.0.0.1:8004/api/shortlink/v1/recycle-bin/page", requestMap);
        return JSON.parseObject(resultPageStr, new TypeReference<>() {
        });
    }

    /**
     * 恢复回收站中的短链接
     *
     * @param requestParam 回收站恢复请求实体
     */
    default void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam) {
        HttpUtil.post("http://127.0.0.1:8004/api/shortlink/v1/recycle-bin/recover", JSON.toJSONString(requestParam));
    }

    /**
     * 删除处于回收站的短链接
     *
     * @param requestParam 回收站删除请求实体
     */
    default void removeRecycleBin(RecycleBinRemoveReqDTO requestParam) {
        HttpUtil.post("http://127.0.0.1:8004/api/shortlink/v1/recycle-bin/remove", JSON.toJSONString(requestParam));
    }

    /**
     * 访问单个短链接指定时间内监控数据
     *
     * @param requestParam 访问短链接监控请求参数
     * @return 短链接监控信息
     */
    default Result<ShortLinkStatsRespDTO> oneShortLinkStats(ShortLinkStatsReqDTO requestParam) {
        String resultBodyStr = HttpUtil.get("http://127.0.0.1:8004/api/short-link/v1/stats", BeanUtil.beanToMap(requestParam));
        return JSON.parseObject(resultBodyStr, new TypeReference<>() {
        });
    }

    /**
     * 访问分组短链接指定时间内监控数据
     *
     * @param requestParam 访分组问短链接监控请求参数
     * @return 分组短链接监控信息
     */
    default Result<ShortLinkStatsRespDTO> groupShortLinkStats(ShortLinkGroupStatsReqDTO requestParam) {
        String resultBodyStr = HttpUtil.get("http://127.0.0.1:8004/api/short-link/v1/stats/group", BeanUtil.beanToMap(requestParam));
        return JSON.parseObject(resultBodyStr, new TypeReference<>() {
        });
    }

    /**
     * 访问单个短链接指定时间内监控访问记录数据
     *
     * @param requestParam 访问短链接监控访问记录请求参数
     * @return 短链接监控访问记录信息
     */
    default Result<IPage<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(ShortLinkStatsAccessRecordReqDTO requestParam) {
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(requestParam, false, true);
        stringObjectMap.remove("orders");
        stringObjectMap.remove("records");
        String resultBodyStr = HttpUtil.get("http://127.0.0.1:8004/api/short-link/v1/stats/access-record", stringObjectMap);
        return JSON.parseObject(resultBodyStr, new TypeReference<>() {
        });
    }

    /**
     * 访问分组短链接指定时间内监控访问记录数据
     *
     * @param requestParam 访问分组短链接监控访问记录请求参数
     * @return 分组短链接监控访问记录信息
     */
    default Result<IPage<ShortLinkStatsAccessRecordRespDTO>> groupShortLinkStatsAccessRecord(ShortLinkGroupStatsAccessRecordReqDTO requestParam) {
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(requestParam, false, true);
        stringObjectMap.remove("orders");
        stringObjectMap.remove("records");
        String resultBodyStr = HttpUtil.get("http://127.0.0.1:8004/api/short-link/v1/stats/access-record/group", stringObjectMap);
        return JSON.parseObject(resultBodyStr, new TypeReference<>() {
        });
    }
}