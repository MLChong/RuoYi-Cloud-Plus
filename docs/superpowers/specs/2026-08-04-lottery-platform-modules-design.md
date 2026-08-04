# 彩票游戏供应商平台（B2B）模块设计

- 日期：2026-08-04
- 状态：设计已获批准（v3）
- 基座：RuoYi-Cloud-Plus 2.6.2（branch 2.X）+ plus-ui（后台管理前端）

## 1. 定位

构建一个 **B2B 彩票游戏供应商平台**（对标 TCGaming / DBGaming 的角色）：

- 我方是**游戏供应商（乙方）**。运营平台（甲方，下称"商户"）通过 API 接入我方彩票游戏。
- **玩家资金始终在商户侧**。我方通过钱包 API 与商户逐笔结算下注/派彩。
- 我方营收 = GGR（商户玩家总输赢）× 费率，向商户收取。

### 明确不做（Out of Scope）

- 玩家充值提现（payment）、玩家注册/登录/KYC —— 玩家由商户 launch 带入
- 代理体系、活动系统 —— 商户侧业务
- 聚合其他游戏供应商 —— 方向相反，我方即供应商
- 包网/多品牌 —— 单品牌运营，框架租户功能关闭（`tenant.enable=false`，数据落默认租户 000000，表保留 tenant_id 字段但无多品牌逻辑）

## 2. 已确认决策

| # | 决策 | 内容 |
|---|---|---|
| 1 | 开奖源 | 双源：官彩采集（多源比对）+ 自开彩 RNG（commit-reveal 可审计） |
| 2 | 商户接入模式 | 同时支持 Seamless（单一钱包：我方定义回调规范，商户实现，我方调用）与 Transfer（转账钱包：我方提供 API，商户调用，我方持游戏内账本） |
| 3 | 无 payment 模块 | 玩家资金全部通过 seamless/transfer 钱包 API 与商户平台结算 |
| 4 | 品牌 | 单品牌 |
| 5 | 币种 | 多币种（含 USDT），商户级币种白名单 |
| 6 | 并发 | 高并发预期：RocketMQ 一期引入；报表量大后接 Doris |

## 3. 总体架构

```
商户服务端 ──(验签)──> /open/**  ─> game-openapi   商户开放 API
玩家浏览器 ──launch──> 游戏 H5 ──> /api/**   ─> game-lottery / game-wallet 玩家端接口 (app_user 会话)
运营人员   ──────────> plus-ui ──> /admin/** ─> ruoyi-system + game-* 后台接口 (sys_user)

                     ruoyi-gateway（统一入口、限流、SSE 透传）

game-modules/（独立顶层目录，与上游 2.X 隔离，避免合并冲突）
├── game-openapi     商户开放 API（门面）
├── game-merchant    商户管理 + 玩家档案
├── game-wallet      钱包端口（TRANSFER 账本 / SEAMLESS 出站引擎）+ 统一流水
├── game-lottery     彩种/期号/投注/开奖/结算（游戏核心，高并发）
└── game-report      对账/账单/拉单数据/运营报表
game-api/            上述服务间的 Dubbo 接口定义（game-api-merchant / wallet / lottery）
```

### 复用基座能力

| 基座模块 | 用途 |
|---|---|
| ruoyi-gateway | 统一入口；`/open` `/api` `/admin` 三类路由与独立限流；SSE 透传（开奖推送） |
| ruoyi-auth + sa-token | 后台 `sys_user`；玩家会话走内置 `app_user` 通道（launch token 换会话） |
| ruoyi-system / plus-ui | 后台管理（运营人员、角色、菜单、字典、审计日志） |
| ruoyi-job (SnailJob) | 期号生成、封盘/开奖触发、transfer 查证补偿、对账、账单、汇率拉取 |
| ruoyi-workflow (Warm-Flow) | 商户开户/费率变更等内部审批（后期按需启用） |
| ruoyi-resource | OSS（对账文件）、邮件通知 |
| common: idempotent / encrypt / ratelimiter / sensitive / websocket / sse | 防重、加解密、限流、脱敏、推送 |
| ruoyi-visual (monitor/nacos/snailjob-server) | 运维监控原样复用 |

### 新增基础设施

- **RocketMQ 5.x**：投注异步落单（事务消息）、派彩重试队列、领域事件总线、延迟消息（封盘/开奖兜底）。封装为 `ruoyi-common-mq` 供各服务使用。
- **Doris**（三期）：报表 OLAP，消费领域事件写入，不侵入上游服务。

## 4. 部署单元与模块职责（一期 5 个）

目标形态可进一步拆分（如 bet 独立），一期以 5 个部署单元、服务内按包分模块的方式落地，拆分成本低。

### 4.1 game-openapi —— 商户开放 API

接口清单（API v1 全量，各接口交付节奏见 §11 分期）：

