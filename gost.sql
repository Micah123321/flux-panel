-- phpMyAdmin SQL Dump
-- version 5.2.0
-- https://www.phpmyadmin.net/
--
-- 主机： localhost
-- 生成日期： 2025-08-14 21:52:52
-- 服务器版本： 5.7.40-log
-- PHP 版本： 7.4.33

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- 数据库： `gost`
--

-- --------------------------------------------------------

--
-- 表的结构 `forward`
--

CREATE TABLE `forward` (
  `id` int(10) NOT NULL,
  `user_id` int(10) NOT NULL,
  `user_name` varchar(100) NOT NULL,
  `name` varchar(100) NOT NULL,
  `tunnel_id` int(10) NOT NULL,
  `in_port` int(10) NOT NULL,
  `out_port` int(10) DEFAULT NULL,
  `remote_addr` longtext NOT NULL,
  `strategy` varchar(100) NOT NULL DEFAULT 'fifo',
  `interface_name` varchar(200) DEFAULT NULL,
  `in_flow` bigint(20) NOT NULL DEFAULT '0',
  `out_flow` bigint(20) NOT NULL DEFAULT '0',
  `created_time` bigint(20) NOT NULL,
  `updated_time` bigint(20) NOT NULL,
  `status` int(10) NOT NULL,
  `inx` int(10) NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- 表的结构 `node`
--

CREATE TABLE `node` (
  `id` int(10) NOT NULL,
  `name` varchar(100) NOT NULL,
  `secret` varchar(100) NOT NULL,
  `ip` longtext,
  `server_ip` varchar(100) NOT NULL,
  `port_sta` int(10) NOT NULL,
  `port_end` int(10) NOT NULL,
  `version` varchar(100) DEFAULT NULL,
  `http` int(10) NOT NULL DEFAULT '0',
  `tls` int(10) NOT NULL DEFAULT '0',
  `socks` int(10) NOT NULL DEFAULT '0',
  `created_time` bigint(20) NOT NULL,
  `updated_time` bigint(20) DEFAULT NULL,
  `status` int(10) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- 表的结构 `aggregate_node_group`
--

CREATE TABLE `aggregate_node_group` (
  `id` int(10) NOT NULL,
  `name` varchar(100) NOT NULL,
  `node_ids` text NOT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_time` bigint(20) NOT NULL,
  `updated_time` bigint(20) NOT NULL,
  `status` int(10) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- 表的结构 `aggregate_forward`
--

CREATE TABLE `aggregate_forward` (
  `id` int(10) NOT NULL,
  `name` varchar(100) NOT NULL,
  `entry_group_id` int(10) NOT NULL,
  `exit_group_id` int(10) NOT NULL,
  `entry_addresses` text NOT NULL,
  `entry_port_start` int(10) NOT NULL,
  `entry_port_end` int(10) NOT NULL,
  `target_port_start` int(10) NOT NULL,
  `target_port_end` int(10) NOT NULL,
  `mode` varchar(30) NOT NULL DEFAULT 'load_balance',
  `traffic_ratio` decimal(10,1) NOT NULL DEFAULT '1.0',
  `in_flow` bigint(20) NOT NULL DEFAULT '0',
  `out_flow` bigint(20) NOT NULL DEFAULT '0',
  `interface_name` varchar(200) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_time` bigint(20) NOT NULL,
  `updated_time` bigint(20) NOT NULL,
  `status` int(10) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- 表的结构 `speed_limit`
--

CREATE TABLE `speed_limit` (
  `id` int(10) NOT NULL,
  `name` varchar(100) NOT NULL,
  `speed` int(10) NOT NULL,
  `tunnel_id` int(10) NOT NULL,
  `tunnel_name` varchar(100) NOT NULL,
  `created_time` bigint(20) NOT NULL,
  `updated_time` bigint(20) DEFAULT NULL,
  `status` int(10) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- 表的结构 `statistics_flow`
--

CREATE TABLE `statistics_flow` (
  `id` int(10) NOT NULL,
  `user_id` int(10) NOT NULL,
  `flow` bigint(20) NOT NULL,
  `total_flow` bigint(20) NOT NULL,
  `time` varchar(100) NOT NULL,
  `created_time` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- 表的结构 `tunnel`
--

CREATE TABLE `tunnel` (
  `id` int(10) NOT NULL,
  `name` varchar(100) NOT NULL,
  `traffic_ratio` decimal(10,1) NOT NULL DEFAULT '1.0',
  `in_node_id` int(10) NOT NULL,
  `in_ip` varchar(100) NOT NULL,
  `out_node_id` int(10) NOT NULL,
  `out_ip` varchar(100) NOT NULL,
  `type` int(10) NOT NULL,
  `protocol` varchar(10) NOT NULL DEFAULT 'tls',
  `flow` int(10) NOT NULL,
  `tcp_listen_addr` varchar(100) NOT NULL DEFAULT '[::]',
  `udp_listen_addr` varchar(100) NOT NULL DEFAULT '[::]',
  `interface_name` varchar(200) DEFAULT NULL,
  `created_time` bigint(20) NOT NULL,
  `updated_time` bigint(20) NOT NULL,
  `status` int(10) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- 表的结构 `user`
--

CREATE TABLE `user` (
  `id` int(10) NOT NULL,
  `user` varchar(100) NOT NULL,
  `pwd` varchar(100) NOT NULL,
  `role_id` int(10) NOT NULL,
  `exp_time` bigint(20) NOT NULL,
  `flow` bigint(20) NOT NULL,
  `in_flow` bigint(20) NOT NULL DEFAULT '0',
  `out_flow` bigint(20) NOT NULL DEFAULT '0',
  `daily_flow` bigint(20) NOT NULL DEFAULT '0' COMMENT '每日流量限制(GiB)，0=不限制',
  `daily_in_flow` bigint(20) NOT NULL DEFAULT '0' COMMENT '今日已用入站流量(字节)',
  `daily_out_flow` bigint(20) NOT NULL DEFAULT '0' COMMENT '今日已用出站流量(字节)',
  `flow_reset_time` bigint(20) NOT NULL,
  `num` int(10) NOT NULL,
  `package_plan_id` int(10) DEFAULT NULL,
  `user_group_id` int(10) DEFAULT NULL,
  `speed_mbps` int(10) NOT NULL DEFAULT '0',
  `ip_limit` int(10) NOT NULL DEFAULT '0',
  `connection_limit` int(10) NOT NULL DEFAULT '0',
  `invite_code` varchar(32) DEFAULT NULL,
  `inviter_user_id` int(10) DEFAULT NULL,
  `invite_balance` decimal(10,2) NOT NULL DEFAULT '0.00',
  `created_time` bigint(20) NOT NULL,
  `updated_time` bigint(20) DEFAULT NULL,
  `status` int(10) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

--
-- 转存表中的数据 `user`
--

INSERT INTO `user` (`id`, `user`, `pwd`, `role_id`, `exp_time`, `flow`, `in_flow`, `out_flow`, `flow_reset_time`, `num`, `package_plan_id`, `user_group_id`, `speed_mbps`, `ip_limit`, `connection_limit`, `invite_code`, `inviter_user_id`, `invite_balance`, `created_time`, `updated_time`, `status`) VALUES
(1, 'admin_user', '3c85cdebade1c51cf64ca9f3c09d182d', 0, 2727251700000, 99999, 0, 0, 1, 99999, NULL, NULL, 0, 0, 0, 'IVADMIN', NULL, 0.00, 1748914865000, 1754011744252, 1);

-- --------------------------------------------------------

--
-- 表的结构 `user_tunnel`
--

CREATE TABLE `user_tunnel` (
  `id` int(10) NOT NULL,
  `user_id` int(10) NOT NULL,
  `tunnel_id` int(10) NOT NULL,
  `speed_id` int(10) DEFAULT NULL,
  `num` int(10) NOT NULL,
  `flow` bigint(20) NOT NULL,
  `in_flow` bigint(20) NOT NULL DEFAULT '0',
  `out_flow` bigint(20) NOT NULL DEFAULT '0',
  `daily_flow` bigint(20) NOT NULL DEFAULT '0' COMMENT '每日流量限制(GiB)，0=不限制',
  `daily_in_flow` bigint(20) NOT NULL DEFAULT '0' COMMENT '今日已用入站流量(字节)',
  `daily_out_flow` bigint(20) NOT NULL DEFAULT '0' COMMENT '今日已用出站流量(字节)',
  `flow_reset_time` bigint(20) NOT NULL,
  `exp_time` bigint(20) NOT NULL,
  `status` int(10) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- 表的结构 `vite_config`
--

CREATE TABLE `vite_config` (
  `id` int(10) NOT NULL,
  `name` varchar(200) NOT NULL,
  `value` varchar(200) NOT NULL,
  `time` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

--
-- 转存表中的数据 `vite_config`
--

INSERT INTO `vite_config` (`id`, `name`, `value`, `time`) VALUES
(1, 'app_name', 'flux', 1755147963000),
(2, 'invite_ratio', '0', 1755147963000),
(3, 'invite_renewal_ratio', '0', 1755147963000);

-- --------------------------------------------------------

--
-- 表的结构 `package_plan`
--

CREATE TABLE `package_plan` (
  `id` int(10) NOT NULL,
  `name` varchar(100) NOT NULL,
  `hidden` int(10) NOT NULL DEFAULT '0',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00',
  `type` int(10) NOT NULL DEFAULT '1',
  `duration_multiplier` int(10) NOT NULL DEFAULT '1',
  `user_group_id` int(10) DEFAULT NULL,
  `flow` bigint(20) NOT NULL DEFAULT '0',
  `daily_flow` bigint(20) NOT NULL DEFAULT '0' COMMENT '每日流量限制(GiB)，0=不限制',
  `max_rules` int(10) NOT NULL DEFAULT '0',
  `speed_mbps` int(10) NOT NULL DEFAULT '0',
  `ip_limit` int(10) NOT NULL DEFAULT '0',
  `connection_limit` int(10) NOT NULL DEFAULT '0',
  `description` longtext,
  `created_time` bigint(20) NOT NULL,
  `updated_time` bigint(20) DEFAULT NULL,
  `status` int(10) NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- 表的结构 `device_group`
--

CREATE TABLE `device_group` (
  `id` int(10) NOT NULL,
  `name` varchar(100) NOT NULL,
  `tunnel_ids` longtext,
  `description` longtext,
  `created_time` bigint(20) NOT NULL,
  `updated_time` bigint(20) DEFAULT NULL,
  `status` int(10) NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- 表的结构 `user_group`
--

CREATE TABLE `user_group` (
  `id` int(10) NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` longtext,
  `created_time` bigint(20) NOT NULL,
  `updated_time` bigint(20) DEFAULT NULL,
  `status` int(10) NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- 表的结构 `user_group_device_group`
--

CREATE TABLE `user_group_device_group` (
  `id` int(10) NOT NULL,
  `user_group_id` int(10) NOT NULL,
  `device_group_id` int(10) NOT NULL,
  `created_time` bigint(20) NOT NULL,
  `updated_time` bigint(20) DEFAULT NULL,
  `status` int(10) NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- 表的结构 `order_record`
--

CREATE TABLE `order_record` (
  `id` int(10) NOT NULL,
  `order_no` varchar(64) NOT NULL,
  `user_id` int(10) NOT NULL,
  `package_plan_id` int(10) NOT NULL,
  `package_name` varchar(100) NOT NULL,
  `original_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `discount_ratio` int(10) NOT NULL DEFAULT '100',
  `payable_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `invite_deduction` decimal(10,2) NOT NULL DEFAULT '0.00',
  `status` int(10) NOT NULL DEFAULT '0',
  `payment_channel` varchar(32) DEFAULT NULL,
  `provider_trade_no` varchar(128) DEFAULT NULL,
  `payment_url` longtext,
  `paid_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `redeem_code_id` int(10) DEFAULT NULL,
  `inviter_user_id` int(10) DEFAULT NULL,
  `reward_ratio` int(10) NOT NULL DEFAULT '0',
  `reward_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `completed_time` bigint(20) DEFAULT NULL,
  `created_time` bigint(20) NOT NULL,
  `updated_time` bigint(20) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- 表的结构 `redeem_code`
--

CREATE TABLE `redeem_code` (
  `id` int(10) NOT NULL,
  `package_plan_id` int(10) NOT NULL,
  `package_name` varchar(100) NOT NULL,
  `discount_ratio` int(10) NOT NULL DEFAULT '100',
  `total_times` int(10) NOT NULL DEFAULT '1',
  `used_times` int(10) NOT NULL DEFAULT '0',
  `code` varchar(64) NOT NULL,
  `created_time` bigint(20) NOT NULL,
  `updated_time` bigint(20) DEFAULT NULL,
  `status` int(10) NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- 表的结构 `invite_record`
--

CREATE TABLE `invite_record` (
  `id` int(10) NOT NULL,
  `inviter_user_id` int(10) NOT NULL,
  `invitee_user_id` int(10) NOT NULL,
  `invite_code` varchar(32) NOT NULL,
  `created_time` bigint(20) NOT NULL,
  `updated_time` bigint(20) DEFAULT NULL,
  `status` int(10) NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- 表的结构 `invite_reward_record`
--

CREATE TABLE `invite_reward_record` (
  `id` int(10) NOT NULL,
  `order_id` int(10) NOT NULL,
  `inviter_user_id` int(10) NOT NULL,
  `invitee_user_id` int(10) NOT NULL,
  `reward_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `ratio` int(10) NOT NULL DEFAULT '0',
  `type` int(10) NOT NULL DEFAULT '1',
  `created_time` bigint(20) NOT NULL,
  `updated_time` bigint(20) DEFAULT NULL,
  `status` int(10) NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- 表的结构 `payment_config`
--

CREATE TABLE `payment_config` (
  `id` int(10) NOT NULL,
  `channel` varchar(32) NOT NULL,
  `display_name` varchar(100) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '0',
  `pay_type` varchar(32) DEFAULT NULL,
  `gateway_url` varchar(500) DEFAULT NULL,
  `app_id` varchar(200) DEFAULT NULL,
  `merchant_id` varchar(200) DEFAULT NULL,
  `secret_key` longtext,
  `private_key` longtext,
  `public_key` longtext,
  `api_key` longtext,
  `endpoint_secret` longtext,
  `serial_no` varchar(200) DEFAULT NULL,
  `notify_url` varchar(500) DEFAULT NULL,
  `return_url` varchar(500) DEFAULT NULL,
  `cancel_url` varchar(500) DEFAULT NULL,
  `currency` varchar(16) DEFAULT NULL,
  `created_time` bigint(20) NOT NULL,
  `updated_time` bigint(20) DEFAULT NULL,
  `status` int(10) NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
--
-- 转储表的索引
--

--
-- 表的索引 `forward`
--
ALTER TABLE `forward`
  ADD PRIMARY KEY (`id`);

--
-- 表的索引 `aggregate_node_group`
--
ALTER TABLE `aggregate_node_group`
  ADD PRIMARY KEY (`id`);

--
-- 表的索引 `aggregate_forward`
--
ALTER TABLE `aggregate_forward`
  ADD PRIMARY KEY (`id`);

--
-- 表的索引 `node`
--
ALTER TABLE `node`
  ADD PRIMARY KEY (`id`);

--
-- 表的索引 `speed_limit`
--
ALTER TABLE `speed_limit`
  ADD PRIMARY KEY (`id`);

--
-- 表的索引 `statistics_flow`
--
ALTER TABLE `statistics_flow`
  ADD PRIMARY KEY (`id`);

--
-- 表的索引 `tunnel`
--
ALTER TABLE `tunnel`
  ADD PRIMARY KEY (`id`);

--
-- 表的索引 `user`
--
ALTER TABLE `user`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `invite_code` (`invite_code`);

--
-- 表的索引 `user_tunnel`
--
ALTER TABLE `user_tunnel`
  ADD PRIMARY KEY (`id`);

--
-- 表的索引 `vite_config`
--
ALTER TABLE `vite_config`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `name` (`name`);

--
-- 表的索引 `package_plan`
--
ALTER TABLE `package_plan`
  ADD PRIMARY KEY (`id`);

--
-- 表的索引 `device_group`
--
ALTER TABLE `device_group`
  ADD PRIMARY KEY (`id`);

--
-- 表的索引 `user_group`
--
ALTER TABLE `user_group`
  ADD PRIMARY KEY (`id`);

--
-- 表的索引 `user_group_device_group`
--
ALTER TABLE `user_group_device_group`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `user_device_group` (`user_group_id`,`device_group_id`);

--
-- 表的索引 `order_record`
--
ALTER TABLE `order_record`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `order_no` (`order_no`),
  ADD KEY `provider_trade_no` (`provider_trade_no`);

--
-- 表的索引 `redeem_code`
--
ALTER TABLE `redeem_code`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `code` (`code`);

--
-- 表的索引 `invite_record`
--
ALTER TABLE `invite_record`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `invitee_user_id` (`invitee_user_id`);

--
-- 表的索引 `invite_reward_record`
--
ALTER TABLE `invite_reward_record`
  ADD PRIMARY KEY (`id`);

--
-- 表的索引 `payment_config`
--
ALTER TABLE `payment_config`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `channel` (`channel`);

--
-- 在导出的表使用AUTO_INCREMENT
--

--
-- 使用表AUTO_INCREMENT `forward`
--
ALTER TABLE `forward`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- 使用表AUTO_INCREMENT `aggregate_node_group`
--
ALTER TABLE `aggregate_node_group`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- 使用表AUTO_INCREMENT `aggregate_forward`
--
ALTER TABLE `aggregate_forward`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- 使用表AUTO_INCREMENT `node`
--
ALTER TABLE `node`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- 使用表AUTO_INCREMENT `speed_limit`
--
ALTER TABLE `speed_limit`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- 使用表AUTO_INCREMENT `statistics_flow`
--
ALTER TABLE `statistics_flow`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- 使用表AUTO_INCREMENT `tunnel`
--
ALTER TABLE `tunnel`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- 使用表AUTO_INCREMENT `user`
--
ALTER TABLE `user`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- 使用表AUTO_INCREMENT `user_tunnel`
--
ALTER TABLE `user_tunnel`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- 使用表AUTO_INCREMENT `vite_config`
--
ALTER TABLE `vite_config`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- 使用表AUTO_INCREMENT `package_plan`
--
ALTER TABLE `package_plan`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- 使用表AUTO_INCREMENT `device_group`
--
ALTER TABLE `device_group`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- 使用表AUTO_INCREMENT `user_group`
--
ALTER TABLE `user_group`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- 使用表AUTO_INCREMENT `user_group_device_group`
--
ALTER TABLE `user_group_device_group`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- 使用表AUTO_INCREMENT `order_record`
--
ALTER TABLE `order_record`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- 使用表AUTO_INCREMENT `redeem_code`
--
ALTER TABLE `redeem_code`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- 使用表AUTO_INCREMENT `invite_record`
--
ALTER TABLE `invite_record`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;

--
-- 使用表AUTO_INCREMENT `invite_reward_record`
--
ALTER TABLE `invite_reward_record`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;
--
-- 使用表AUTO_INCREMENT `payment_config`
--
ALTER TABLE `payment_config`
  MODIFY `id` int(10) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
