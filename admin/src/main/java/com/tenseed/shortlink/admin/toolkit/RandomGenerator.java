package com.tenseed.shortlink.admin.toolkit;

import cn.hutool.core.util.RandomUtil;

/**
 * 随机字符串生成工具类
 */
public class RandomGenerator {

    /**
     * 自定义的包含大小写字母和数字的字符集
     */
    private static final String CUSTOM_BASE_CHAR_NUMBER = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /**
     * 生成包含大小写字母和数字的6位随机字符串
     *
     * @return 6位随机字符串
     */
    public static String generateSixAlphaNumber() {
        return RandomUtil.randomString(CUSTOM_BASE_CHAR_NUMBER, 6);
    }

}