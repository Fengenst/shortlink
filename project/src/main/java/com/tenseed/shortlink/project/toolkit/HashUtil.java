package com.tenseed.shortlink.project.toolkit;

import cn.hutool.core.lang.hash.MurmurHash;

/**
 * HASH 工具类
 */
public class HashUtil {

    // Base62 字符集：包含数字、大写字母和小写字母，共 62个字符
    private static final char[] CHARS = new char[]{
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
    };

    private static final int SIZE = CHARS.length;

    /**
     * 将十进制数字转换为 Base62 编码
     *
     * @param num 十进制数字
     * @return Base62 编码字符串
     */
    private static String convertDecToBase62(long num) {
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            // 取余数作为字符索引
            int i = (int) (num % SIZE);
            sb.append(CHARS[i]);
            // 继续处理商
            num /= SIZE;
        }
        // 由于是从低位到高位计算，需要反转字符串
        return sb.reverse().toString();
    }

    /**
     * 对字符串进行 MurmurHash32 并转换为 Base62 编码
     *
     * @param str 输入字符串
     * @return Base62 编码的哈希值
     */
    public static String hashToBase62(String str) {
        // 使用 MurmurHash 算法生成 32位哈希值
        int i = MurmurHash.hash32(str);
        // 处理负数情况，确保得到正数
        long num = i < 0 ? Integer.MAX_VALUE - (long) i : i;
        // 转换为 Base62 编码
        return convertDecToBase62(num);
    }
}