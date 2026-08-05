# 一期 Plan A：工程骨架 + 商户服务（game-merchant）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭起 game-modules/game-api 工程骨架，交付第一个可启动、可测试的业务服务 game-merchant（商户 + 玩家档案 + Dubbo 契约），为后续 wallet/lottery/openapi 计划提供地基。

**Architecture:** 遵循 spec `docs/superpowers/specs/2026-08-04-lottery-platform-modules-design.md`。业务代码放独立顶层目录 `game-modules/`（服务）与 `game-api/`（Dubbo 契约），与上游 2.X 隔离；game-merchant 完全复刻 ruoyi-system 的分层范式（domain/bo/vo/mapper/service/controller/dubbo）。

**Tech Stack:** JDK 17、Spring Boot 3.5.15、RuoYi-Cloud-Plus 2.6.2（`${revision}`）、MyBatis-Plus（BaseMapperPlus + @AutoMapper/mapstruct-plus）、Dubbo 3、Sa-Token、Nacos、JUnit5 + Mockito（spring-boot-starter-test）。

## 一期计划总览（本文件 = Plan A）

| 计划 | 内容 | 状态 |
|---|---|---|
| **A（本计划）** | 工程骨架 + game-merchant 商户服务 | 待执行 |
| B | ruoyi-common-mq 封装(RocketMQ) + game-wallet TRANSFER 钱包 | 待撰写 |
| C | game-lottery 彩种/期号/投注/RNG开奖/结算（极速快三闭环） | 待撰写 |
| D | game-openapi 验签框架 + launch + transfer API | 待撰写 |
| E | 游戏 H5 前端（token 换会话、投注、SSE 开奖） | 待撰写 |
| F | plus-ui 后台页面 + 菜单权限 SQL | 待撰写 |

每份计划独立交付可运行、可测试的软件；B 依赖 A 的骨架，C 依赖 B 的钱包，D 依赖 A+B，E 依赖 D。

## Global Constraints（所有 Task 隐含遵守）

- **上游隔离**：只允许新建/修改 `game-modules/`、`game-api/`、`script/sql/game/`、`script/config/nacos/game-*.yml`、`docs/`。唯一例外：根 `pom.xml` 的 `<modules>` 追加两行（Task 1）。
- **包名**：`org.dromara.game.<service>.*`；groupId `org.dromara`；版本一律 `${revision}`（当前 2.6.2）。
- **租户**：整站单品牌，游戏服务**不引入** `ruoyi-common-tenant`，实体**不映射** tenant_id；DDL 保留 `tenant_id varchar(20) default '000000'` 列以备将来。
- **金额**：`decimal(24,6)`，Java 侧 `BigDecimal`；资金类字段禁止直接 set 覆盖，只能原子增减（SQL `prepaid_balance = prepaid_balance + ?` 带非负守卫）。
- **ID**：主键 `bigint(20)`，雪花 ID（框架 MyBatis-Plus ASSIGN_ID 默认，实体 `@TableId` 即可，插入时不手工赋值）。
- **删除**：商户表用逻辑删除（`del_flag` + `@TableLogic`）；玩家表不提供删除。
- **端口/命名**：game-merchant 端口 **9301**（9302 wallet、9303 lottery、9304 openapi、9305 report 预留）；数据库 `game_merchant`。
- **DDL 风格**：全小写、`engine=innodb`、审计列 `create_dept/create_by/create_time/update_by/update_time/remark` 与上游 `ry-cloud.sql` 完全一致。
- **测试**：业务规则用纯 Mockito 单测（不起 Spring 上下文、不依赖 nacos/mysql）；测试命令 `mvn -q test -pl game-modules/game-merchant -am -Dtest=<TestClass>`。
- **⚠️ 勘误（执行期发现，覆盖本计划所有测试命令）**：根 pom 全局 `<skipTests>true</skipTests>` 且 surefire `<groups>${profiles.active}</groups>` 按标签过滤。因此：① 本计划中所有 `mvn … test` 命令**必须追加 `-DskipTests=false`**；② 每个测试类**必须加 `@Tag("dev")`**（`org.junit.jupiter.api.Tag`），否则测试静默不执行（"Tests run: 0" 或无输出，貌似绿灯实为空跑）。验证真跑了的标准：输出含 `Tests run: N`（N>0）。
- **提交**：跟随仓库风格 `add 新增 …` / `update 优化 …` / `fix 修复 …`，末尾带 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`。
- **本地栈**：运行时验证需要本地 docker 栈（nacos dev namespace、mysql root/password@3306、redis），见 memory《Run full stack on macOS》；纯编译/单测不需要。

---

### Task 1: Maven 工程骨架（game-api / game-modules 聚合模块）

**Files:**
- Create: `game-api/pom.xml`
- Create: `game-modules/pom.xml`
- Modify: 根 `pom.xml`（`<modules>` 追加两行）

**Interfaces:**
- Consumes: 无
- Produces: maven 聚合结构；后续 Task 的子模块挂在 `game-api`、`game-modules` 下

- [ ] **Step 1: 创建 `game-api/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>org.dromara</groupId>
        <artifactId>ruoyi-cloud-plus</artifactId>
        <version>${revision}</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>
    <artifactId>game-api</artifactId>
    <packaging>pom</packaging>

    <description>
        game-api 彩票游戏平台 Dubbo 接口模块
    </description>

    <modules>
        <module>game-api-merchant</module>
    </modules>
</project>
```

- [ ] **Step 2: 创建 `game-modules/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>org.dromara</groupId>
        <artifactId>ruoyi-cloud-plus</artifactId>
        <version>${revision}</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>
    <artifactId>game-modules</artifactId>
    <packaging>pom</packaging>

    <description>
        game-modules 彩票游戏平台业务模块
    </description>

    <modules>
        <module>game-merchant</module>
    </modules>
</project>
```

- [ ] **Step 3: 根 `pom.xml` 注册模块**

在根 `pom.xml` 的 `<modules>` 块末尾（`<module>ruoyi-example</module>` 之后）追加：

```xml
        <module>game-api</module>
        <module>game-modules</module>
```

- [ ] **Step 4: 先建出两个空的子模块目录占位（Task 2/3 会填内容），否则聚合器 validate 会失败**

本 Task 只建 `game-api/game-api-merchant/pom.xml` 与 `game-modules/game-merchant/pom.xml` 的**最小壳**：

`game-api/game-api-merchant/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>org.dromara</groupId>
        <artifactId>game-api</artifactId>
        <version>${revision}</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>
    <artifactId>game-api-merchant</artifactId>

    <description>
        game-api-merchant 商户服务接口模块
    </description>

    <dependencies>
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-core</artifactId>
        </dependency>
    </dependencies>
</project>
```

`game-modules/game-merchant/pom.xml`（依赖在 Task 3 补全，先给可编译最小集）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>org.dromara</groupId>
        <artifactId>game-modules</artifactId>
        <version>${revision}</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>
    <artifactId>game-merchant</artifactId>

    <description>
        game-merchant 商户服务
    </description>
</project>
```

- [ ] **Step 5: 验证**

Run: `mvn -q validate`
Expected: `BUILD SUCCESS`（reactor 列表包含 game-api、game-api-merchant、game-modules、game-merchant）

