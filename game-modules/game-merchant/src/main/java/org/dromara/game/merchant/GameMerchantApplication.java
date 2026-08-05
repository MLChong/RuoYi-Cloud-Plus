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
        // dubbo-common 的 JsonUtils 通过 ServiceLoader 依次探测 fastjson2/fastjson/gson/jackson 四个内置实现，
        // 未选中的实现若其依赖库缺失也会在探测阶段抛 NoClassDefFoundError（ServiceLoader.hasNext() 的异常未被
        // 该方法捕获），与业务是否用到该 JSON 库无关。显式指定优先使用 classpath 已有的 fastjson2，
        // 使探测在第一个候选即命中返回，跳过后续未装库的候选，避免服务导出阶段启动失败。
        System.setProperty("dubbo.json-framework.prefer", "fastjson2");

        SpringApplication application = new SpringApplication(GameMerchantApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
        System.out.println("(♥◠‿◠)ﾉﾞ  商户服务启动成功   ლ(´ڡ`ლ)ﾞ  ");
    }
}