| 接口 | 说明 |
|---|---|
| `POST /open/game/launch` | 传商户玩家ID/币种/语言/returnUrl，返回一次性 token + 游戏 H5 URL |
| `POST /open/wallet/transfer-in` / `transfer-out` | Transfer 模式转入/转出 |
| `POST /open/wallet/transfer-query` | 按商户 requestId 查转账终态（补单用） |
| `GET /open/wallet/balance` | Transfer 模式游戏内余额查询 |
| `GET /open/records/bets` | 游标式增量拉单（商户做打码量/报表） |
| `GET /open/draw/results` | 开奖结果查询 |

安全与协议：

- HMAC-SHA256 签名（商户级 secret）+ timestamp + nonce 防重放（5 分钟窗口）
- 商户 IP 白名单；`/open` 路由独立限流
- 写接口以商户侧 `requestId` 幂等
- HTTPS + JSON；金额用字符串承载 DECIMAL；时间一律 UTC ISO-8601

### 4.2 game-merchant —— 商户与玩家

- 商户档案：编号、名称、状态、secret、钱包模式（SEAMLESS / TRANSFER，商户级二选一）、seamless 回调地址、币种白名单、费率、限红模板、IP 白名单
- **预存额度/保证金**：费用从预存额度扣减，余额不足自动停服（拒绝 launch 与下注），防欠费
- 玩家档案：`(merchant_id, external_player_id)` 唯一；首次 launch 自动建档；状态、个体限红；我方无密码/注册体系

### 4.3 game-wallet —— 钱包端口（全系统关键抽象）

`WalletPort.debit / credit / rollback / getBalance`，按商户钱包模式路由：

- **TRANSFER 实现**：内部账本 `(merchant_id, player_id, currency)`，balance+frozen，乐观锁；下注走 Redis Lua 预扣 + 异步落库；`transfer_order` 状态机（转入/转出/查询中/成功/失败/冲正），SnailJob 定时查证补偿卡单
- **SEAMLESS 实现**：出站 HTTP 调商户实现的回调（我方定义规范：getBalance / debit / credit / rollback）。**debit 同步**（默认超时 3s；超时或失败即发 rollback 并拒单——钱不到手不接单）；**credit 异步**（RocketMQ 重试队列直至商户确认，对账兜底）；**商户级隔离**：独立线程池 + 熔断，单商户回调故障仅暂停该商户投注
- **统一交易流水** `wallet_transaction`：两种模式共用，不可变，幂等键 = 我方 transaction_id（snowflake）；是对账与账单的唯一口径
- 币种与汇率：币种主数据 + 汇率表（SnailJob 定时拉取），报表折算基准币 USD

### 4.4 game-lottery —— 游戏核心

- **彩种配置**：彩种（首发：极速快三/RNG；后续时时彩、PK10、六合彩、越南彩等）、玩法与赔率、限红（平台默认 → 商户模板 → 玩家个体三级）
- **期号**：SnailJob 按彩种时刻表（含时区，存储全 UTC）预生成；状态机 `PENDING → OPEN → CLOSED → DRAWN → SETTLED / VOIDED`
- **投注**：校验（会话、封盘、限红、商户状态）→ WalletPort.debit → RocketMQ 事务消息落注单；"钱动必有单"由事务消息 + 冲正保证
- **开奖双源**（策略接口 `DrawSource`）：
  - OFFICIAL：每彩种 ≥2 采集源比对一致才开奖；不一致/超时 → 延迟开奖 → 超阈值转异常期
  - RNG：独立组件（便于将来送审认证）；期号创建时公布 `hash(serverSeed)`，开奖后公开 serverSeed，结果 = 确定性函数(serverSeed, 期号)，可第三方复验
- **结算**：开奖事件驱动，按期号+分片键并行；中奖判定 → WalletPort.credit 派彩；幂等、整期可重跑
- **推送**：SSE 推送开奖结果与余额变动到游戏 H5

### 4.5 game-report —— 对账 / 账单 / 报表

- 商户账单：GGR × 费率，按币种统计、折算 USD，月结；从预存额度自动扣收
- 日切对账：对账文件（OSS）+ API，我方流水 vs 商户流水
- 拉单数据准备（供 openapi 游标接口）；运营报表（平台/彩种/商户维度盈亏）
- 一期 MySQL 查询；三期消费领域事件接 Doris，上游无改动

### 4.6 风控（一期内嵌，四期独立成服务）

- 投注侧：限红硬校验、异常下注模式识别（对刷/扫水）规则拦截、玩家/商户黑名单
- 商户侧：预存额度耗尽自动停服、单商户投注/派彩异常波动告警

## 5. 关键流程

### 5.1 Launch

商户服务端 → `POST /open/game/launch`（验签）→ 校验商户状态/币种/额度 → 玩家自动建档（首次）→ 签发一次性 token（5 分钟 TTL）→ 返回游戏 URL → H5 以 token 换 sa-token `app_user` 会话（上下文含 merchant_id / player_id / currency / 钱包模式）。

### 5.2 下注（Seamless）