- [ ] **Step 6: Commit**

```bash
git add pom.xml game-api game-modules
git commit -m "add 新增 game-api/game-modules 工程骨架

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: game-api-merchant 契约（Dubbo 接口 + Vo）

**Files:**
- Create: `game-api/game-api-merchant/src/main/java/org/dromara/game/merchant/api/RemoteMerchantService.java`
- Create: `game-api/game-api-merchant/src/main/java/org/dromara/game/merchant/api/RemotePlayerService.java`
- Create: `game-api/game-api-merchant/src/main/java/org/dromara/game/merchant/api/domain/vo/RemoteMerchantVo.java`
- Create: `game-api/game-api-merchant/src/main/java/org/dromara/game/merchant/api/domain/vo/RemotePlayerVo.java`

**Interfaces:**
- Consumes: 无
- Produces（Plan B/C/D 的消费契约，签名不可改）:
  - `RemoteMerchantVo getByCode(String merchantCode)` — 返回 null 表示商户不存在
  - `RemotePlayerVo getOrCreate(Long merchantId, String externalPlayerId)` — 商户停用/玩家停用抛 `ServiceException`

- [ ] **Step 1: 写 `RemoteMerchantVo.java`**

```java
package org.dromara.game.merchant.api.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 商户远程视图对象（含 secret，仅限服务间调用，严禁透出到任何 HTTP 响应）
 *
 * @author MLChong
 */
@Data
public class RemoteMerchantVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商户ID */
    private Long merchantId;

    /** 商户编码 */
    private String merchantCode;

    /** 商户名称 */
    private String merchantName;

    /** 状态（0正常 1停用） */
    private String status;

    /** API 签名密钥 */
    private String secret;

    /** 钱包模式（TRANSFER/SEAMLESS） */
    private String walletMode;

    /** seamless 回调地址 */
    private String callbackUrl;

    /** 支持币种列表（大写 ISO 码，如 CNY/USDT） */
    private List<String> currencies;

    /** 费率（0~1 小数） */
    private BigDecimal feeRate;

    /** 预存额度余额 */
    private BigDecimal prepaidBalance;
}
```

- [ ] **Step 2: 写 `RemotePlayerVo.java`**

```java
package org.dromara.game.merchant.api.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 玩家远程视图对象
 *
 * @author MLChong
 */
@Data
public class RemotePlayerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 玩家ID */
    private Long playerId;

    /** 所属商户ID */
    private Long merchantId;

    /** 商户侧玩家标识 */
    private String externalPlayerId;

    /** 状态（0正常 1停用） */
    private String status;
}
```

- [ ] **Step 3: 写两个接口**

`RemoteMerchantService.java`：

```java
package org.dromara.game.merchant.api;

import org.dromara.game.merchant.api.domain.vo.RemoteMerchantVo;

/**
 * 商户远程服务
 *
 * @author MLChong
 */
public interface RemoteMerchantService {

    /**
     * 按商户编码查询商户（含 secret，供 openapi 验签）
     *
     * @param merchantCode 商户编码
     * @return 商户信息，不存在返回 null
     */
    RemoteMerchantVo getByCode(String merchantCode);
}
```

`RemotePlayerService.java`：

```java
package org.dromara.game.merchant.api;

import org.dromara.game.merchant.api.domain.vo.RemotePlayerVo;

/**
 * 玩家远程服务
 *
 * @author MLChong
 */
public interface RemotePlayerService {

    /**
     * 查询或自动建档玩家（launch 链路调用，并发安全）
     *
     * @param merchantId       商户ID
     * @param externalPlayerId 商户侧玩家标识
     * @return 玩家信息
     */
    RemotePlayerVo getOrCreate(Long merchantId, String externalPlayerId);
}
```

- [ ] **Step 4: 编译验证**

Run: `mvn -q compile -pl game-api/game-api-merchant -am`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add game-api/game-api-merchant
git commit -m "add 新增 game-api-merchant 商户/玩家 Dubbo 契约

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: game-merchant 服务骨架（可启动、可注册）

**Files:**
- Modify: `game-modules/game-merchant/pom.xml`（补全依赖）
- Create: `game-modules/game-merchant/src/main/java/org/dromara/game/merchant/GameMerchantApplication.java`
- Create: `game-modules/game-merchant/src/main/resources/application.yml`
- Create: `game-modules/game-merchant/src/main/resources/logback-plus.xml`（复制自 ruoyi-system）
- Create: `script/config/nacos/game-merchant.yml`（nacos 配置种子文件）

**Interfaces:**
- Consumes: Task 1 骨架
- Produces: 可启动并注册到 nacos 的空服务，Task 5+ 在其中填业务

- [ ] **Step 1: 补全 `game-modules/game-merchant/pom.xml` 的依赖与构建块**

在 `<description>` 之后追加（照抄，注意不含 tenant/seata）：

```xml
    <dependencies>
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-nacos</artifactId>
        </dependency>
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-log</artifactId>
        </dependency>
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-doc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-mybatis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-dubbo</artifactId>
        </dependency>
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-idempotent</artifactId>
        </dependency>
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-translation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>game-api-merchant</artifactId>
            <version>${revision}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>${project.artifactId}</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot.version}</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
```

- [ ] **Step 2: 写启动类 `GameMerchantApplication.java`**

```java
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
```

- [ ] **Step 3: 写 `application.yml`**

```yaml
# Tomcat
server:
  port: 9301

# Spring
spring:
  application:
    # 应用名称
    name: game-merchant
  profiles:
    # 环境配置
    active: @profiles.active@

--- # nacos 配置
spring:
  cloud:
    nacos:
      # nacos 服务地址
      server-addr: @nacos.server@
      username: @nacos.username@
      password: @nacos.password@
      discovery:
        # 注册组
        group: @nacos.discovery.group@
        namespace: ${spring.profiles.active}
      config:
        # 配置组
        group: @nacos.config.group@
        namespace: ${spring.profiles.active}
  config:
    import:
      - optional:nacos:application-common.yml
      - optional:nacos:datasource.yml
      - optional:nacos:${spring.application.name}.yml
```

- [ ] **Step 4: 复制日志配置**

Run: `cp ruoyi-modules/ruoyi-system/src/main/resources/logback-plus.xml game-modules/game-merchant/src/main/resources/logback-plus.xml`

- [ ] **Step 5: 写 nacos 配置种子 `script/config/nacos/game-merchant.yml`**

数据源自包含（不改上游 `datasource.yml`），`${spring.datasource.type}` 与 hikari 参数由共享 `datasource.yml` 提供：

```yaml
spring:
  datasource:
    dynamic:
      # 设置默认的数据源或者数据源组,默认值即为 master
      primary: master
      datasource:
        # 主库数据源
        master:
          type: ${spring.datasource.type}
          driver-class-name: com.mysql.cj.jdbc.Driver
          url: jdbc:mysql://localhost:3306/game_merchant?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8&rewriteBatchedStatements=true&allowPublicKeyRetrieval=true
          username: root
          password: password
```

- [ ] **Step 6: 把配置发布到 nacos（dev namespace）**

打开 nacos 控制台（本地栈 `http://localhost:8848/nacos`）→ 命名空间切到 `dev` → 配置管理 → 新建配置：Data ID `game-merchant.yml`、Group 与现有 `ruoyi-system.yml` 相同、格式 YAML、内容粘贴 Step 5 文件全文并发布。（若栈未启动，本步骤与 Step 8 顺延到 Task 8 冒烟时执行。）

