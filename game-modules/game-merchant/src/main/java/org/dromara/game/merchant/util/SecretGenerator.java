package org.dromara.game.merchant.util;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 商户 API 签名密钥生成器
 *
 * @author MLChong
 */
public class SecretGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretGenerator() {
    }

    /**
     * 生成 64 位小写十六进制随机密钥（256 bit 熵）
     */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