校验 → 同步 `debit`（成功才继续；超时/失败 → 发 `rollback`（幂等）并拒单）→ RocketMQ 事务消息 → 消费端落注单 + 流水。

### 5.3 下注（Transfer）

校验 → Redis Lua 原子预扣 → RocketMQ 事务消息 → 落注单 + 账本流水；失败走冲正回补。

### 5.4 开奖与结算

SnailJob 到点触发 → DrawSource 取号 → 期号置 DRAWN + 发开奖事件 + SSE 推送 → 结算消费者分片并行判奖 → 中奖单 `credit` 派彩（seamless 异步重试 / transfer 直接入账本）→ 期号置 SETTLED。

### 5.5 异常期（VOIDED）

采集源持续不一致或 RNG 组件故障超阈值 → 整期作废 → 全部注单退款（`credit` 原额，幂等）→ 公告推送。

## 6. 数据设计要点

- 按服务分库：`game_merchant` / `game_wallet` / `game_lottery` / `game_report`（MySQL）
- 金额 `DECIMAL(24,6)`；ID 用 snowflake（框架 ASSIGN_ID）；时间全 UTC
- 注单表按月分表，分片键 player 维度（玩家查单），期号+彩种二级索引（结算扫描）；一期即按此建表
- 资金相关表（流水/注单/转账单）**只有状态流转，禁止物理删除与更新金额字段**
- 关键表：merchant、merchant_player、player_wallet(transfer)、wallet_transaction、transfer_order、lottery、lottery_play、lottery_issue、bet_order、draw_result、settle_batch、merchant_bill、exchange_rate

## 7. 前端

- **plus-ui 扩展**（运营后台）：商户管理、彩种/玩法/赔率/限红配置、期号与开奖管理（含异常期处置）、注单查询、交易流水、对账与账单、风控名单、报表
- **游戏 H5**：独立新项目（Vue3，交易大厅式投注界面），launch token 进入，多语言、多币种展示，SSE 实时开奖；不复用 plus-ui

## 8. 非功能

- 并发基线一期压测确定；投注链路水平扩容（lottery 服务无状态，热点在 Redis 与 MQ）
- 所有跨服务资金操作幂等（transaction_id）；seamless 出站调用商户级线程池隔离 + 熔断 + 3s 超时
- 可观测复用基座：prometheus + skylog + monitor；商户回调成功率/延迟单独打点告警

## 9. 错误处理策略

| 场景 | 策略 |
|---|---|
| 商户 seamless 回调不可用 | 熔断该商户 → 暂停其玩家投注（其余商户不受影响）→ 恢复自动放开 |
| debit 超时（结果未知） | 发幂等 rollback + 拒单；rollback 也失败则进对账差异队列人工处置 |
| credit 派彩失败 | MQ 重试（退避）→ 超限进死信 + SnailJob 补偿 + 对账兜底，绝不静默丢弃 |
| transfer 卡单 | transfer-query 查证 → 终态冲正，SnailJob 周期兜底 |
| 采集源不一致/超时 | 延迟开奖 → 超阈值整期 VOIDED 退款 |
| MQ 落单消费失败 | 重试 → 死信告警；投注入口有事务消息回查，防"扣款无单" |

## 10. 测试策略

- 玩法结算规则：golden-case 表驱动单测（每玩法穷举边界：中奖/未中/和局/限红边界）
- 集成测试：mock 商户回调服务端验证 seamless 全链路（含超时/乱序/重复回调）
- 对账一致性测试：注入随机故障后跑对账必须能发现并归零差异
- 投注链路压测（一期建基线）；实施阶段按 TDD 执行

## 11. 分期

| 期 | 内容 |
|---|---|
| 一期 | merchant + openapi（launch/验签）+ wallet TRANSFER 模式 + lottery 极速快三（RNG）全闭环 + 游戏 H5 + RocketMQ；seamless 回调规范文档定稿（不实现） |
| 二期 | SEAMLESS 出站引擎（重试/熔断/对账）+ 官采开奖源 + 增量拉单 API |
| 三期 | 商户账单计费 + 对账体系 + Doris 报表 + 彩种铺量 |
| 四期 | 风控独立服务 + RNG 认证送审（iTech Labs/GLI）+（可选）商户自助查询后台 |

*一期先 TRANSFER 的原因：不依赖商户实现我方回调即可自闭环测试；若首个商户指定 seamless，一二期内容对调。*

## 12. 合规

线上彩票供应属强监管行业：我方需持供应商类牌照（如 PAGCOR gaming supplier license、Curaçao 等，视目标市场）；RNG 需通过认证（四期送审）；面向玩家的地区限制与 KYC/AML 义务主要由商户（运营方）承担，我方在合同与技术上保留按商户停服能力。

## 13. 默认技术选型（实施阶段可调整）

RocketMQ 5.x；签名 HMAC-SHA256；基准币 USD；首发彩种极速快三（RNG）；OLAP 选 Doris；游戏 H5 用 Vue3。