- [ ] **Step 7: 编译打包验证**

Run: `mvn -q -DskipTests package -pl game-modules/game-merchant -am`
Expected: `BUILD SUCCESS`

- [ ] **Step 8: 启动验证（需本地栈：nacos/mysql/redis 已起，且 Task 4 的库已建）**

Run: `mvn -q -DskipTests install -pl game-modules/game-merchant -am`（先把依赖装入本地仓库，spring-boot:run 不能带 -am 否则会在聚合 pom 上执行失败）
Run: `mvn spring-boot:run -pl game-modules/game-merchant -DskipTests`
Expected: 控制台出现 `商户服务启动成功`；nacos 控制台 dev 命名空间服务列表出现 `game-merchant`。Ctrl-C 停止。

- [ ] **Step 9: Commit**

```bash
git add game-modules/game-merchant script/config/nacos/game-merchant.yml
git commit -m "add 新增 game-merchant 服务骨架(9301) 与 nacos 配置

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: game_merchant 数据库 DDL

**Files:**
- Create: `script/sql/game/game_merchant.sql`

**Interfaces:**
- Consumes: 无
- Produces: `merchant`、`merchant_player` 两表，Task 5/6 的实体与之一一对应

- [ ] **Step 1: 写 `script/sql/game/game_merchant.sql`**

```sql
-- ----------------------------
-- 彩票游戏供应商平台 商户库
-- ----------------------------
create database if not exists `game_merchant` default character set utf8mb4 collate utf8mb4_general_ci;
use `game_merchant`;

-- ----------------------------
-- 商户表
-- ----------------------------
drop table if exists merchant;
create table merchant (
  merchant_id       bigint(20)      not null                   comment '商户ID',
  tenant_id         varchar(20)     default '000000'           comment '租户编号',
  merchant_code     varchar(32)     not null                   comment '商户编码',
  merchant_name     varchar(50)     not null                   comment '商户名称',
  status            char(1)         default '0'                comment '状态（0正常 1停用）',
  secret            varchar(64)     not null                   comment 'API签名密钥',
  wallet_mode       varchar(20)     not null default 'TRANSFER' comment '钱包模式（TRANSFER/SEAMLESS）',
  callback_url      varchar(500)    default null               comment 'seamless回调地址',
  currencies        varchar(200)    not null                   comment '支持币种（逗号分隔大写ISO码）',
  fee_rate          decimal(8,6)    not null default 0         comment '费率（0~1）',
  prepaid_balance   decimal(24,6)   not null default 0         comment '预存额度余额',
  ip_whitelist      varchar(500)    default null               comment 'IP白名单（逗号分隔）',
  del_flag          char(1)         default '0'                comment '删除标志（0存在 1删除）',
  create_dept       bigint(20)      default null               comment '创建部门',
  create_by         bigint(20)      default null               comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         bigint(20)      default null               comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(255)    default null               comment '备注',
  primary key (merchant_id),
  unique key uk_merchant_code (merchant_code)
) engine=innodb comment = '商户表';

-- ----------------------------
-- 玩家档案表
-- ----------------------------
drop table if exists merchant_player;
create table merchant_player (
  player_id           bigint(20)      not null                 comment '玩家ID',
  tenant_id           varchar(20)     default '000000'         comment '租户编号',
  merchant_id         bigint(20)      not null                 comment '所属商户ID',
  external_player_id  varchar(64)     not null                 comment '商户侧玩家标识',
  status              char(1)         default '0'              comment '状态（0正常 1停用）',
  create_dept         bigint(20)      default null             comment '创建部门',
  create_by           bigint(20)      default null             comment '创建者',
  create_time         datetime                                 comment '创建时间',
  update_by           bigint(20)      default null             comment '更新者',
  update_time         datetime                                 comment '更新时间',
  remark              varchar(255)    default null             comment '备注',
  primary key (player_id),
  unique key uk_merchant_external (merchant_id, external_player_id)
) engine=innodb comment = '玩家档案表';
```

- [ ] **Step 2: 建库验证（需本地 mysql）**

Run: `mysql -h127.0.0.1 -uroot -ppassword < script/sql/game/game_merchant.sql && mysql -h127.0.0.1 -uroot -ppassword -e "show tables from game_merchant;"`
Expected: 输出 `merchant` 与 `merchant_player`

- [ ] **Step 3: Commit**

```bash
git add script/sql/game/game_merchant.sql
git commit -m "add 新增 game_merchant 库 DDL(商户/玩家档案)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: 商户域（实体/Mapper/Service/Controller，TDD）

**Files:**
- Create: `game-modules/game-merchant/src/main/java/org/dromara/game/merchant/util/SecretGenerator.java`
- Create: `game-modules/game-merchant/src/main/java/org/dromara/game/merchant/domain/Merchant.java`
- Create: `game-modules/game-merchant/src/main/java/org/dromara/game/merchant/domain/bo/MerchantBo.java`
- Create: `game-modules/game-merchant/src/main/java/org/dromara/game/merchant/domain/vo/MerchantVo.java`
- Create: `game-modules/game-merchant/src/main/java/org/dromara/game/merchant/mapper/MerchantMapper.java`
- Create: `game-modules/game-merchant/src/main/java/org/dromara/game/merchant/service/IMerchantService.java`
- Create: `game-modules/game-merchant/src/main/java/org/dromara/game/merchant/service/impl/MerchantServiceImpl.java`
- Create: `game-modules/game-merchant/src/main/java/org/dromara/game/merchant/controller/MerchantController.java`
- Test: `game-modules/game-merchant/src/test/java/org/dromara/game/merchant/util/SecretGeneratorTest.java`
- Test: `game-modules/game-merchant/src/test/java/org/dromara/game/merchant/service/MerchantServiceTest.java`

**Interfaces:**
- Consumes: Task 4 的表结构
- Produces（Task 6/7 与 Plan B/D 依赖，签名不可改）:
  - `IMerchantService`：`TableDataInfo<MerchantVo> queryPageList(MerchantBo bo, PageQuery pageQuery)`、`MerchantVo queryById(Long merchantId)`、`int insertMerchant(MerchantBo bo)`、`int updateMerchant(MerchantBo bo)`、`String resetSecret(Long merchantId)`、`void adjustPrepaid(Long merchantId, BigDecimal delta)`、`int deleteByIds(Long[] merchantIds)`、`Merchant getByCode(String merchantCode)`
  - `SecretGenerator.generate()`：返回 64 位小写十六进制随机串

- [ ] **Step 1: 写失败测试 `SecretGeneratorTest.java`**

```java
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
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q test -pl game-modules/game-merchant -am -Dtest=SecretGeneratorTest`
Expected: `COMPILATION ERROR`（`SecretGenerator` 不存在）——即红灯

- [ ] **Step 3: 实现 `SecretGenerator.java`**

```java
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
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q test -pl game-modules/game-merchant -am -Dtest=SecretGeneratorTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: 写实体/Bo/Vo/Mapper（无业务逻辑，直接写）**

`Merchant.java`：

```java
package org.dromara.game.merchant.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;

