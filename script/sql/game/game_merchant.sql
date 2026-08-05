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
