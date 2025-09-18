package com.tenseed.shortlink.project.toolkit;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;

import java.util.Date;
import java.util.Optional;

import static com.tenseed.shortlink.project.common.constant.ShortLinkConstant.DEFAULT_CACHE_VALID_DATE;

/**
 * 短链接工具类
 */
public class LinkUtil {

    /**
     * 获取短链接缓存有效期时间
     *
     * @param validDate 有效期时间
     * @return 有限期时间戳
     */
    public static Long getLinkCacheValidTime(Date validDate) {
        // 使用 Optional 避免空指针异常
        return Optional.ofNullable(validDate)
                // 如果有效期不为空，则计算当前时间到有效期的时间差(毫秒)
                .map(each -> DateUtil.between(new Date(), each, DateUnit.MS))
                // 如果有效期为空，则使用默认缓存有效期
                .orElse(DEFAULT_CACHE_VALID_DATE);
    }

}