/**
 * 商户表 merchant
 *
 * @author MLChong
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("merchant")
public class Merchant extends BaseEntity {

    /**
     * 商户ID
     */
    @TableId(value = "merchant_id")
    private Long merchantId;

    /**
     * 商户编码
     */
    private String merchantCode;

    /**
     * 商户名称
     */
    private String merchantName;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * API签名密钥
     */
    private String secret;

    /**
     * 钱包模式（TRANSFER/SEAMLESS）
     */
    private String walletMode;

    /**
     * seamless回调地址
     */
    private String callbackUrl;

    /**
     * 支持币种（逗号分隔大写ISO码）
     */
    private String currencies;

    /**
     * 费率（0~1）
     */
    private BigDecimal feeRate;

    /**
     * 预存额度余额
     */
    private BigDecimal prepaidBalance;

    /**
     * IP白名单（逗号分隔）
     */
    private String ipWhitelist;

    /**
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;
}
```

`MerchantBo.java`（注意：**没有** secret 与 prepaidBalance 字段——密钥只能重置、额度只能原子增减）：

```java
package org.dromara.game.merchant.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.game.merchant.domain.Merchant;

import java.math.BigDecimal;

/**
 * 商户业务对象 merchant
 *
 * @author MLChong
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = Merchant.class, reverseConvertGenerate = false)
public class MerchantBo extends BaseEntity {

    /**
     * 商户ID
     */
    private Long merchantId;

    /**
     * 商户编码（4-32位字母数字下划线）
     */
    @NotBlank(message = "商户编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$", message = "商户编码须为4-32位字母数字下划线")
    private String merchantCode;

    /**
     * 商户名称
     */
    @NotBlank(message = "商户名称不能为空")
    @Size(max = 50, message = "商户名称不能超过{max}个字符")
    private String merchantName;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 钱包模式（TRANSFER/SEAMLESS）
     */
    @NotBlank(message = "钱包模式不能为空")
    @Pattern(regexp = "^(TRANSFER|SEAMLESS)$", message = "钱包模式仅支持TRANSFER/SEAMLESS")
    private String walletMode;

    /**
     * seamless回调地址
     */
    @Size(max = 500, message = "回调地址不能超过{max}个字符")
    private String callbackUrl;

    /**
     * 支持币种（逗号分隔大写ISO码）
     */
    @NotBlank(message = "支持币种不能为空")
    @Pattern(regexp = "^[A-Z]{3,10}(,[A-Z]{3,10})*$", message = "币种须为逗号分隔的大写代码")
    private String currencies;

    /**
     * 费率（0~1）
     */
    @NotNull(message = "费率不能为空")
    @DecimalMin(value = "0", message = "费率不能小于0")
    @DecimalMax(value = "1", message = "费率不能大于1")
    private BigDecimal feeRate;

    /**
     * IP白名单（逗号分隔）
     */
    @Size(max = 500, message = "IP白名单不能超过{max}个字符")
    private String ipWhitelist;

    /**
     * 备注
     */
    private String remark;
}
```

`MerchantVo.java`（**没有** secret 字段，密钥绝不出现在查询响应里）：

```java
package org.dromara.game.merchant.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.game.merchant.domain.Merchant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 商户视图对象 merchant
 *
 * @author MLChong
 */
@Data
@AutoMapper(target = Merchant.class)
public class MerchantVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 商户ID
     */
    private Long merchantId;

    /**
     * 商户编码
     */
    private String merchantCode;

    /**
     * 商户名称
     */
    private String merchantName;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 钱包模式（TRANSFER/SEAMLESS）
     */
    private String walletMode;

    /**
     * seamless回调地址
     */
    private String callbackUrl;

    /**
     * 支持币种（逗号分隔大写ISO码）
     */
    private String currencies;

    /**
     * 费率（0~1）
     */
    private BigDecimal feeRate;

    /**
     * 预存额度余额
     */
    private BigDecimal prepaidBalance;

    /**
     * IP白名单（逗号分隔）
     */
    private String ipWhitelist;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private Date createTime;
}
```

`MerchantMapper.java`：

```java
package org.dromara.game.merchant.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.game.merchant.domain.Merchant;
import org.dromara.game.merchant.domain.vo.MerchantVo;

/**
 * 商户表 数据层
 *
 * @author MLChong
 */
