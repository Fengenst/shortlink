package com.tenseed.shortlink.project.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.Week;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tenseed.shortlink.project.common.convention.exception.ClientException;
import com.tenseed.shortlink.project.common.convention.exception.ServiceException;
import com.tenseed.shortlink.project.common.enums.ValidDateTypeEnum;
import com.tenseed.shortlink.project.dao.entity.*;
import com.tenseed.shortlink.project.dao.mapper.*;
import com.tenseed.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.tenseed.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.tenseed.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.tenseed.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.tenseed.shortlink.project.dto.resp.ShortLinkGroupCountQueryRespDTO;
import com.tenseed.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.tenseed.shortlink.project.service.ShortLinkService;
import com.tenseed.shortlink.project.toolkit.HashUtil;
import com.tenseed.shortlink.project.toolkit.LinkUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.tenseed.shortlink.project.common.constant.RedisKeyConstant.*;
import static com.tenseed.shortlink.project.common.constant.ShortLinkConstant.AMAP_REMOTE_URL;

/**
 * 短链接接口实现层
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {

    private final RBloomFilter<String> shortUriCreateCachePenetrationBloomFilter;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;

    @Value("${short-link.stats.locale.amap-key}")
    private String statsLocaleAmapKey;

    /**
     * 生成短链接后缀
     *
     * @param requestParam 短链接创建请求参数
     * @return 短链接后缀
     */
    private String generateSuffix(ShortLinkCreateReqDTO requestParam) {
        int customGenerateCount = 0;
        String shortUri;
        while (true) {
            // 限制生成次数，防止无限循环
            if (customGenerateCount > 10) {
                throw new ServiceException("短链接频繁生成，请稍后再试");
            }
            // 添加时间戳确保每次生成输入不同
            String originUrl = requestParam.getOriginUrl();
            originUrl += System.currentTimeMillis();
            // 哈希转换为Base62编码
            shortUri = HashUtil.hashToBase62(originUrl);
            // 检查布隆过滤器避免重复
            if (!shortUriCreateCachePenetrationBloomFilter.contains(requestParam.getDomain() + "/" + shortUri)) {
                break;
            }
            customGenerateCount++;
        }
        return shortUri;
    }


    /**
     * 获取网站的 favicon 图标链接
     *
     * @param url 网站 URL
     * @return favicon 图标链接，如果不存在则返回 null
     */
    @SneakyThrows
    private String getFavicon(String url) {
        // 建立 HTTP 连接验证网站可访问性
        URL targetUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();
        int responseCode = connection.getResponseCode();

        // 网站访问正常时解析 HTML 获取 favicon
        if (HttpURLConnection.HTTP_OK == responseCode) {
            // 使用 Jsoup 解析网站 HTML 文档
            Document document = Jsoup.connect(url).get();
            // 在 HTML 文档中查找 favicon 图标链接
            // cssQuery：查找 <link> 标签，rel属性值匹配 ^(shortcut )?icon 正则表达式
            // 可以匹配 "icon"、"shortcut icon" 等常见的 favicon 声明方式
            Element faviconLink = document.select("link[rel~=(?i)^(shortcut )?icon]").first();
            // 如果找到了 favicon 链接，则返回其绝对URL地址
            if (faviconLink != null) {
                // attr("abs:href") 获取 href 属性的绝对 URL（自动补全域名）
                return faviconLink.attr("abs:href");
            }
        }
        // 如果任何步骤失败或未找到 favicon，返回 null
        return null;
    }

    /**
     * 根据当前时间到下一个小时的分钟差值并返回
     *
     * @return 分钟数
     */
    public static int minutesUntilNextHour() {
        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();

        // 获取下一个整点时间
        LocalDateTime nextHour = now.plusHours(1).truncatedTo(ChronoUnit.HOURS);

        // 计算当前时间到下一个整点的分钟差值
        long minutesUntilNextHour = ChronoUnit.MINUTES.between(now, nextHour);

        return (int) minutesUntilNextHour;
    }

    /**
     * 短链接访问统计
     *
     * @param fullShortUrl 完整短链接
     * @param gid          分组标识
     * @param request      HTTP 请求
     * @param response     HTTP 响应
     */
    private void shortLinkStats(String fullShortUrl, String gid, ServletRequest request, ServletResponse response) {
        // uvFirstFlag 用于标记本次访问是否为该小时内的首次UV
        AtomicBoolean uvFirstFlag = new AtomicBoolean();
        Cookie[] cookies = ((HttpServletRequest) request).getCookies();
        try {
            // expireMinutes 计算到下一个整点的小时剩余分钟数，用于设置Redis缓存的过期时间
            int expireMinutes = minutesUntilNextHour();

            AtomicReference<String> uv = new AtomicReference<>();

            // addResponseCookieTask 是一个Runnable任务，封装了处理“新访客”的逻辑
            // 只有当浏览器没有携带uv cookie时，才会执行此任务
            Runnable addResponseCookieTask = () -> {
                uv.set(UUID.fastUUID().toString()); // 生成一个唯一的ID作为UV标识
                Cookie uvCookie = new Cookie("uv", uv.get());
                uvCookie.setMaxAge(60 * 60 * 24 * 30); // 设置cookie有效期为30天
                // 设置cookie路径，确保在访问该短链接时浏览器会携带此cookie
                uvCookie.setPath(StrUtil.sub(fullShortUrl, fullShortUrl.indexOf("/"), fullShortUrl.length()));
                ((HttpServletResponse) response).addCookie(uvCookie); // 将cookie添加到响应中
                uvFirstFlag.set(Boolean.TRUE); // 标记本次访问是新的UV
                // 将UV ID添加到Redis Set，用于去重
                stringRedisTemplate.opsForSet().add("short-link:stats:uv" + fullShortUrl, uv.get());
                // 设置Redis缓存的过期时间，使其与自然小时对齐
                stringRedisTemplate.expire("short-link:stats:uv" + fullShortUrl, expireMinutes, TimeUnit.MINUTES);
            };

            // 判断请求是否携带任何cookie
            if (ArrayUtil.isNotEmpty(cookies)) {
                // 过滤出名为"uv"的cookie，并获取其值
                Arrays.stream(cookies).filter(each -> Objects.equals(each.getName(), "uv"))
                        .findFirst()
                        .map(Cookie::getValue)
                        // 如果找到了"uv" cookie，执行ifPresent逻辑；否则，执行orElse逻辑
                        .ifPresentOrElse(each -> {
                            uv.set(each);
                            // 尝试将UV ID添加到Redis Set。uvAdded>0L表示添加成功（新的UV）
                            Long uvAdded = stringRedisTemplate.opsForSet().add("short-link:stats:uv" + fullShortUrl, each);
                            // 根据Redis返回结果设置UV标志
                            uvFirstFlag.set(uvAdded != null && uvAdded > 0L);

                            // 有当成功新增UV ID时，才设置过期时间
                            // 这样做避免了在每次重复访问时都刷新过期时间，从而导致缓存永不过期的问题
                            if (uvAdded != null && uvAdded > 0L) {
                                stringRedisTemplate.expire("short-link:stats:uv" + fullShortUrl, expireMinutes, TimeUnit.MINUTES);
                            }
                        }, addResponseCookieTask); // 如果没有找到"uv" cookie，执行新访客任务
            } else {
                // 如果请求完全没有携带cookie，直接执行新访客任务
                addResponseCookieTask.run();
            }

            // 获取请求IP，并进行UIP统计
            String remoteAddr = LinkUtil.getActualIp(((HttpServletRequest) request));
            // 尝试将IP添加到Redis Set，uipAdded>0L表示添加成功（新的UIP）
            Long uipAdded = stringRedisTemplate.opsForSet().add("short-link:stats:uip" + fullShortUrl, remoteAddr);
            boolean uipFirstFlag = uipAdded != null && uipAdded > 0L;
            // 只有当成功新增UIP时，才设置过期时间，原理同UV
            if (uipFirstFlag) {
                stringRedisTemplate.expire("short-link:stats:uip" + fullShortUrl, expireMinutes, TimeUnit.MINUTES);
            }

            // 假如 gid 是 null，通过短链接跳转表查询对应的分组ID
            if (StrUtil.isBlank(gid)) {
                LambdaQueryWrapper<ShortLinkGotoDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                        .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
                ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(queryWrapper);
                gid = shortLinkGotoDO.getGid();
            }

            // 获得当前日期、星期、小时，用于数据库统计
            Date date = new Date();
            Week week = DateUtil.dayOfWeekEnum(date);
            int hour = DateUtil.hour(date, true);

            // 构建数据库统计对象，准备进行持久化
            LinkAccessStatsDO linkAccessStatsDO = LinkAccessStatsDO.builder()
                    .fullShortUrl(fullShortUrl)
                    .gid(gid)
                    .date(LocalDate.now())
                    .pv(1) // 每次访问PV加1
                    .uv(uvFirstFlag.get() ? 1 : 0) // 根据uvFirstFlag判断是否新增UV
                    .uip(uipFirstFlag ? 1 : 0) // 根据uipFirstFlag判断是否新增UIP
                    .hour(hour)
                    .weekday(week.getIso8601Value())
                    .build();

            // 调用Mapper方法，将数据保存到数据库
            // 这里的shortLinkStats方法使用了ON DUPLICATE KEY UPDATE，
            // 负责将pv/uv/uip累加到当天的每小时记录中
            linkAccessStatsMapper.shortLinkStats(linkAccessStatsDO);

            // 对访问地区的统计
            Map<String, Object> localeParamMap = new HashMap<>();
            localeParamMap.put("key", statsLocaleAmapKey);
            localeParamMap.put("ip", remoteAddr);
            String localeResultStr = HttpUtil.get(AMAP_REMOTE_URL, localeParamMap);
            JSONObject localeResultObj = JSON.parseObject(localeResultStr);
            String infocode = localeResultObj.getString("infocode");
            LinkLocaleStatsDO linkLocaleStatsDO;
            String actualProvince;
            String actualCity;
            if (StrUtil.isNotBlank(infocode) && StrUtil.equals(infocode, "10000")) {
                String province = localeResultObj.getString("province");
                boolean unknownFlag = StrUtil.equals(province, "[]");
                linkLocaleStatsDO = LinkLocaleStatsDO.builder()
                        .fullShortUrl(fullShortUrl)
                        .gid(gid)
                        .date(LocalDate.now())
                        .cnt(1)
                        .province(actualProvince = unknownFlag ? "未知" : province)
                        .city(actualCity = unknownFlag ? "未知" : localeResultObj.getString("city"))
                        .adcode(unknownFlag ? "未知" : localeResultObj.getString("adcode"))
                        .country("中国")
                        .build();
                linkLocaleStatsMapper.shortLinkLocaleStats(linkLocaleStatsDO);

                // 对访问系统的统计
                String os = LinkUtil.getOs(((HttpServletRequest) request));
                LinkOsStatsDO linkOsStatsDO = LinkOsStatsDO.builder()
                        .fullShortUrl(fullShortUrl)
                        .gid(gid)
                        .date(LocalDate.now())
                        .cnt(1)
                        .os(os)
                        .build();
                linkOsStatsMapper.shortLinkOsStats(linkOsStatsDO);

                // 对访问浏览器的统计
                String browser = LinkUtil.getBrowser(((HttpServletRequest) request));
                LinkBrowserStatsDO linkBrowserStatsDO = LinkBrowserStatsDO.builder()
                        .fullShortUrl(fullShortUrl)
                        .gid(gid)
                        .date(LocalDate.now())
                        .cnt(1)
                        .browser(browser)
                        .build();
                linkBrowserStatsMapper.shortLinkBrowserStats(linkBrowserStatsDO);

                // 访问设备
                String device = LinkUtil.getDevice(((HttpServletRequest) request));
                LinkDeviceStatsDO linkDeviceStatsDO = LinkDeviceStatsDO.builder()
                        .device(device)
                        .cnt(1)
                        .gid(gid)
                        .fullShortUrl(fullShortUrl)
                        .date(LocalDate.now())
                        .build();
                linkDeviceStatsMapper.shortLinkDeviceState(linkDeviceStatsDO);

                // 访问网络
                String network = LinkUtil.getNetwork(((HttpServletRequest) request));
                LinkNetworkStatsDO linkNetworkStatsDO = LinkNetworkStatsDO.builder()
                        .network(network)
                        .cnt(1)
                        .gid(gid)
                        .fullShortUrl(fullShortUrl)
                        .date(LocalDate.now())
                        .build();
                linkNetworkStatsMapper.shortLinkNetworkState(linkNetworkStatsDO);

                // 访问日志
                LinkAccessLogsDO linkAccessLogsDO = LinkAccessLogsDO.builder()
                        .fullShortUrl(fullShortUrl)
                        .gid(gid)
                        .user(uv.get())
                        .browser(browser)
                        .os(os)
                        .ip(remoteAddr)
                        .network(network)
                        .device(device)
                        .locale(StrUtil.join("-", "中国", actualProvince, actualCity))
                        .build();
                linkAccessLogsMapper.insert(linkAccessLogsDO);
            }
        } catch (Throwable e) {
            log.error("短链接访问量统计异常", e);
        }
    }

    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        // 生成短链接后缀
        String shortLinkSuffix = generateSuffix(requestParam);
        // 构造完整短链接
        String fullShortUrl = requestParam.getDomain() + "/" + shortLinkSuffix;

        // 构建短链接实体对象
        ShortLinkDO shortLinkDO = BeanUtil.toBean(requestParam, ShortLinkDO.class);
        shortLinkDO.setShortUri(shortLinkSuffix);
        shortLinkDO.setEnableStatus(0); // 设置启用状态
        shortLinkDO.setFullShortUrl(fullShortUrl);
        // 获取目标网站favicon图标
        // shortLinkDO.setFavicon(getFavicon(requestParam.getOriginUrl()));

        // 构建短链接路由实体对象
        ShortLinkGotoDO shortLinkGotoDO = ShortLinkGotoDO.builder()
                .fullShortUrl(fullShortUrl)
                .gid(requestParam.getGid())
                .build();

        try {
            // 插入主表 & 路由表
            baseMapper.insert(shortLinkDO);
            shortLinkGotoMapper.insert(shortLinkGotoDO);
        } catch (DuplicateKeyException ex) {
            // 唯一约束冲突 → 说明短链已存在（可能是并发下生成重复）
            LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl);
            ShortLinkDO availableShortLinkDO = baseMapper.selectOne(queryWrapper);
            if (availableShortLinkDO != null) {
                log.warn("短链接：{} 重复入库", fullShortUrl);
                throw new ServiceException("短链接生成重复");
            }
        }
        // 短链接创建时进行缓存预热，避免首次访问时缓存未命中导致查库
        stringRedisTemplate.opsForValue().set(
                String.format(GOTO_SHORT_LINK_KEY, fullShortUrl),
                requestParam.getOriginUrl(),
                LinkUtil.getLinkCacheValidTime(requestParam.getValidDate()), TimeUnit.MILLISECONDS
        );
        // 将短链接添加到布隆过滤器中
        shortUriCreateCachePenetrationBloomFilter.add(fullShortUrl);
        // 构建并返回响应结果
        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl("http://" + shortLinkDO.getFullShortUrl())
                .originUrl(requestParam.getOriginUrl())
                .gid(requestParam.getGid())
                .build();
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        // 查询已存在的短链接记录
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getEnableStatus, 0);
        ShortLinkDO existedShortLinkDO = baseMapper.selectOne(queryWrapper);
        if (existedShortLinkDO == null) {
            throw new ClientException("短链接记录不存在");
        }

        // 构建新的短链接对象，保留原有统计信息
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .domain(existedShortLinkDO.getDomain())
                .shortUri(existedShortLinkDO.getShortUri())
                .clickNum(existedShortLinkDO.getClickNum())
                .favicon(existedShortLinkDO.getFavicon())
                .createdType(existedShortLinkDO.getCreatedType())
                .gid(requestParam.getGid())
                .originUrl(requestParam.getOriginUrl())
                .describe(requestParam.getDescribe())
                .validDateType(requestParam.getValidDateType())
                .validDate(requestParam.getValidDate())
                .build();

        // 判断分组是否发生变化
        if (Objects.equals(existedShortLinkDO.getGid(), requestParam.getGid())) {
            // 分组未变化，直接更新记录
            LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, requestParam.getGid())
                    .eq(ShortLinkDO::getEnableStatus, 0)
                    .set(Objects
                                    .equals(requestParam.getValidDateType(), ValidDateTypeEnum.PERMANENT.getType())
                            , ShortLinkDO::getValidDate, null);
            baseMapper.update(shortLinkDO, updateWrapper);
        } else {
            // 分组发生变化，先删除原记录再插入新记录
            LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, existedShortLinkDO.getGid())
                    .eq(ShortLinkDO::getEnableStatus, 0);
            baseMapper.delete(updateWrapper);
            baseMapper.insert(shortLinkDO);
        }

        // 更新数据库后，删除 Redis 缓存，处理缓存一致性
        String fullShortUrl = requestParam.getFullShortUrl();
        stringRedisTemplate.delete(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl));
        stringRedisTemplate.delete(String.format(GOTO_NULL_SHORT_LINK_KEY, fullShortUrl));
    }


    @SneakyThrows
    @Override
    public void restoreUrl(String shortUri, ServletRequest request, ServletResponse response) {
        // 1.构造完整短链接：域名 + 路径，如 "s.example.com/abc123"
        String serverName = request.getServerName();
        String fullShortUrl = serverName + "/" + shortUri;

        // 2️.第一次检查：无锁快速路径—— 先查 Redis 缓存，命中则直接跳转，避免加锁和查库开销
        String originalLink = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl));
        if (StrUtil.isNotBlank(originalLink)) {
            shortLinkStats(fullShortUrl, null, request, response); // 跳转成功则进行短链接基础访问统计
            ((HttpServletResponse) response).sendRedirect(originalLink);
            return; // 缓存命中，流程结束
        }

        // 3.使用布隆过滤器拦截非法短链（防止缓存穿透）
        boolean contains = shortUriCreateCachePenetrationBloomFilter.contains(fullShortUrl);
        if (!contains) {
            ((HttpServletResponse) response).sendRedirect("/page/notfound");
            return;
        }

        // 4.检查 null 缓存（防止无效短链频繁访问 DB）
        String gotoNullShortLink = stringRedisTemplate.opsForValue().get(String.format(GOTO_NULL_SHORT_LINK_KEY, fullShortUrl));
        if (StrUtil.isNotBlank(gotoNullShortLink)) {
            ((HttpServletResponse) response).sendRedirect("/page/notfound");
            return;
        }

        // 5.缓存未命中 → 为防止缓存击穿，使用 Redisson 分布式锁串行化“查库+回填”操作
        RLock lock = redissonClient.getLock(String.format(LOCK_GOTO_SHORT_LINK_KEY, fullShortUrl));
        lock.lock(); // 阻塞直到获取锁（建议生产环境设置超时，如 lock.lock(30, TimeUnit.SECONDS)）

        try {
            // 6.第二次检查：双重检查锁定（Double-Checked Locking）
            // 加锁后再次检查缓存 —— 因为在等待锁的过程中，可能其他线程已查库并回填缓存
            // 若此时缓存已存在，直接跳转，避免重复查库（节省 DB 资源，提升并发效率）
            originalLink = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl));
            if (StrUtil.isNotBlank(originalLink)) {
                shortLinkStats(fullShortUrl, null, request, response); // 跳转成功则进行短链接基础访问统计
                ((HttpServletResponse) response).sendRedirect(originalLink);
                return;
            }

            // 7.缓存仍无 → 查询“路由表”定位数据（支持分表架构）
            LambdaQueryWrapper<ShortLinkGotoDO> LinkGotoQueryWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                    .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
            ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(LinkGotoQueryWrapper);

            // 8.路由记录不存在 → 短链非法或已删除，直接返回（可扩展风控逻辑，如记录攻击行为）
            if (shortLinkGotoDO == null) {
                stringRedisTemplate.opsForValue().set(String.format(GOTO_NULL_SHORT_LINK_KEY, fullShortUrl), "-", 30, TimeUnit.MINUTES);
                ((HttpServletResponse) response).sendRedirect("/page/notfound");
                return;
            }

            // 9.根据路由信息（gid）+ 完整短链，查询主表，同时校验“未删除”和“已启用”状态
            LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getGid, shortLinkGotoDO.getGid()) // 分组ID，用于分表/业务隔离
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl) // 完整短链
                    .eq(ShortLinkDO::getEnableStatus, 0); // 已启用

            ShortLinkDO shortLinkDO = baseMapper.selectOne(queryWrapper);

            // 10.查询成功 → 检验有效期 + 回填 Redis 缓存 + 执行跳转
            if (shortLinkDO == null || (shortLinkDO.getValidDate() != null && shortLinkDO.getValidDate().before(new Date()))) {
                // 有效期已过 → 视为无效，写入 null 缓存
                stringRedisTemplate.opsForValue().set(String.format(GOTO_NULL_SHORT_LINK_KEY, fullShortUrl), "-", 30, TimeUnit.MINUTES);
                ((HttpServletResponse) response).sendRedirect("/page/notfound");
                return;
            }
            // 有效 → 写入 Redis 缓存，设置过期时间，避免长期脏数据
            stringRedisTemplate.opsForValue().set(
                    String.format(GOTO_SHORT_LINK_KEY, fullShortUrl),
                    shortLinkDO.getOriginUrl(),
                    LinkUtil.getLinkCacheValidTime(shortLinkDO.getValidDate()), TimeUnit.MILLISECONDS
            );

            shortLinkStats(fullShortUrl, shortLinkDO.getGid(), request, response); // 跳转成功则行短链接基础访问统计
            ((HttpServletResponse) response).sendRedirect(shortLinkDO.getOriginUrl());

            // 11.若主表也无有效数据 → 说明短链已失效，静默返回（也可跳转 404 页面）
            // 此处未处理，保持静默（符合当前逻辑）

        } finally {
            // 无论成功失败，必须释放锁！防止死锁
            lock.unlock();
        }
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam) {
        // 构造查询条件：根据分组 ID 查询未删除的启用短链接，按创建时间倒序排列
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getEnableStatus, 0)
                .orderByDesc(ShortLinkDO::getCreateTime);

        // 执行分页查询
        IPage<ShortLinkDO> resultPage = baseMapper.selectPage(requestParam, queryWrapper);
        // 转换查询结果为响应 DTO，并添加 http 前缀
        return resultPage.convert(each -> {
            ShortLinkPageRespDTO result = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            result.setDomain("http://" + result.getDomain());  // 补全域名前缀
            return result;
        });
    }


    @Override
    public List<ShortLinkGroupCountQueryRespDTO> listGroupShortLinkCount(List<String> requestParam) {
        // select gid as gid, count(*) as shortLinkCount from t_link_2 where enable_status = 0 and gid in () group by gid;
        QueryWrapper<ShortLinkDO> queryWrapper = Wrappers.query(new ShortLinkDO())
                .select("gid, count(*) as shortLinkCount")
                .in("gid", requestParam)
                .eq("enable_status", 0)
                .groupBy("gid");
        // 执行查询，将结果以Map列表的形式返回，每个Map代表一行数据
        List<Map<String, Object>> shortLinkDOList = baseMapper.selectMaps(queryWrapper);
        // 使用BeanUtil工具将 List<Map<String, Object>> 转换为 List<ShortLinkGroupCountQueryRespDTO> 并返回
        // 这样可以将数据库查询结果映射到DTO对象中，便于接口返回
        return BeanUtil.copyToList(shortLinkDOList, ShortLinkGroupCountQueryRespDTO.class);
    }
}
