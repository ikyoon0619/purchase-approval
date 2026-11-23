-- V1: 11단계까지의 전체 테이블 (운영 첫 배포)
-- 만든 방법: 빈 MySQL 8에 ddl-auto=create로 엔티티 그대로 테이블을 만든 뒤 mysqldump --no-data 로 뽑았다.
-- 이후 엔티티를 바꾸면 이 파일은 고치지 말고 V2__설명.sql 을 새로 추가한다 (Flyway는 이미 실행한 파일의 체크섬을 검사한다).
-- Spring Batch 실행 이력 테이블(BATCH_*)은 spring.batch.jdbc.initialize-schema 가 만든다.

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `approval_line` (
  `round` int NOT NULL,
  `step_order` int NOT NULL,
  `approver_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pending_at` datetime(6) DEFAULT NULL,
  `processed_at` datetime(6) DEFAULT NULL,
  `request_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `step_name` varchar(20) NOT NULL,
  `comment` varchar(500) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_approval_line_approver_status` (`approver_id`,`status`),
  KEY `FKl37guj8auekjeheh47f0blwgu` (`request_id`),
  CONSTRAINT `FKl37guj8auekjeheh47f0blwgu` FOREIGN KEY (`request_id`) REFERENCES `purchase_request` (`id`),
  CONSTRAINT `FKpmfj68bi96hehb49hmqgv3ge` FOREIGN KEY (`approver_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `attachment` (
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` bigint NOT NULL,
  `size` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `content_type` varchar(100) DEFAULT NULL,
  `stored_name` varchar(100) NOT NULL,
  `original_name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKrocbhepttudplj7v35pjrt17i` (`request_id`),
  CONSTRAINT `FKrocbhepttudplj7v35pjrt17i` FOREIGN KEY (`request_id`) REFERENCES `purchase_request` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `audit_log` (
  `success` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint DEFAULT NULL,
  `login_id` varchar(30) NOT NULL,
  `target_type` varchar(30) NOT NULL,
  `ip` varchar(45) DEFAULT NULL,
  `target_id` varchar(50) DEFAULT NULL,
  `detail` varchar(500) DEFAULT NULL,
  `action` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_log_created` (`created_at`),
  KEY `idx_audit_log_login` (`login_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `code_group` (
  `use_yn` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(30) DEFAULT NULL,
  `group_code` varchar(30) NOT NULL,
  `updated_by` varchar(30) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  PRIMARY KEY (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `common_code` (
  `sort_order` int NOT NULL,
  `use_yn` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `code` varchar(30) NOT NULL,
  `created_by` varchar(30) DEFAULT NULL,
  `group_code` varchar(30) NOT NULL,
  `updated_by` varchar(30) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_common_code` (`group_code`,`code`),
  CONSTRAINT `FKmhd6umsf3hdfgwr8ukw3551re` FOREIGN KEY (`group_code`) REFERENCES `code_group` (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `department` (
  `use_yn` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `code` varchar(20) NOT NULL,
  `created_by` varchar(30) DEFAULT NULL,
  `updated_by` varchar(30) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKq8ymhgj6pt1msox0o3bg51uvo` (`code`),
  KEY `FKmgsnnmudxrwqidn4f64q8rp4o` (`parent_id`),
  CONSTRAINT `FKmgsnnmudxrwqidn4f64q8rp4o` FOREIGN KEY (`parent_id`) REFERENCES `department` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `error_log` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `login_id` varchar(30) DEFAULT NULL,
  `exception_class` varchar(200) NOT NULL,
  `source` varchar(300) NOT NULL,
  `message` varchar(1000) DEFAULT NULL,
  `stack_trace` text,
  PRIMARY KEY (`id`),
  KEY `idx_error_log_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `inventory` (
  `quantity` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKoh4flp8k6tpqi9bgx7oleryqd` (`item_id`),
  CONSTRAINT `FKrflym5lxj6xhmu4ok3ohmun5a` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `inventory_history` (
  `after_qty` int NOT NULL,
  `change_qty` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_id` bigint NOT NULL,
  `order_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(30) DEFAULT NULL,
  `updated_by` varchar(30) DEFAULT NULL,
  `memo` varchar(200) DEFAULT NULL,
  `change_type` varchar(10) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_inventory_history_item` (`item_id`,`id`),
  KEY `FK5i2inkkn95ep3e6vmdp23fpx6` (`order_id`),
  CONSTRAINT `FK1cc35s1rkyjdgdnlw3d06igt2` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`),
  CONSTRAINT `FK5i2inkkn95ep3e6vmdp23fpx6` FOREIGN KEY (`order_id`) REFERENCES `purchase_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `item` (
  `standard_price` decimal(15,0) NOT NULL,
  `use_yn` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `code` varchar(20) NOT NULL,
  `category_code` varchar(30) NOT NULL,
  `created_by` varchar(30) DEFAULT NULL,
  `unit_code` varchar(30) NOT NULL,
  `updated_by` varchar(30) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6cgogdarkq48dlg1lbnv4q1oq` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `member` (
  `enabled` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `department_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `position_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(30) DEFAULT NULL,
  `login_id` varchar(30) NOT NULL,
  `name` varchar(30) NOT NULL,
  `updated_by` varchar(30) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `password` varchar(100) NOT NULL,
  `role` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKenfm5patwjqulw8k4wwuo6f60` (`login_id`),
  KEY `FKlmd4h7lh9acdyvi0xxbvsqrmk` (`department_id`),
  KEY `FKcbkb0poq30jh6my2b38iwcq9b` (`position_id`),
  CONSTRAINT `FKcbkb0poq30jh6my2b38iwcq9b` FOREIGN KEY (`position_id`) REFERENCES `position` (`id`),
  CONSTRAINT `FKlmd4h7lh9acdyvi0xxbvsqrmk` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `menu` (
  `sort_order` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(30) DEFAULT NULL,
  `name` varchar(30) NOT NULL,
  `updated_by` varchar(30) DEFAULT NULL,
  `url` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKfva1u374y516dwyfua3c56ogi` (`url`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `menu_role` (
  `menu_id` bigint NOT NULL,
  `role` varchar(20) DEFAULT NULL,
  KEY `FK3gapbw9hw4pgxo6x90qb1b69` (`menu_id`),
  CONSTRAINT `FK3gapbw9hw4pgxo6x90qb1b69` FOREIGN KEY (`menu_id`) REFERENCES `menu` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `monthly_purchase_stat` (
  `request_count` int NOT NULL,
  `total_amount` decimal(15,0) NOT NULL,
  `stat_month` varchar(7) NOT NULL,
  `aggregated_at` datetime(6) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `department_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_monthly_stat_month_dept` (`stat_month`,`department_id`),
  KEY `FKvjb51r5f9spvt5o8wptuo4et` (`department_id`),
  CONSTRAINT `FKvjb51r5f9spvt5o8wptuo4et` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `notification` (
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `dedup_key` varchar(100) DEFAULT NULL,
  `link_url` varchar(200) DEFAULT NULL,
  `message` varchar(300) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK1y6h06cue5e8t0aufsh091stl` (`dedup_key`),
  KEY `idx_notification_member_read` (`member_id`,`read_at`),
  CONSTRAINT `FK1xep8o2ge7if6diclyyx53v4q` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `position` (
  `rank_order` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `name` varchar(20) NOT NULL,
  `created_by` varchar(30) DEFAULT NULL,
  `updated_by` varchar(30) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKqe48lxuex3swuovou3giy8qpk` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `purchase_order` (
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ordered_at` datetime(6) NOT NULL,
  `received_at` datetime(6) DEFAULT NULL,
  `request_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `vendor_id` bigint NOT NULL,
  `version` bigint DEFAULT NULL,
  `order_no` varchar(20) DEFAULT NULL,
  `created_by` varchar(30) DEFAULT NULL,
  `updated_by` varchar(30) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7e1dhnw8p4i52jnx4pq6cbug4` (`request_id`),
  UNIQUE KEY `UKku27u0bp84ldhex3hn8fwhqqc` (`order_no`),
  KEY `FK20jcn7pw6hvx0uo0sh4y1d9xv` (`vendor_id`),
  CONSTRAINT `FK20jcn7pw6hvx0uo0sh4y1d9xv` FOREIGN KEY (`vendor_id`) REFERENCES `vendor` (`id`),
  CONSTRAINT `FK52nooa9u3d90jhedepcagbema` FOREIGN KEY (`request_id`) REFERENCES `purchase_request` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `purchase_order_item` (
  `line_no` int NOT NULL,
  `quantity` int NOT NULL,
  `received_quantity` int DEFAULT NULL,
  `unit_price` decimal(15,0) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6hnf77cwyb21thxqtphoatybv` (`item_id`),
  KEY `FKfnkwl0pe7oycs9e8swib8cni9` (`order_id`),
  CONSTRAINT `FK6hnf77cwyb21thxqtphoatybv` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`),
  CONSTRAINT `FKfnkwl0pe7oycs9e8swib8cni9` FOREIGN KEY (`order_id`) REFERENCES `purchase_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `purchase_request` (
  `submit_round` int NOT NULL,
  `total_amount` decimal(15,0) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `department_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `requester_id` bigint NOT NULL,
  `submitted_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `vendor_id` bigint NOT NULL,
  `version` bigint DEFAULT NULL,
  `request_no` varchar(20) DEFAULT NULL,
  `created_by` varchar(30) DEFAULT NULL,
  `purpose_code` varchar(30) NOT NULL,
  `updated_by` varchar(30) DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `reason` varchar(2000) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKipaucryuhwajhmup0sb35oy4m` (`request_no`),
  KEY `FKgqgj0wupu0ltvkubm13avyw8p` (`department_id`),
  KEY `FKkpoyv7hj6lt8e75txiyngaj81` (`requester_id`),
  KEY `FKpmx14y2fsfdt02nilf72hq0ig` (`vendor_id`),
  CONSTRAINT `FKgqgj0wupu0ltvkubm13avyw8p` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`),
  CONSTRAINT `FKkpoyv7hj6lt8e75txiyngaj81` FOREIGN KEY (`requester_id`) REFERENCES `member` (`id`),
  CONSTRAINT `FKpmx14y2fsfdt02nilf72hq0ig` FOREIGN KEY (`vendor_id`) REFERENCES `vendor` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `purchase_request_item` (
  `amount` decimal(15,0) NOT NULL,
  `line_no` int NOT NULL,
  `quantity` int NOT NULL,
  `unit_price` decimal(15,0) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_id` bigint NOT NULL,
  `request_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKhge0nrkq3c9k5xjx70im0y1ln` (`item_id`),
  KEY `FK453qfoa9d7a5h30c9m4jb1lbu` (`request_id`),
  CONSTRAINT `FK453qfoa9d7a5h30c9m4jb1lbu` FOREIGN KEY (`request_id`) REFERENCES `purchase_request` (`id`),
  CONSTRAINT `FKhge0nrkq3c9k5xjx70im0y1ln` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `vendor` (
  `use_yn` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `status_checked_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `biz_no` varchar(10) NOT NULL,
  `ceo_name` varchar(30) DEFAULT NULL,
  `created_by` varchar(30) DEFAULT NULL,
  `updated_by` varchar(30) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `status` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKspcypys4x2drel4qcwhm3sg5n` (`biz_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