public interface MerchantMapper extends BaseMapperPlus<Merchant, MerchantVo> {

}
```

- [ ] **Step 6: 写失败测试 `MerchantServiceTest.java`**

```java
package org.dromara.game.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.game.merchant.domain.Merchant;
import org.dromara.game.merchant.domain.bo.MerchantBo;
import org.dromara.game.merchant.mapper.MerchantMapper;
import org.dromara.game.merchant.service.impl.MerchantServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("商户服务")
@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantMapper baseMapper;

    @InjectMocks
    private MerchantServiceImpl service;

    private MerchantBo validBo() {
        MerchantBo bo = new MerchantBo();
        bo.setMerchantCode("mch_test01");
        bo.setMerchantName("测试商户");
        bo.setWalletMode("TRANSFER");
        bo.setCurrencies("CNY,USDT");
        bo.setFeeRate(new BigDecimal("0.08"));
        return bo;
    }

    @Test
    @DisplayName("新增：编码已存在则抛异常")
    void insert_duplicateCode_throws() {
        when(baseMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);
        assertThrows(ServiceException.class, () -> service.insertMerchant(validBo()));
        verify(baseMapper, never()).insert(any(Merchant.class));
    }

    @Test
    @DisplayName("新增：自动生成64位密钥、默认状态0、额度0")
    void insert_generatesSecretAndDefaults() {
        when(baseMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);
        when(baseMapper.insert(any(Merchant.class))).thenReturn(1);

        int rows = service.insertMerchant(validBo());

        assertEquals(1, rows);
        ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
        verify(baseMapper).insert(captor.capture());
        Merchant saved = captor.getValue();
        assertTrue(saved.getSecret().matches("^[0-9a-f]{64}$"));
        assertEquals("0", saved.getStatus());
        assertEquals(BigDecimal.ZERO, saved.getPrepaidBalance());
    }

    @Test
    @DisplayName("修改：不允许变更编码与密钥")
    void update_neverTouchesCodeAndSecret() {
        MerchantBo bo = validBo();
        bo.setMerchantId(1L);
        when(baseMapper.updateById(any(Merchant.class))).thenReturn(1);

        service.updateMerchant(bo);

        ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
        verify(baseMapper).updateById(captor.capture());
        assertNull(captor.getValue().getMerchantCode());
        assertNull(captor.getValue().getSecret());
    }

    @Test
    @DisplayName("重置密钥：返回新64位密钥")
    void resetSecret_returnsNewSecret() {
        Merchant db = new Merchant();
        db.setMerchantId(1L);
        db.setSecret("old");
        when(baseMapper.selectById(1L)).thenReturn(db);
        when(baseMapper.updateById(any(Merchant.class))).thenReturn(1);

        String secret = service.resetSecret(1L);

        assertTrue(secret.matches("^[0-9a-f]{64}$"));
    }

    @Test
    @DisplayName("重置密钥：商户不存在抛异常")
    void resetSecret_notFound_throws() {
        when(baseMapper.selectById(9L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.resetSecret(9L));
    }

    @Test
    @DisplayName("额度调整：余额不足（原子守卫更新0行）抛异常")
    void adjustPrepaid_insufficient_throws() {
        when(baseMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        assertThrows(ServiceException.class,
            () -> service.adjustPrepaid(1L, new BigDecimal("-100")));
    }

    @Test
    @DisplayName("额度调整：成功")
    void adjustPrepaid_ok() {
        when(baseMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        assertDoesNotThrow(() -> service.adjustPrepaid(1L, new BigDecimal("100")));
    }

    @Test
    @DisplayName("额度调整：delta为0直接拒绝")
    void adjustPrepaid_zero_throws() {
        assertThrows(ServiceException.class,
            () -> service.adjustPrepaid(1L, BigDecimal.ZERO));
        verifyNoInteractions(baseMapper);
    }
}
```

- [ ] **Step 7: 运行确认失败**

Run: `mvn -q test -pl game-modules/game-merchant -am -Dtest=MerchantServiceTest`
Expected: `COMPILATION ERROR`（`IMerchantService`/`MerchantServiceImpl` 不存在）

- [ ] **Step 8: 实现 `IMerchantService.java` + `MerchantServiceImpl.java`**

`IMerchantService.java`：

```java
package org.dromara.game.merchant.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.game.merchant.domain.Merchant;
import org.dromara.game.merchant.domain.bo.MerchantBo;
import org.dromara.game.merchant.domain.vo.MerchantVo;

import java.math.BigDecimal;

/**
 * 商户服务层
 *
 * @author MLChong
 */
public interface IMerchantService {

    /**
     * 分页查询商户列表
     */
    TableDataInfo<MerchantVo> queryPageList(MerchantBo bo, PageQuery pageQuery);

    /**
     * 按ID查询商户
     */
    MerchantVo queryById(Long merchantId);

    /**
     * 按编码查询商户（含secret，服务内部使用）
     */
    Merchant getByCode(String merchantCode);

    /**
     * 新增商户（自动生成密钥）
     */
    int insertMerchant(MerchantBo bo);

    /**
     * 修改商户（编码与密钥不可修改）
     */
    int updateMerchant(MerchantBo bo);

    /**
     * 重置密钥并返回新密钥（仅此一次透出）
     */
    String resetSecret(Long merchantId);

    /**
     * 原子调整预存额度（delta 可正可负，结果不允许为负）
     */
    void adjustPrepaid(Long merchantId, BigDecimal delta);

    /**
     * 批量逻辑删除
     */
    int deleteByIds(Long[] merchantIds);
}
```

`MerchantServiceImpl.java`：

```java
package org.dromara.game.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.game.merchant.domain.Merchant;
import org.dromara.game.merchant.domain.bo.MerchantBo;
import org.dromara.game.merchant.domain.vo.MerchantVo;
import org.dromara.game.merchant.mapper.MerchantMapper;
import org.dromara.game.merchant.service.IMerchantService;
import org.dromara.game.merchant.util.SecretGenerator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * 商户服务层实现
 *
 * @author MLChong
 */
@RequiredArgsConstructor
@Service
public class MerchantServiceImpl implements IMerchantService {

    private final MerchantMapper baseMapper;

    @Override
    public TableDataInfo<MerchantVo> queryPageList(MerchantBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<Merchant> lqw = buildQueryWrapper(bo);
        Page<MerchantVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    private LambdaQueryWrapper<Merchant> buildQueryWrapper(MerchantBo bo) {
        LambdaQueryWrapper<Merchant> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getMerchantCode()), Merchant::getMerchantCode, bo.getMerchantCode());
        lqw.like(StringUtils.isNotBlank(bo.getMerchantName()), Merchant::getMerchantName, bo.getMerchantName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), Merchant::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getWalletMode()), Merchant::getWalletMode, bo.getWalletMode());
        lqw.orderByDesc(Merchant::getMerchantId);
        return lqw;
    }

    @Override
    public MerchantVo queryById(Long merchantId) {
        return baseMapper.selectVoById(merchantId);
    }

    @Override
    public Merchant getByCode(String merchantCode) {
        return baseMapper.selectOne(Wrappers.<Merchant>lambdaQuery()
            .eq(Merchant::getMerchantCode, merchantCode));
    }

    @Override
    public int insertMerchant(MerchantBo bo) {
        boolean exists = baseMapper.exists(Wrappers.<Merchant>lambdaQuery()
            .eq(Merchant::getMerchantCode, bo.getMerchantCode()));
        if (exists) {
            throw new ServiceException("商户编码已存在: " + bo.getMerchantCode());
        }
        Merchant merchant = MapstructUtils.convert(bo, Merchant.class);
        merchant.setSecret(SecretGenerator.generate());
        merchant.setStatus("0");
        merchant.setPrepaidBalance(BigDecimal.ZERO);
        return baseMapper.insert(merchant);
    }

    @Override
    public int updateMerchant(MerchantBo bo) {
        Merchant merchant = MapstructUtils.convert(bo, Merchant.class);
        // 编码与密钥不可通过修改接口变更
        merchant.setMerchantCode(null);
        merchant.setSecret(null);
        return baseMapper.updateById(merchant);
    }

    @Override
    public String resetSecret(Long merchantId) {
        Merchant db = baseMapper.selectById(merchantId);
        if (db == null) {
            throw new ServiceException("商户不存在: " + merchantId);
        }
        String secret = SecretGenerator.generate();
        Merchant update = new Merchant();
        update.setMerchantId(merchantId);
        update.setSecret(secret);
        baseMapper.updateById(update);
        return secret;
    }

    @Override
    public void adjustPrepaid(Long merchantId, BigDecimal delta) {
        if (delta == null || delta.signum() == 0) {
            throw new ServiceException("调整金额不能为空或0");
        }
        LambdaUpdateWrapper<Merchant> luw = Wrappers.<Merchant>lambdaUpdate()
            .setSql("prepaid_balance = prepaid_balance + {0}", delta)
            .eq(Merchant::getMerchantId, merchantId)
            .apply("prepaid_balance + {0} >= 0", delta);
        int rows = baseMapper.update(null, luw);
        if (rows == 0) {
            throw new ServiceException("额度调整失败：商户不存在或余额不足");
        }
    }

    @Override
    public int deleteByIds(Long[] merchantIds) {
        return baseMapper.deleteByIds(Arrays.asList(merchantIds));
    }
}
```

- [ ] **Step 9: 运行确认通过**

Run: `mvn -q test -pl game-modules/game-merchant -am -Dtest=MerchantServiceTest`
Expected: `Tests run: 8, Failures: 0, Errors: 0`

- [ ] **Step 10: 写 `MerchantController.java`（薄层，不写单测）**

```java
package org.dromara.game.merchant.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.game.merchant.domain.bo.MerchantBo;
import org.dromara.game.merchant.domain.vo.MerchantVo;
import org.dromara.game.merchant.service.IMerchantService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 商户管理
 *
 * @author MLChong
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/merchant")
public class MerchantController extends BaseController {

    private final IMerchantService merchantService;

