package org.dromara.game.merchant;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

/**
 * 商户服务
 *
 * @author MLChong
 */
@EnableDubbo
@SpringBootApplication
public class GameMerchantApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(GameMerchantApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
        System.out.println("(♥◠‿◠)ﾉﾞ  商户服务启动成功   ლ(´ڡ`ლ)ﾞ  ");
    }
}
