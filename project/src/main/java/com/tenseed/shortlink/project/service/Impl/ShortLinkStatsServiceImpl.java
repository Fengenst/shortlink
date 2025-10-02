package com.tenseed.shortlink.project.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tenseed.shortlink.project.dao.entity.*;
import com.tenseed.shortlink.project.dao.mapper.*;
import com.tenseed.shortlink.project.dto.ShortLinkUvTypeQueryDTO;
import com.tenseed.shortlink.project.dto.req.*;
import com.tenseed.shortlink.project.dto.resp.*;
import com.tenseed.shortlink.project.service.ShortLinkStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 短链接监控接口实现层
 */
@Service
@RequiredArgsConstructor
public class ShortLinkStatsServiceImpl implements ShortLinkStatsService {

    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;


    @Override
    public ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam) {
        // 获取每日访问统计数据列表，若为空则直接返回 null
        List<LinkAccessStatsDO> linkAccessStatsDOList = linkAccessStatsMapper.listStatsByShortLink(requestParam);
        if (CollUtil.isEmpty(linkAccessStatsDOList)) {
            return null;
        }

        /*
         * 基础访问数据 (PV, UV, UIP)
         * 1. 调用 linkAccessLogsMapper 查询指定短链接的总 PV、UV 和 UIP 数据。
         */
        LinkAccessStatsDO pvUvUipByShortLink = linkAccessLogsMapper.queryPvUvUipByShortLink(requestParam);

        /*
         * 基础访问详情（按日期）
         * 1. 初始化 `daily` 列表用于存储每日访问详情。
         * 2. 解析请求参数中的起始和结束日期。
         * 3. 生成指定日期范围内的所有日期字符串列表 (`rangeDates`)。
         * 4. 遍历 `rangeDates` 中的每一个日期。
         * 5. 尝试从每日统计数据列表中找到匹配当前日期的记录。
         * 6. 如果找到记录，使用该记录的 PV、UV、UIP 和日期构建一个响应对象，并添加到 `daily` 列表。
         * 7. 如果未找到记录，使用 PV=0, UV=0, UIP=0 和当前日期构建一个响应对象，并添加到 `daily` 列表。
         */
        List<ShortLinkStatsAccessDailyRespDTO> daily = new ArrayList<>();
        List<String> rangeDates = DateUtil.rangeToList(
                        DateUtil.parse(requestParam.getStartDate()),
                        DateUtil.parse(requestParam.getEndDate()),
                        DateField.DAY_OF_MONTH
                )
                .stream()
                .map(DateUtil::formatDate)
                .toList();
        rangeDates.forEach(each -> {
            linkAccessStatsDOList.stream()
                    .filter(item -> Objects.equals(each, item.getDate().toString()))
                    .findFirst()
                    .ifPresentOrElse(item -> {
                        ShortLinkStatsAccessDailyRespDTO accessDailyRespDTO = ShortLinkStatsAccessDailyRespDTO.builder()
                                .date(each)
                                .pv(item.getPv())
                                .uv(item.getUv())
                                .uip(item.getUip())
                                .build();
                        daily.add(accessDailyRespDTO);
                    }, () -> {
                        ShortLinkStatsAccessDailyRespDTO accessDailyRespDTO = ShortLinkStatsAccessDailyRespDTO.builder()
                                .date(each)
                                .pv(0)
                                .uv(0)
                                .uip(0)
                                .build();
                        daily.add(accessDailyRespDTO);
                    });
        });


        /*
         * 地区访问详情（仅国内）
         * 1. 初始化 `localeCnStats` 列表用于存储地区访问详情。
         * 2. 调用 linkLocaleStatsMapper 获取短链接的地区统计数据列表。
         * 3. 累加地区统计数据列表中所有地区的 `cnt`（访问次数）得到总访问量 (`localeCnSum`)。
         * 4. 遍历地区统计数据列表。
         * 5. 计算当前地区的访问量占总访问量的比例 (`ratio`)。
         * 6. 将比例四舍五入保留两位小数 (`actualRatio`)。
         * 7. 使用访问量 (`cnt`)、地区名称 (`province`) 和占比 (`actualRatio`) 构建响应对象，并添加到 `localeCnStats` 列表。
         */
        List<ShortLinkStatsLocaleCNRespDTO> localeCnStats = new ArrayList<>();
        List<LinkLocaleStatsDO> linkLocaleStatsDOList = linkLocaleStatsMapper.listLocaleByShortLink(requestParam);
        int localeCnSum = linkLocaleStatsDOList.stream()
                .mapToInt(LinkLocaleStatsDO::getCnt)
                .sum();
        linkLocaleStatsDOList.forEach(each -> {
            double ratio = (double) each.getCnt() / localeCnSum;
            double actualRadio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsLocaleCNRespDTO localeCNRespDTO = ShortLinkStatsLocaleCNRespDTO.builder()
                    .cnt(each.getCnt())
                    .locale(each.getProvince())
                    .ratio(actualRadio)
                    .build();
            localeCnStats.add(localeCNRespDTO);
        });

        /*
         * 小时访问详情 (0-23小时)
         * 1. 初始化 `hourStats` 列表用于存储每小时访问量。
         * 2. 调用 linkAccessStatsMapper 获取短链接的按小时统计数据列表。
         * 3. 循环从 0 到 23（表示小时）。
         * 4. 尝试从小时统计数据列表中找到匹配当前小时的记录。
         * 5. 如果找到，获取其 PV 值。
         * 6. 如果未找到，PV 值为 0。
         * 7. 将该小时的访问量添加到 `hourStats` 列表。
         */
        List<Integer> hourStats = new ArrayList<>();
        List<LinkAccessStatsDO> linkHourAccessStatsDOList = linkAccessStatsMapper.listHourStatsByShortLink(requestParam);
        for (int i = 0; i < 24; i++) {
            AtomicInteger hour = new AtomicInteger(i);
            Integer hourCnt = linkHourAccessStatsDOList.stream()
                    .filter(each -> Objects.equals(each.getHour(), hour.get()))
                    .findFirst()
                    .map(LinkAccessStatsDO::getPv)
                    .orElse(0);
            hourStats.add(hourCnt);
        }

        /*
         * 高频访问IP详情
         * 1. 初始化 `topIpStats` 列表用于存储高频IP详情。
         * 2. 调用 linkAccessLogsMapper 获取高频访问 IP 及其访问次数列表。
         * 3. 遍历高频 IP 列表。
         * 4. 从 Map 中提取 IP 地址 (`ip`) 和访问次数 (`count`)。
         * 5. 使用 IP 和访问次数构建响应对象，并添加到 `topIpStats` 列表。
         */
        List<ShortLinkStatsTopIpRespDTO> topIpStats = new ArrayList<>();
        List<HashMap<String, Object>> linkTopIpCntStatsList = linkAccessLogsMapper.listTopIpByShortLink(requestParam);
        linkTopIpCntStatsList.forEach(each -> {
            ShortLinkStatsTopIpRespDTO statsTopIpRespDTO = ShortLinkStatsTopIpRespDTO.builder()
                    .ip(each.get("ip").toString())
                    .cnt(Integer.parseInt(each.get("count").toString()))
                    .build();
            topIpStats.add(statsTopIpRespDTO);
        });

        /*
         * 一周访问详情 (星期一到星期日)
         * 1. 初始化 `weekdayStats` 列表用于存储星期访问量。
         * 2. 调用 linkAccessStatsMapper 获取短链接的按星期统计数据列表。
         * 3. 循环从 1 到 7（表示星期几）。
         * 4. 尝试从星期统计数据列表中找到匹配当前星期的记录。
         * 5. 如果找到，获取其 PV 值。
         * 6. 如果未找到，PV 值为 0。
         * 7. 将该星期的访问量添加到 `weekdayStats` 列表。
         */
        List<Integer> weekdayStats = new ArrayList<>();
        List<LinkAccessStatsDO> linkWeekdayAccessStatsList = linkAccessStatsMapper.listWeekdayStatsByShortLink(requestParam);
        for (int i = 1; i < 8; i++) {
            AtomicInteger weekday = new AtomicInteger(i);
            Integer weekdayCnt = linkWeekdayAccessStatsList.stream()
                    .filter(each -> Objects.equals(each.getWeekday(), weekday.get()))
                    .findFirst()
                    .map(LinkAccessStatsDO::getPv)
                    .orElse(0);
            weekdayStats.add(weekdayCnt);
        }

        /*
         * 浏览器访问详情
         * 1. 初始化 `browserStats` 列表用于存储浏览器详情。
         * 2. 调用 linkBrowserStatsMapper 获取浏览器统计数据列表。
         * 3. 累加浏览器统计数据列表中所有浏览器的 `count` 得到总访问量 (`browserSum`)。
         * 4. 遍历浏览器统计数据列表。
         * 5. 计算当前浏览器的访问量占总访问量的比例 (`ratio`)。
         * 6. 将比例四舍五入保留两位小数 (`actualRatio`)。
         * 7. 使用访问量 (`cnt`)、浏览器名称 (`browser`) 和占比 (`actualRatio`) 构建响应对象，并添加到 `browserStats` 列表。
         */
        List<ShortLinkStatsBrowserRespDTO> browserStats = new ArrayList<>();
        List<HashMap<String, Object>> linkBrowserCntStatsList = linkBrowserStatsMapper.listBrowserStatsByShortLink(requestParam);
        int browserSum = linkBrowserCntStatsList.stream()
                .mapToInt(each -> Integer.parseInt(each.get("count").toString()))
                .sum();
        linkBrowserCntStatsList.forEach(each -> {
            double ratio = (double) Integer.parseInt(each.get("count").toString()) / browserSum;
            double actualRadio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsBrowserRespDTO linkStatsBrowserRespDTO = ShortLinkStatsBrowserRespDTO.builder()
                    .cnt(Integer.parseInt(each.get("count").toString()))
                    .browser(each.get("browser").toString())
                    .ratio(actualRadio)
                    .build();
            browserStats.add(linkStatsBrowserRespDTO);
        });

        /*
         * 操作系统访问详情
         * 1. 初始化 `osStats` 列表用于存储操作系统详情。
         * 2. 调用 linkOsStatsMapper 获取操作系统统计数据列表。
         * 3. 累加操作系统统计数据列表中所有操作系统的 `count` 得到总访问量 (`osSum`)。
         * 4. 遍历操作系统统计数据列表。
         * 5. 计算当前操作系统的访问量占总访问量的比例 (`ratio`)。
         * 6. 将比例四舍五入保留两位小数 (`actualRatio`)。
         * 7. 使用访问量 (`cnt`)、操作系统名称 (`os`) 和占比 (`actualRatio`) 构建响应对象，并添加到 `osStats` 列表。
         */
        List<ShortLinkStatsOsRespDTO> osStats = new ArrayList<>();
        List<HashMap<String, Object>> linkOsStatsDOList = linkOsStatsMapper.listOsStatsByShortLink(requestParam);
        int osSum = linkOsStatsDOList.stream()
                .mapToInt(each -> Integer.parseInt(each.get("count").toString()))
                .sum();
        linkOsStatsDOList.forEach(each -> {
            double ratio = (double) Integer.parseInt(each.get("count").toString()) / osSum;
            double actualRatio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsOsRespDTO linkStatsOsRespDTO = ShortLinkStatsOsRespDTO.builder()
                    .os(each.get("os").toString())
                    .cnt(Integer.parseInt(each.get("count").toString()))
                    .ratio(actualRatio)
                    .build();
            osStats.add(linkStatsOsRespDTO);
        });

        /*
         * 访客访问类型详情 (新访客/老访客)
         * 1. 初始化 `uvTypeStats` 列表用于存储访客类型详情。
         * 2. 调用 linkAccessLogsMapper 查询新访客 (`newUserCnt`) 和老访客 (`oldUserCnt`) 的数量。
         * 3. 从查询结果 Map 中安全地获取老访客数量 (`oldUserCnt`)，如果为空则默认为 0。
         * 4. 从查询结果 Map 中安全地获取新访客数量 (`newUserCnt`)，如果为空则默认为 0。
         * 5. 将新访客和老访客的数量相加得到总 UV 访客数 (`uvSum`)。
         * 6. 计算老访客占比 (`oldRatio`)，并四舍五入保留两位小数 (`actualOldRatio`)。
         * 7. 计算新访客占比 (`newRatio`)，并四舍五入保留两位小数 (`actualNewRatio`)。
         * 8. 构建新访客 (`newUser`) 的响应对象，包含数量和占比，并添加到 `uvTypeStats`。
         * 9. 构建老访客 (`oldUser`) 的响应对象，包含数量和占比，并添加到 `uvTypeStats`。
         */
        List<ShortLinkStatsUvRespDTO> uvTypeStats = new ArrayList<>();
        HashMap<String, Object> linkUvTypeCntList = linkAccessLogsMapper.findUvTypeCntByShortLink(requestParam);
        int oldUserCnt = Integer.parseInt(
                Optional.ofNullable(linkUvTypeCntList)
                        .map(each -> each.get("oldUserCnt"))
                        .map(Object::toString)
                        .orElse("0")
        );
        int newUserCnt = Integer.parseInt(
                Optional.ofNullable(linkUvTypeCntList)
                        .map(each -> each.get("newUserCnt"))
                        .map(Object::toString)
                        .orElse("0")
        );
        int uvSum = oldUserCnt + newUserCnt;
        double oldRatio = (double) oldUserCnt / uvSum;
        double actualOldRatio = Math.round(oldRatio * 100.0) / 100.0;
        double newRatio = (double) newUserCnt / uvSum;
        double actualNewRatio = Math.round(newRatio * 100.0) / 100.0;
        ShortLinkStatsUvRespDTO newUvRespDTO = ShortLinkStatsUvRespDTO.builder()
                .uvType("newUser")
                .cnt(newUserCnt)
                .ratio(actualNewRatio)
                .build();
        uvTypeStats.add(newUvRespDTO);
        ShortLinkStatsUvRespDTO oldUvRespDTO = ShortLinkStatsUvRespDTO.builder()
                .uvType("oldUser")
                .cnt(oldUserCnt)
                .ratio(actualOldRatio)
                .build();
        uvTypeStats.add(oldUvRespDTO);

        /*
         * 访问设备类型详情
         * 1. 初始化 `deviceStats` 列表用于存储设备详情。
         * 2. 调用 linkDeviceStatsMapper 获取设备统计数据列表。
         * 3. 累加设备统计数据列表中所有设备的 `cnt` 得到总访问量 (`deviceSum`)。
         * 4. 遍历设备统计数据列表。
         * 5. 计算当前设备的访问量占总访问量的比例 (`ratio`)。
         * 6. 将比例四舍五入保留两位小数 (`actualRatio`)。
         * 7. 使用访问量 (`cnt`)、设备名称 (`device`) 和占比 (`actualRatio`) 构建响应对象，并添加到 `deviceStats` 列表。
         */
        List<ShortLinkStatsDeviceRespDTO> deviceStats = new ArrayList<>();
        List<HashMap<String, Object>> linkDeviceStatsDOList = linkDeviceStatsMapper.listDeviceStatsByShortLink(requestParam);
        int deviceSum = linkDeviceStatsDOList.stream()
                .mapToInt(each -> Integer.parseInt(each.get("count").toString()))
                .sum();
        linkDeviceStatsDOList.forEach(each -> {
            double ratio = (double) Integer.parseInt(each.get("count").toString()) / deviceSum;
            double actualRatio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsDeviceRespDTO linkStatsDeviceRespDTO = ShortLinkStatsDeviceRespDTO.builder()
                    .device(each.get("device").toString())
                    .cnt(Integer.parseInt(each.get("count").toString()))
                    .ratio(actualRatio)
                    .build();
            deviceStats.add(linkStatsDeviceRespDTO);
        });


        /*
         * 访问网络类型详情
         * 1. 初始化 `networkStats` 列表用于存储网络详情。
         * 2. 调用 linkNetworkStatsMapper 获取网络统计数据列表。
         * 3. 累加网络统计数据列表中所有网络的 `cnt` 得到总访问量 (`networkSum`)。
         * 4. 遍历网络统计数据列表。
         * 5. 计算当前网络的访问量占总访问量的比例 (`ratio`)。
         * 6. 将比例四舍五入保留两位小数 (`actualRatio`)。
         * 7. 使用访问量 (`cnt`)、网络类型 (`network`) 和占比 (`actualRatio`) 构建响应对象，并添加到 `networkStats` 列表。
         */
        List<ShortLinkStatsNetworkRespDTO> networkStats = new ArrayList<>();
        List<HashMap<String, Object>> linkNetworkStatsDOList = linkNetworkStatsMapper.listNetworkStatsByShortLink(requestParam);
        int networkSum = linkNetworkStatsDOList.stream()
                .mapToInt(each -> Integer.parseInt(each.get("count").toString()))
                .sum();
        linkNetworkStatsDOList.forEach(each -> {
            double ratio = (double) Integer.parseInt(each.get("count").toString()) / networkSum;
            double actualRatio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsNetworkRespDTO linkStatsNetworkRespDTO = ShortLinkStatsNetworkRespDTO.builder()
                    .network(each.get("network").toString())
                    .cnt(Integer.parseInt(each.get("count").toString()))
                    .ratio(actualRatio)
                    .build();
            networkStats.add(linkStatsNetworkRespDTO);
        });

        /*
         * 组装最终的 ShortLinkStatsRespDTO 结果对象
         * 1. 使用所有已计算和填充的统计数据（PV, UV, UIP, daily, localeCnStats, hourStats, topIpStats, weekdayStats, browserStats, osStats, uvTypeStats, deviceStats, networkStats）构建并返回最终的 `ShortLinkStatsRespDTO` 对象。
         */
        return ShortLinkStatsRespDTO.builder()
                .pv(pvUvUipByShortLink.getPv())
                .uv(pvUvUipByShortLink.getUv())
                .uip(pvUvUipByShortLink.getUip())
                .daily(daily)
                .localeCnStats(localeCnStats)
                .hourStats(hourStats)
                .topIpStats(topIpStats)
                .weekdayStats(weekdayStats)
                .browserStats(browserStats)
                .osStats(osStats)
                .uvTypeStats(uvTypeStats)
                .deviceStats(deviceStats)
                .networkStats(networkStats)
                .build();
    }

    @Override
    public ShortLinkStatsRespDTO groupShortLinkStats(ShortLinkGroupStatsReqDTO requestParam) {
        List<LinkAccessStatsDO> listStatsByGroup = linkAccessStatsMapper.listStatsByGroup(requestParam);
        if (CollUtil.isEmpty(listStatsByGroup)) {
            return null;
        }
        // 基础访问数据
        LinkAccessStatsDO pvUvUidStatsByGroup = linkAccessLogsMapper.findPvUvUidStatsByGroup(requestParam);
        // 基础访问详情
        List<ShortLinkStatsAccessDailyRespDTO> daily = new ArrayList<>();
        List<String> rangeDates = DateUtil.rangeToList(DateUtil.parse(requestParam.getStartDate()), DateUtil.parse(requestParam.getEndDate()), DateField.DAY_OF_MONTH).stream()
                .map(DateUtil::formatDate)
                .toList();
        rangeDates.forEach(each -> listStatsByGroup.stream()
                .filter(item -> Objects.equals(each, item.getDate().toString()))
                .findFirst()
                .ifPresentOrElse(item -> {
                    ShortLinkStatsAccessDailyRespDTO accessDailyRespDTO = ShortLinkStatsAccessDailyRespDTO.builder()
                            .date(each)
                            .pv(item.getPv())
                            .uv(item.getUv())
                            .uip(item.getUip())
                            .build();
                    daily.add(accessDailyRespDTO);
                }, () -> {
                    ShortLinkStatsAccessDailyRespDTO accessDailyRespDTO = ShortLinkStatsAccessDailyRespDTO.builder()
                            .date(each)
                            .pv(0)
                            .uv(0)
                            .uip(0)
                            .build();
                    daily.add(accessDailyRespDTO);
                }));
        // 地区访问详情（仅国内）
        List<ShortLinkStatsLocaleCNRespDTO> localeCnStats = new ArrayList<>();
        List<LinkLocaleStatsDO> listedLocaleByGroup = linkLocaleStatsMapper.listLocaleByGroup(requestParam);
        int localeCnSum = listedLocaleByGroup.stream()
                .mapToInt(LinkLocaleStatsDO::getCnt)
                .sum();
        listedLocaleByGroup.forEach(each -> {
            double ratio = (double) each.getCnt() / localeCnSum;
            double actualRatio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsLocaleCNRespDTO localeCNRespDTO = ShortLinkStatsLocaleCNRespDTO.builder()
                    .cnt(each.getCnt())
                    .locale(each.getProvince())
                    .ratio(actualRatio)
                    .build();
            localeCnStats.add(localeCNRespDTO);
        });
        // 小时访问详情
        List<Integer> hourStats = new ArrayList<>();
        List<LinkAccessStatsDO> listHourStatsByGroup = linkAccessStatsMapper.listHourStatsByGroup(requestParam);
        for (int i = 0; i < 24; i++) {
            AtomicInteger hour = new AtomicInteger(i);
            int hourCnt = listHourStatsByGroup.stream()
                    .filter(each -> Objects.equals(each.getHour(), hour.get()))
                    .findFirst()
                    .map(LinkAccessStatsDO::getPv)
                    .orElse(0);
            hourStats.add(hourCnt);
        }
        // 高频访问IP详情
        List<ShortLinkStatsTopIpRespDTO> topIpStats = new ArrayList<>();
        List<HashMap<String, Object>> listTopIpByGroup = linkAccessLogsMapper.listTopIpByGroup(requestParam);
        listTopIpByGroup.forEach(each -> {
            ShortLinkStatsTopIpRespDTO statsTopIpRespDTO = ShortLinkStatsTopIpRespDTO.builder()
                    .ip(each.get("ip").toString())
                    .cnt(Integer.parseInt(each.get("count").toString()))
                    .build();
            topIpStats.add(statsTopIpRespDTO);
        });
        // 一周访问详情
        List<Integer> weekdayStats = new ArrayList<>();
        List<LinkAccessStatsDO> listWeekdayStatsByGroup = linkAccessStatsMapper.listWeekdayStatsByGroup(requestParam);
        for (int i = 1; i < 8; i++) {
            AtomicInteger weekday = new AtomicInteger(i);
            int weekdayCnt = listWeekdayStatsByGroup.stream()
                    .filter(each -> Objects.equals(each.getWeekday(), weekday.get()))
                    .findFirst()
                    .map(LinkAccessStatsDO::getPv)
                    .orElse(0);
            weekdayStats.add(weekdayCnt);
        }
        // 浏览器访问详情
        List<ShortLinkStatsBrowserRespDTO> browserStats = new ArrayList<>();
        List<HashMap<String, Object>> listBrowserStatsByGroup = linkBrowserStatsMapper.listBrowserStatsByGroup(requestParam);
        int browserSum = listBrowserStatsByGroup.stream()
                .mapToInt(each -> Integer.parseInt(each.get("count").toString()))
                .sum();
        listBrowserStatsByGroup.forEach(each -> {
            double ratio = (double) Integer.parseInt(each.get("count").toString()) / browserSum;
            double actualRatio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsBrowserRespDTO browserRespDTO = ShortLinkStatsBrowserRespDTO.builder()
                    .cnt(Integer.parseInt(each.get("count").toString()))
                    .browser(each.get("browser").toString())
                    .ratio(actualRatio)
                    .build();
            browserStats.add(browserRespDTO);
        });
        // 操作系统访问详情
        List<ShortLinkStatsOsRespDTO> osStats = new ArrayList<>();
        List<HashMap<String, Object>> listOsStatsByGroup = linkOsStatsMapper.listOsStatsByGroup(requestParam);
        int osSum = listOsStatsByGroup.stream()
                .mapToInt(each -> Integer.parseInt(each.get("count").toString()))
                .sum();
        listOsStatsByGroup.forEach(each -> {
            double ratio = (double) Integer.parseInt(each.get("count").toString()) / osSum;
            double actualRatio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsOsRespDTO osRespDTO = ShortLinkStatsOsRespDTO.builder()
                    .cnt(Integer.parseInt(each.get("count").toString()))
                    .os(each.get("os").toString())
                    .ratio(actualRatio)
                    .build();
            osStats.add(osRespDTO);
        });
        // 访问设备类型详情
        List<ShortLinkStatsDeviceRespDTO> deviceStats = new ArrayList<>();
        List<LinkDeviceStatsDO> listDeviceStatsByGroup = linkDeviceStatsMapper.listDeviceStatsByGroup(requestParam);
        int deviceSum = listDeviceStatsByGroup.stream()
                .mapToInt(LinkDeviceStatsDO::getCnt)
                .sum();
        listDeviceStatsByGroup.forEach(each -> {
            double ratio = (double) each.getCnt() / deviceSum;
            double actualRatio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsDeviceRespDTO deviceRespDTO = ShortLinkStatsDeviceRespDTO.builder()
                    .cnt(each.getCnt())
                    .device(each.getDevice())
                    .ratio(actualRatio)
                    .build();
            deviceStats.add(deviceRespDTO);
        });
        // 访问网络类型详情
        List<ShortLinkStatsNetworkRespDTO> networkStats = new ArrayList<>();
        List<LinkNetworkStatsDO> listNetworkStatsByGroup = linkNetworkStatsMapper.listNetworkStatsByGroup(requestParam);
        int networkSum = listNetworkStatsByGroup.stream()
                .mapToInt(LinkNetworkStatsDO::getCnt)
                .sum();
        listNetworkStatsByGroup.forEach(each -> {
            double ratio = (double) each.getCnt() / networkSum;
            double actualRatio = Math.round(ratio * 100.0) / 100.0;
            ShortLinkStatsNetworkRespDTO networkRespDTO = ShortLinkStatsNetworkRespDTO.builder()
                    .cnt(each.getCnt())
                    .network(each.getNetwork())
                    .ratio(actualRatio)
                    .build();
            networkStats.add(networkRespDTO);
        });
        return ShortLinkStatsRespDTO.builder()
                .pv(pvUvUidStatsByGroup.getPv())
                .uv(pvUvUidStatsByGroup.getUv())
                .uip(pvUvUidStatsByGroup.getUip())
                .daily(daily)
                .localeCnStats(localeCnStats)
                .hourStats(hourStats)
                .topIpStats(topIpStats)
                .weekdayStats(weekdayStats)
                .browserStats(browserStats)
                .osStats(osStats)
                .deviceStats(deviceStats)
                .networkStats(networkStats)
                .build();
    }

    @Override
    public IPage<ShortLinkStatsAccessRecordRespDTO> shortLinkStatsAccessRecord(ShortLinkStatsAccessRecordReqDTO requestParam) {
        LambdaQueryWrapper<LinkAccessLogsDO> queryWrapper = Wrappers.lambdaQuery(LinkAccessLogsDO.class)
                .eq(LinkAccessLogsDO::getGid, requestParam.getGid())
                .eq(LinkAccessLogsDO::getFullShortUrl, requestParam.getFullShortUrl())
                .between(LinkAccessLogsDO::getCreateTime, requestParam.getStartDate(), requestParam.getEndDate())
                .orderByDesc(LinkAccessLogsDO::getCreateTime);
        IPage<LinkAccessLogsDO> linkAccessLogsDOIPage = linkAccessLogsMapper.selectPage(requestParam, queryWrapper);
        IPage<ShortLinkStatsAccessRecordRespDTO> actualResult = linkAccessLogsDOIPage.convert(
                each -> BeanUtil.toBean(each, ShortLinkStatsAccessRecordRespDTO.class)
        );
        List<String> userAccessLogsList = actualResult.getRecords()
                .stream()
                .map(ShortLinkStatsAccessRecordRespDTO::getUser)
                .toList();
        if (CollectionUtil.isEmpty(userAccessLogsList)) {
            return actualResult;
        }
        ShortLinkUvTypeQueryDTO shortLinkUvTypeQueryDTO = new ShortLinkUvTypeQueryDTO();
        BeanUtils.copyProperties(requestParam, shortLinkUvTypeQueryDTO);
        shortLinkUvTypeQueryDTO.setUserAccessLogsList(userAccessLogsList);
        List<Map<String, Object>> uvTypeList = linkAccessLogsMapper.selectUvByUsers(shortLinkUvTypeQueryDTO);
        actualResult.getRecords().forEach(each -> {
            String uvType = uvTypeList.stream()
                    .filter(item -> Objects.equals(each.getUser(), item.get("user")))
                    .findFirst()
                    .map(item -> item.get("uvType"))
                    .map(Object::toString)
                    .orElse("旧访客");
            each.setUvType(uvType);
        });
        return actualResult;
    }

    @Override
    public IPage<ShortLinkStatsAccessRecordRespDTO> groupShortLinkStatsAccessRecord(ShortLinkGroupStatsAccessRecordReqDTO requestParam) {
        LambdaQueryWrapper<LinkAccessLogsDO> queryWrapper = Wrappers.lambdaQuery(LinkAccessLogsDO.class)
                .eq(LinkAccessLogsDO::getGid, requestParam.getGid())
                .between(LinkAccessLogsDO::getCreateTime, requestParam.getStartDate(), requestParam.getEndDate())
                .eq(LinkAccessLogsDO::getDelFlag, 0)
                .orderByDesc(LinkAccessLogsDO::getCreateTime);
        IPage<LinkAccessLogsDO> linkAccessLogsDOIPage = linkAccessLogsMapper.selectPage(requestParam, queryWrapper);
        IPage<ShortLinkStatsAccessRecordRespDTO> actualResult = linkAccessLogsDOIPage.convert(each -> BeanUtil.toBean(each, ShortLinkStatsAccessRecordRespDTO.class));
        List<String> userAccessLogsList = actualResult.getRecords().stream()
                .map(ShortLinkStatsAccessRecordRespDTO::getUser)
                .toList();
        List<Map<String, Object>> uvTypeList = linkAccessLogsMapper.selectGroupUvTypeByUsers(
                requestParam.getGid(),
                requestParam.getStartDate(),
                requestParam.getEndDate(),
                userAccessLogsList
        );
        actualResult.getRecords().forEach(each -> {
            String uvType = uvTypeList.stream()
                    .filter(item -> Objects.equals(each.getUser(), item.get("user")))
                    .findFirst()
                    .map(item -> item.get("UvType"))
                    .map(Object::toString)
                    .orElse("旧访客");
            each.setUvType(uvType);
        });
        return actualResult;
    }
}