    /**
     * 查询商户列表
     */
    @SaCheckPermission("game:merchant:list")
    @GetMapping("/list")
    public TableDataInfo<MerchantVo> list(MerchantBo bo, PageQuery pageQuery) {
        return merchantService.queryPageList(bo, pageQuery);
    }

    /**
     * 获取商户详细信息
     */
    @SaCheckPermission("game:merchant:query")
    @GetMapping("/{merchantId}")
    public R<MerchantVo> getInfo(@PathVariable Long merchantId) {
        return R.ok(merchantService.queryById(merchantId));
    }

    /**
     * 新增商户
     */
    @SaCheckPermission("game:merchant:add")
    @Log(title = "商户管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody MerchantBo bo) {
        return toAjax(merchantService.insertMerchant(bo));
    }

    /**
     * 修改商户
     */
    @SaCheckPermission("game:merchant:edit")
    @Log(title = "商户管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody MerchantBo bo) {
        return toAjax(merchantService.updateMerchant(bo));
    }

    /**
     * 重置商户密钥（新密钥仅在本响应中透出一次）
     */
    @SaCheckPermission("game:merchant:resetSecret")
    @Log(title = "商户管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/resetSecret/{merchantId}")
    public R<String> resetSecret(@PathVariable Long merchantId) {
        return R.ok("重置成功", merchantService.resetSecret(merchantId));
    }

    /**
     * 调整商户预存额度（正数增加 负数扣减）
     */
    @SaCheckPermission("game:merchant:prepaid")
    @Log(title = "商户管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/prepaid/{merchantId}")
    public R<Void> adjustPrepaid(@PathVariable Long merchantId,
                                 @NotNull(message = "调整金额不能为空") @RequestParam BigDecimal delta) {
        merchantService.adjustPrepaid(merchantId, delta);
        return R.ok();
    }

    /**
     * 删除商户
     */
    @SaCheckPermission("game:merchant:remove")
    @Log(title = "商户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{merchantIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] merchantIds) {
        return toAjax(merchantService.deleteByIds(merchantIds));
    }
}
```

- [ ] **Step 11: 全量编译 + 测试**

Run: `mvn -q test -pl game-modules/game-merchant -am`
Expected: `BUILD SUCCESS`，Tests run ≥ 10，全绿

- [ ] **Step 12: Commit**

```bash
git add game-modules/game-merchant
git commit -m "add 新增 商户管理(实体/服务/控制器) 密钥生成与预存额度原子调整

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: 玩家档案 getOrCreate（并发安全，TDD）

**Files:**
- Create: `game-modules/game-merchant/src/main/java/org/dromara/game/merchant/domain/MerchantPlayer.java`
- Create: `game-modules/game-merchant/src/main/java/org/dromara/game/merchant/domain/vo/PlayerVo.java`
- Create: `game-modules/game-merchant/src/main/java/org/dromara/game/merchant/mapper/MerchantPlayerMapper.java`
- Create: `game-modules/game-merchant/src/main/java/org/dromara/game/merchant/service/IPlayerService.java`
- Create: `game-modules/game-merchant/src/main/java/org/dromara/game/merchant/service/impl/PlayerServiceImpl.java`
- Create: `game-modules/game-merchant/src/main/java/org/dromara/game/merchant/controller/PlayerController.java`
- Test: `game-modules/game-merchant/src/test/java/org/dromara/game/merchant/service/PlayerServiceTest.java`

**Interfaces:**
- Consumes: Task 4 表结构、Task 5 无直接依赖
- Produces（Task 7 依赖）:
  - `IPlayerService`：`MerchantPlayer getOrCreate(Long merchantId, String externalPlayerId)`、`TableDataInfo<PlayerVo> queryPageList(Long merchantId, String externalPlayerId, PageQuery pageQuery)`、`int changeStatus(Long playerId, String status)`

- [ ] **Step 1: 写失败测试 `PlayerServiceTest.java`**

```java
package org.dromara.game.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.game.merchant.domain.MerchantPlayer;
import org.dromara.game.merchant.mapper.MerchantPlayerMapper;
import org.dromara.game.merchant.service.impl.PlayerServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("玩家档案服务")
@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private MerchantPlayerMapper baseMapper;

    @InjectMocks
    private PlayerServiceImpl service;

    private MerchantPlayer player(String status) {
        MerchantPlayer p = new MerchantPlayer();
        p.setPlayerId(100L);
        p.setMerchantId(1L);
        p.setExternalPlayerId("u001");
        p.setStatus(status);
        return p;
    }

    @Test
    @DisplayName("已存在则直接返回，不插入")
    void getOrCreate_existing_returns() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(player("0"));

        MerchantPlayer result = service.getOrCreate(1L, "u001");

        assertEquals(100L, result.getPlayerId());
        verify(baseMapper, never()).insert(any(MerchantPlayer.class));
    }

    @Test
    @DisplayName("不存在则自动建档")
    void getOrCreate_new_inserts() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(baseMapper.insert(any(MerchantPlayer.class))).thenReturn(1);

        MerchantPlayer result = service.getOrCreate(1L, "u001");

        assertEquals("u001", result.getExternalPlayerId());
        assertEquals("0", result.getStatus());
        verify(baseMapper).insert(any(MerchantPlayer.class));
    }

    @Test
    @DisplayName("并发撞唯一键：捕获DuplicateKey后重查返回")
    void getOrCreate_race_reselects() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class)))
            .thenReturn(null)
            .thenReturn(player("0"));
        when(baseMapper.insert(any(MerchantPlayer.class)))
            .thenThrow(new DuplicateKeyException("uk_merchant_external"));

        MerchantPlayer result = service.getOrCreate(1L, "u001");

        assertEquals(100L, result.getPlayerId());
        verify(baseMapper, times(2)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("玩家已停用则抛异常")
    void getOrCreate_disabled_throws() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(player("1"));
        assertThrows(ServiceException.class, () -> service.getOrCreate(1L, "u001"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q test -pl game-modules/game-merchant -am -Dtest=PlayerServiceTest`
Expected: `COMPILATION ERROR`

- [ ] **Step 3: 实现实体/Vo/Mapper/Service**

`MerchantPlayer.java`：

```java
package org.dromara.game.merchant.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 玩家档案表 merchant_player
 *
 * @author MLChong
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("merchant_player")
public class MerchantPlayer extends BaseEntity {

    /**
     * 玩家ID
     */
    @TableId(value = "player_id")
    private Long playerId;

    /**
     * 所属商户ID
     */
    private Long merchantId;

    /**
     * 商户侧玩家标识
     */
    private String externalPlayerId;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;
}
```

`PlayerVo.java`：

```java
package org.dromara.game.merchant.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.game.merchant.domain.MerchantPlayer;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 玩家档案视图对象 merchant_player
 *
 * @author MLChong
 */
@Data
@AutoMapper(target = MerchantPlayer.class)
public class PlayerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 玩家ID
     */
    private Long playerId;

    /**
     * 所属商户ID
     */
    private Long merchantId;

    /**
     * 商户侧玩家标识
     */
    private String externalPlayerId;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 创建时间
     */
    private Date createTime;
}
```

`MerchantPlayerMapper.java`：

```java
package org.dromara.game.merchant.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.game.merchant.domain.MerchantPlayer;
import org.dromara.game.merchant.domain.vo.PlayerVo;

/**
 * 玩家档案表 数据层
 *
 * @author MLChong
 */
public interface MerchantPlayerMapper extends BaseMapperPlus<MerchantPlayer, PlayerVo> {

}
```

