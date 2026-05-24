CREATE TABLE IF NOT EXISTS `achievement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `icon` VARCHAR(50) DEFAULT NULL,
  `name` VARCHAR(100) NOT NULL,
  `description` VARCHAR(500) DEFAULT NULL,
  `category` VARCHAR(50) NOT NULL,
  `rarity` VARCHAR(20) DEFAULT 'COMMON',
  `target` INT DEFAULT 1,
  `sort_order` INT DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_achievement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `achievement_id` BIGINT NOT NULL,
  `progress` INT DEFAULT 0,
  `unlocked` TINYINT(1) DEFAULT 0,
  `unlock_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_achievement` (`user_id`, `achievement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `exercise` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `target` VARCHAR(200) DEFAULT NULL,
  `category` VARCHAR(50) NOT NULL,
  `difficulty` VARCHAR(20) DEFAULT 'BEGINNER',
  `equip_icon` VARCHAR(10) DEFAULT NULL,
  `description` TEXT DEFAULT NULL,
  `primary_muscle` VARCHAR(100) DEFAULT NULL,
  `secondary_muscles` VARCHAR(500) DEFAULT NULL,
  `reps` VARCHAR(50) DEFAULT NULL,
  `sets` INT DEFAULT NULL,
  `tips` TEXT DEFAULT NULL,
  `sort_order` INT DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(50) NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `content` TEXT DEFAULT NULL,
  `is_read` TINYINT(1) DEFAULT 0,
  `related_id` BIGINT DEFAULT NULL,
  `related_type` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_notification_user_read` (`user_id`, `is_read`),
  KEY `idx_notification_user_time` (`user_id`, `create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `chat_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `session_id` VARCHAR(100) NOT NULL,
  `role` VARCHAR(20) NOT NULL,
  `content` TEXT NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chat_session` (`user_id`, `session_id`, `create_time`),
  KEY `idx_chat_user_time` (`user_id`, `create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `food_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `food_id` VARCHAR(50) DEFAULT NULL,
  `food_name` VARCHAR(200) DEFAULT NULL,
  `calories` DOUBLE DEFAULT NULL,
  `protein` DOUBLE DEFAULT NULL,
  `carbs` DOUBLE DEFAULT NULL,
  `fat` DOUBLE DEFAULT NULL,
  `serving_size` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_food_record_user` (`user_id`, `create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `food_item` (
  `id` VARCHAR(50) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `name_en` VARCHAR(200) DEFAULT NULL,
  `calories` DOUBLE DEFAULT NULL,
  `protein` DOUBLE DEFAULT NULL,
  `carbs` DOUBLE DEFAULT NULL,
  `fat` DOUBLE DEFAULT NULL,
  `fiber` DOUBLE DEFAULT NULL,
  `serving_size` VARCHAR(50) DEFAULT NULL,
  `image_url` VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_body_metric_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `height` DECIMAL(10,2) DEFAULT NULL,
  `weight` DECIMAL(10,2) DEFAULT NULL,
  `body_fat_percentage` DECIMAL(10,2) DEFAULT NULL,
  `record_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_body_metric_log_user_time` (`user_id`, `record_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `community_post_like` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `post_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_community_post_like_post_user` (`post_id`, `user_id`),
  KEY `idx_community_post_like_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Performance indexes for existing tables
CREATE INDEX IF NOT EXISTS `idx_food_record_user` ON `food_record` (`user_id`, `create_time` DESC);
CREATE INDEX IF NOT EXISTS `idx_training_plan_user_date` ON `ai_training_plan` (`user_id`, `plan_date`);
CREATE INDEX IF NOT EXISTS `idx_diet_plan_user_date` ON `ai_diet_plan` (`user_id`, `plan_date`);
CREATE INDEX IF NOT EXISTS `idx_community_post_user` ON `community_post` (`user_id`, `create_time` DESC);
CREATE INDEX IF NOT EXISTS `idx_community_comment_post` ON `community_comment` (`post_id`, `create_time`);
CREATE INDEX IF NOT EXISTS `idx_user_follow_follower` ON `user_follow` (`follower_id`);
CREATE INDEX IF NOT EXISTS `idx_user_follow_following` ON `user_follow` (`following_id`);
CREATE INDEX IF NOT EXISTS `idx_sys_user_username` ON `sys_user` (`username`);
CREATE INDEX IF NOT EXISTS `idx_user_body_profile_user` ON `user_body_profile` (`user_id`);
CREATE INDEX IF NOT EXISTS `idx_body_metric_log_user_time` ON `user_body_metric_log` (`user_id`, `record_time` DESC);

INSERT INTO `achievement` (`icon`, `name`, `description`, `category`, `rarity`, `target`, `sort_order`) VALUES
('🔥', '初试锋芒', '完成您的第一次训练会话', 'training', 'COMMON', 1, 1),
('⚡', '周而复始', '连续保持 7 天的训练记录', 'training', 'COMMON', 7, 2),
('💎', '钢铁意志', '累计完成 30 次训练会话', 'training', 'RARE', 30, 3),
('👑', '百炼成钢', '累计记录 100 次训练会话', 'training', 'EPIC', 100, 4),
('🗡️', '永不停歇', '连续 30 天不间断训练', 'training', 'LEGENDARY', 30, 5),
('🏋️', '万金之王', '在一周内累计搬运 10,000 kg 的训练总量', 'training', 'RARE', 10000, 6),
('🥗', '纯净饮食', '记录您的第一份饮食计划', 'nutrition', 'COMMON', 1, 7),
('🥩', '蛋白质大亨', '连续 14 天达成蛋白质摄入目标', 'nutrition', 'RARE', 14, 8),
('🌿', '营养专家', '连续 30 天完美达成所有营养宏量指标', 'nutrition', 'EPIC', 30, 9),
('📡', '首次发声', '在社区网络发布您的第一条动态', 'social', 'COMMON', 1, 10),
('❤️', '社交达人', '您的动态累计获得 100 次点赞', 'social', 'RARE', 100, 11),
('🌐', '网络核心', '关注 50 名成员并拥有 50 名追随者', 'social', 'EPIC', 50, 12);

-- Exercise Type Strategy: Add type column to exercise table
ALTER TABLE `exercise` ADD COLUMN IF NOT EXISTS `type` VARCHAR(20) DEFAULT 'strength' AFTER `sort_order`;

-- Update existing exercise records with type based on category
UPDATE `exercise` SET `type` = 'cardio' WHERE `category` IN ('有氧', 'CARDIO', 'cardio') OR `name` LIKE '%跑%' OR `name` LIKE '%骑行%' OR `name` LIKE '%游泳%' OR `name` LIKE '%跳绳%' OR `name` LIKE '%划船%' OR `name` LIKE '%椭圆%' OR `name` LIKE '%登山%';
UPDATE `exercise` SET `type` = 'flexibility' WHERE `category` IN ('柔韧', 'FLEXIBILITY', 'flexibility', '拉伸', 'STRETCH') OR `name` LIKE '%拉伸%' OR `name` LIKE '%伸展%' OR `name` LIKE '%瑜伽%' OR `name` LIKE '%泡沫轴%';
