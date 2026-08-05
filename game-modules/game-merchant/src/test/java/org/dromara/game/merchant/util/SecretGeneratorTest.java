package org.dromara.game.merchant.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("商户密钥生成器")
class SecretGeneratorTest {

    @Test
    @DisplayName("生成64位小写十六进制字符串")
    void generate_returns64LowerHex() {
        String secret = SecretGenerator.generate();
        assertEquals(64, secret.length());
        assertTrue(secret.matches("^[0-9a-f]{64}$"));
    }

    @Test
    @DisplayName("两次生成结果不同")
    void generate_isRandom() {
        assertNotEquals(SecretGenerator.generate(), SecretGenerator.generate());
    }
}