`IPlayerService.java`：

```java
package org.dromara.game.merchant.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.game.merchant.domain.MerchantPlayer;
import org.dromara.game.merchant.domain.vo.PlayerVo;

/**
 * 玩家档案服务层
 *
 * @author MLChong
 */
public interface IPlayerService {

    /**
     * 查询或自动建档（launch 链路，并发安全）；玩家停用抛 ServiceException
     */
    MerchantPlayer getOrCreate(Long merchantId, String externalPlayerId);

    /**
     * 后台分页查询
     */
    TableDataInfo<PlayerVo> queryPageList(Long merchantId, String externalPlayerId, PageQuery pageQuery);

    /**
     * 启用/停用玩家
     */
    int changeStatus(Long playerId, String status);
}
```

`PlayerServiceImpl.java`：

```java
package org.dromara.game.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.game.merchant.domain.MerchantPlayer;
import org.dromara.game.merchant.domain.vo.PlayerVo;
import org.dromara.game.merchant.mapper.MerchantPlayerMapper;
import org.dromara.game.merchant.service.IPlayerService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 玩家档案服务层实现
 *
 * @author MLChong
 */
@RequiredArgsConstructor
@Service
public class PlayerServiceImpl implements IPlayerService {

    private final MerchantPlayerMapper baseMapper;

    @Override
    public MerchantPlayer getOrCreate(Long merchantId, String externalPlayerId) {
        MerchantPlayer player = selectByUk(merchantId, externalPlayerId);
        if (player == null) {
            MerchantPlayer insert = new MerchantPlayer();
            insert.setMerchantId(merchantId);
            insert.setExternalPlayerId(externalPlayerId);
            insert.setStatus("0");
            try {
                baseMapper.insert(insert);
                player = insert;
            } catch (DuplicateKeyException e) {
                // 并发建档撞唯一键，重查即可
                player = selectByUk(merchantId, externalPlayerId);
            }
        }
        if (player == null) {
            throw new ServiceException("玩家建档失败");
        }
        if (!"0".equals(player.getStatus())) {
            throw new ServiceException("玩家已停用");
        }
        return player;
    }

    private MerchantPlayer selectByUk(Long merchantId, String externalPlayerId) {
        return baseMapper.selectOne(Wrappers.<MerchantPlayer>lambdaQuery()
            .eq(MerchantPlayer::getMerchantId, merchantId)
            .eq(MerchantPlayer::getExternalPlayerId, externalPlayerId));
    }

    @Override
    public TableDataInfo<PlayerVo> queryPageList(Long merchantId, String externalPlayerId, PageQuery pageQuery) {
        LambdaQueryWrapper<MerchantPlayer> lqw = Wrappers.lambdaQuery();
        lqw.eq(merchantId != null, MerchantPlayer::getMerchantId, merchantId);
        lqw.like(StringUtils.isNotBlank(externalPlayerId), MerchantPlayer::getExternalPlayerId, externalPlayerId);
        lqw.orderByDesc(MerchantPlayer::getPlayerId);
        Page<PlayerVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public int changeStatus(Long playerId, String status) {
        MerchantPlayer update = new MerchantPlayer();
        update.setPlayerId(playerId);
        update.setStatus(status);
        return baseMapper.updateById(update);
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q test -pl game-modules/game-merchant -am -Dtest=PlayerServiceTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: 写 `PlayerController.java`（后台只查 + 状态变更）**

```java
package org.dromara.game.merchant.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.game.merchant.domain.vo.PlayerVo;
import org.dromara.game.merchant.service.IPlayerService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 玩家档案管理
 *
 * @author MLChong
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/player")
public class PlayerController extends BaseController {

    private final IPlayerService playerService;

    /**
     * 查询玩家列表
     */
    @SaCheckPermission("game:player:list")
    @GetMapping("/list")
    public TableDataInfo<PlayerVo> list(@RequestParam(required = false) Long merchantId,
                                        @RequestParam(required = false) String externalPlayerId,
                                        PageQuery pageQuery) {
        return playerService.queryPageList(merchantId, externalPlayerId, pageQuery);
    }

    /**
     * 启用/停用玩家
     */
    @SaCheckPermission("game:player:edit")
    @Log(title = "玩家档案", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus/{playerId}")
    public R<Void> changeStatus(@PathVariable Long playerId,
                                @Pattern(regexp = "^[01]$", message = "状态仅支持0/1") @RequestParam String status) {
        return toAjax(playerService.changeStatus(playerId, status));
    }
}
```

- [ ] **Step 6: 全量测试 + Commit**

Run: `mvn -q test -pl game-modules/game-merchant -am`
Expected: 全绿

```bash
git add game-modules/game-merchant
git commit -m "add 新增 玩家档案 getOrCreate并发安全建档与后台管理

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: Dubbo 契约实现（TDD）

**Files:**
- Create: `game-modules/game-merchant/src/main/java/org/dromara/game/merchant/dubbo/RemoteMerchantServiceImpl.java`
- Create: `game-modules/game-merchant/src/main/java/org/dromara/game/merchant/dubbo/RemotePlayerServiceImpl.java`
- Test: `game-modules/game-merchant/src/test/java/org/dromara/game/merchant/dubbo/RemoteServiceTest.java`

**Interfaces:**
- Consumes: Task 2 契约、Task 5 `IMerchantService.getByCode`、Task 6 `IPlayerService.getOrCreate`
- Produces: Plan D（openapi）可 `@DubboReference` 调用的两个远程服务

- [ ] **Step 1: 写失败测试 `RemoteServiceTest.java`**

```java
package org.dromara.game.merchant.dubbo;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.game.merchant.api.domain.vo.RemoteMerchantVo;
import org.dromara.game.merchant.api.domain.vo.RemotePlayerVo;
import org.dromara.game.merchant.domain.Merchant;
import org.dromara.game.merchant.domain.MerchantPlayer;
import org.dromara.game.merchant.service.IMerchantService;
import org.dromara.game.merchant.service.IPlayerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Dubbo 远程服务")
@ExtendWith(MockitoExtension.class)
class RemoteServiceTest {

    @Mock
    private IMerchantService merchantService;

    @Mock
    private IPlayerService playerService;

    @InjectMocks
    private RemoteMerchantServiceImpl remoteMerchantService;

    @InjectMocks
    private RemotePlayerServiceImpl remotePlayerService;

    private Merchant merchant(String status) {
        Merchant m = new Merchant();
        m.setMerchantId(1L);
        m.setMerchantCode("mch_test01");
        m.setMerchantName("测试商户");
        m.setStatus(status);
        m.setSecret("a".repeat(64));
        m.setWalletMode("TRANSFER");
        m.setCurrencies("CNY,USDT");
        m.setFeeRate(new BigDecimal("0.08"));
        m.setPrepaidBalance(new BigDecimal("1000"));
        return m;
    }

    @Test
    @DisplayName("getByCode：币种串转列表，字段完整映射")
    void getByCode_mapsFields() {
        when(merchantService.getByCode("mch_test01")).thenReturn(merchant("0"));

        RemoteMerchantVo vo = remoteMerchantService.getByCode("mch_test01");

        assertEquals(1L, vo.getMerchantId());
        assertEquals(List.of("CNY", "USDT"), vo.getCurrencies());
        assertEquals("a".repeat(64), vo.getSecret());
    }

    @Test
    @DisplayName("getByCode：不存在返回null")
    void getByCode_notFound_returnsNull() {
        when(merchantService.getByCode("nope")).thenReturn(null);
        assertNull(remoteMerchantService.getByCode("nope"));
    }

    @Test
    @DisplayName("getOrCreate：商户不存在或已停用则抛异常，不建档")
    void getOrCreate_merchantMissingOrDisabled_throws() {
        when(merchantService.queryById(1L)).thenReturn(null);
        assertThrows(ServiceException.class,
            () -> remotePlayerService.getOrCreate(1L, "u001"));
        verifyNoInteractions(playerService);
    }

    @Test
    @DisplayName("getOrCreate：正常链路返回玩家")
    void getOrCreate_ok() {
        org.dromara.game.merchant.domain.vo.MerchantVo mvo = new org.dromara.game.merchant.domain.vo.MerchantVo();
        mvo.setMerchantId(1L);
        mvo.setStatus("0");
        when(merchantService.queryById(1L)).thenReturn(mvo);
        MerchantPlayer p = new MerchantPlayer();
        p.setPlayerId(100L);
        p.setMerchantId(1L);
        p.setExternalPlayerId("u001");
        p.setStatus("0");
        when(playerService.getOrCreate(1L, "u001")).thenReturn(p);

        RemotePlayerVo vo = remotePlayerService.getOrCreate(1L, "u001");

        assertEquals(100L, vo.getPlayerId());
        assertEquals("u001", vo.getExternalPlayerId());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q test -pl game-modules/game-merchant -am -Dtest=RemoteServiceTest`
Expected: `COMPILATION ERROR`

- [ ] **Step 3: 实现两个 Dubbo 服务**

`RemoteMerchantServiceImpl.java`：

```java
package org.dromara.game.merchant.dubbo;

import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.game.merchant.api.RemoteMerchantService;
import org.dromara.game.merchant.api.domain.vo.RemoteMerchantVo;
import org.dromara.game.merchant.domain.Merchant;
import org.dromara.game.merchant.service.IMerchantService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 商户远程服务实现
 *
 * @author MLChong
 */
@RequiredArgsConstructor
@Service
@DubboService
public class RemoteMerchantServiceImpl implements RemoteMerchantService {

    private final IMerchantService merchantService;

    @Override
    public RemoteMerchantVo getByCode(String merchantCode) {
        Merchant merchant = merchantService.getByCode(merchantCode);
        if (merchant == null) {
            return null;
        }
        RemoteMerchantVo vo = new RemoteMerchantVo();
        vo.setMerchantId(merchant.getMerchantId());
        vo.setMerchantCode(merchant.getMerchantCode());
        vo.setMerchantName(merchant.getMerchantName());
        vo.setStatus(merchant.getStatus());
        vo.setSecret(merchant.getSecret());
        vo.setWalletMode(merchant.getWalletMode());
        vo.setCallbackUrl(merchant.getCallbackUrl());
        vo.setFeeRate(merchant.getFeeRate());
        vo.setPrepaidBalance(merchant.getPrepaidBalance());
        List<String> currencies = StringUtils.isBlank(merchant.getCurrencies())
            ? List.of()
            : Arrays.asList(merchant.getCurrencies().split(","));
        vo.setCurrencies(currencies);
        return vo;
    }
}
```

`RemotePlayerServiceImpl.java`：

```java
package org.dromara.game.merchant.dubbo;

import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.game.merchant.api.RemotePlayerService;
import org.dromara.game.merchant.api.domain.vo.RemotePlayerVo;
import org.dromara.game.merchant.domain.MerchantPlayer;
import org.dromara.game.merchant.domain.vo.MerchantVo;
import org.dromara.game.merchant.service.IMerchantService;
import org.dromara.game.merchant.service.IPlayerService;
import org.springframework.stereotype.Service;

/**
 * 玩家远程服务实现
 *
 * @author MLChong
 */
@RequiredArgsConstructor
@Service
@DubboService
public class RemotePlayerServiceImpl implements RemotePlayerService {

    private final IMerchantService merchantService;
    private final IPlayerService playerService;

    @Override
    public RemotePlayerVo getOrCreate(Long merchantId, String externalPlayerId) {
        MerchantVo merchant = merchantService.queryById(merchantId);
        if (merchant == null || !"0".equals(merchant.getStatus())) {
            throw new ServiceException("商户不存在或已停用");
        }
        MerchantPlayer player = playerService.getOrCreate(merchantId, externalPlayerId);
        RemotePlayerVo vo = new RemotePlayerVo();
        vo.setPlayerId(player.getPlayerId());
        vo.setMerchantId(player.getMerchantId());
        vo.setExternalPlayerId(player.getExternalPlayerId());
        vo.setStatus(player.getStatus());
        return vo;
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q test -pl game-modules/game-merchant -am -Dtest=RemoteServiceTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add game-modules/game-merchant
git commit -m "add 新增 商户/玩家 Dubbo 远程服务实现

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 8: 全量验证 + 冒烟

**Files:**
- 无新文件（如有修复则改动对应文件）

**Interfaces:**
- Consumes: Task 1–7 全部产物
- Produces: Plan A 完成态——全绿测试 + 可注册运行的服务

- [ ] **Step 1: 全量单测**

Run: `mvn -q test -pl game-modules/game-merchant -am`
Expected: `BUILD SUCCESS`，Tests run ≥ 18，Failures 0，Errors 0

- [ ] **Step 2: 全工程编译（确认未破坏上游模块）**

Run: `mvn -q -T 1C -DskipTests package`
Expected: `BUILD SUCCESS`（所有 reactor 模块）

- [ ] **Step 3: 冒烟（需本地栈，见 memory《Run full stack on macOS»）**

1. 确认 Task 4 已建库、Task 3 Step 6 已发布 nacos 配置（未做则现在做）
2. Run: `mvn -q -DskipTests install -pl game-modules/game-merchant -am && mvn spring-boot:run -pl game-modules/game-merchant -DskipTests`
3. Expected: 日志出现 `商户服务启动成功`，无 ERROR；nacos dev 命名空间出现 `game-merchant`；日志可见 Dubbo 暴露 `RemoteMerchantService`/`RemotePlayerService`
4. Ctrl-C 停止

- [ ] **Step 4: 如有修复，提交收尾**

```bash
git add -A game-modules game-api
git commit -m "fix 修复 game-merchant 冒烟问题

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

（无修复则跳过本步）

---

## 计划外（明确不在 Plan A 做）

- 后台菜单/权限 SQL 与 plus-ui 页面 → Plan F（当前 `@SaCheckPermission` 权限串已定义，届时只需插菜单数据）
- 商户限红模板与玩家个体限红 → Plan C（限红属彩种域，届时在 merchant/player 表加关联列或独立配置表）
- 商户信息的 Redis 缓存 → Plan D 验签链路成为热点时再加（YAGNI）
- 网关路由 `game-merchant` 暴露 → Plan F 联调时统一加
