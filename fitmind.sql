-- FitMind database initialization script
-- Target: MySQL 8.0+

CREATE DATABASE IF NOT EXISTS `fitmind`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `fitmind`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `user_achievement`;
DROP TABLE IF EXISTS `achievement`;
DROP TABLE IF EXISTS `food_record`;
DROP TABLE IF EXISTS `food_item`;
DROP TABLE IF EXISTS `user_body_metric_log`;
DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `notification`;
DROP TABLE IF EXISTS `exercise`;
DROP TABLE IF EXISTS `user_follow`;
DROP TABLE IF EXISTS `community_comment`;
DROP TABLE IF EXISTS `community_post_like`;
DROP TABLE IF EXISTS `community_post`;
DROP TABLE IF EXISTS `ai_diet_plan`;
DROP TABLE IF EXISTS `ai_training_plan`;
DROP TABLE IF EXISTS `user_body_profile`;
DROP TABLE IF EXISTS `sys_user`;

CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'User ID',
  `username` VARCHAR(50) NOT NULL COMMENT 'Username',
  `password` VARCHAR(100) NOT NULL COMMENT 'BCrypt password hash',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT 'Nickname',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT 'Avatar URL',
  `email` VARCHAR(100) DEFAULT NULL COMMENT 'Email',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT 'Phone',
  `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'Role',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=active, 0=disabled',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  `last_login_time` DATETIME DEFAULT NULL COMMENT 'Last login time',
  `profile_prompt_required` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether profile form should be shown',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  UNIQUE KEY `uk_sys_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_body_profile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Profile ID',
  `user_id` BIGINT NOT NULL COMMENT 'User ID',
  `height` DECIMAL(5, 2) DEFAULT NULL COMMENT 'Height in cm',
  `weight` DECIMAL(5, 2) DEFAULT NULL COMMENT 'Weight in kg',
  `age` INT DEFAULT NULL COMMENT 'Age',
  `gender` TINYINT DEFAULT NULL COMMENT '1=male, 2=female, 0=unknown',
  `body_shape` VARCHAR(20) DEFAULT NULL COMMENT 'Body shape',
  `body_fat_percentage` DECIMAL(5, 2) DEFAULT NULL COMMENT 'Body fat percentage',
  `fitness_goal` VARCHAR(100) DEFAULT NULL COMMENT 'Primary goal',
  `training_goals` VARCHAR(500) DEFAULT NULL COMMENT 'Goal tags',
  `activity_level` VARCHAR(20) DEFAULT NULL COMMENT 'Activity level',
  `has_injury` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether user has injury',
  `injury_parts` VARCHAR(500) DEFAULT NULL COMMENT 'Injury parts',
  `profile_completed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether profile is completed',
  `last_profile_update_time` DATETIME DEFAULT NULL COMMENT 'Last profile update time',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_body_profile_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_body_metric_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Metric log ID',
  `user_id` BIGINT NOT NULL COMMENT 'User ID',
  `height` DECIMAL(5, 2) DEFAULT NULL COMMENT 'Height in cm',
  `weight` DECIMAL(5, 2) DEFAULT NULL COMMENT 'Weight in kg',
  `body_fat_percentage` DECIMAL(5, 2) DEFAULT NULL COMMENT 'Body fat percentage',
  `record_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Metric record time',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (`id`),
  KEY `idx_user_body_metric_log_user_time` (`user_id`, `record_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_training_plan` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Plan ID',
  `user_id` BIGINT NOT NULL COMMENT 'User ID',
  `plan_date` DATE NOT NULL COMMENT 'Plan date',
  `plan_name` VARCHAR(100) NOT NULL COMMENT 'Plan name',
  `target_muscle_group` VARCHAR(100) DEFAULT NULL COMMENT 'Target muscle group',
  `estimated_duration` INT DEFAULT NULL COMMENT 'Estimated duration in minutes',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=pending, 1=completed, 2=partial',
  `content` JSON DEFAULT NULL COMMENT 'Generated plan content',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (`id`),
  KEY `idx_ai_training_plan_user_date` (`user_id`, `plan_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_diet_plan` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Plan ID',
  `user_id` BIGINT NOT NULL COMMENT 'User ID',
  `plan_date` DATE NOT NULL COMMENT 'Plan date',
  `total_calories` INT DEFAULT NULL COMMENT 'Target calories',
  `protein` DECIMAL(6, 2) DEFAULT NULL COMMENT 'Protein grams',
  `carbs` DECIMAL(6, 2) DEFAULT NULL COMMENT 'Carb grams',
  `fat` DECIMAL(6, 2) DEFAULT NULL COMMENT 'Fat grams',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=pending, 1=completed',
  `content` JSON DEFAULT NULL COMMENT 'Generated diet content',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (`id`),
  KEY `idx_ai_diet_plan_user_date` (`user_id`, `plan_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `community_post` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Post ID',
  `user_id` BIGINT NOT NULL COMMENT 'User ID',
  `content` TEXT NOT NULL COMMENT 'Post content',
  `likes` INT NOT NULL DEFAULT 0 COMMENT 'Like count',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (`id`),
  KEY `idx_community_post_user_time` (`user_id`, `create_time`),
  KEY `idx_community_post_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `community_comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Comment ID',
  `post_id` BIGINT NOT NULL COMMENT 'Post ID',
  `user_id` BIGINT NOT NULL COMMENT 'User ID',
  `content` TEXT NOT NULL COMMENT 'Comment content',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (`id`),
  KEY `idx_community_comment_post_time` (`post_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `community_post_like` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Like ID',
  `post_id` BIGINT NOT NULL COMMENT 'Post ID',
  `user_id` BIGINT NOT NULL COMMENT 'User ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_community_post_like_post_user` (`post_id`, `user_id`),
  KEY `idx_community_post_like_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_follow` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Follow ID',
  `follower_id` BIGINT NOT NULL COMMENT 'Follower user ID',
  `following_id` BIGINT NOT NULL COMMENT 'Following user ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_follow_pair` (`follower_id`, `following_id`),
  KEY `idx_user_follow_follower` (`follower_id`),
  KEY `idx_user_follow_following` (`following_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `achievement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `icon` VARCHAR(50) DEFAULT NULL,
  `name` VARCHAR(100) NOT NULL,
  `description` VARCHAR(500) DEFAULT NULL,
  `category` VARCHAR(50) NOT NULL,
  `rarity` VARCHAR(20) DEFAULT 'COMMON',
  `target` INT DEFAULT 1,
  `sort_order` INT DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_achievement` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `exercise` (
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
  `type` VARCHAR(20) DEFAULT 'strength',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `notification` (
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
  KEY `idx_notification_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `chat_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `session_id` VARCHAR(100) NOT NULL,
  `role` VARCHAR(20) NOT NULL,
  `content` TEXT NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chat_session` (`user_id`, `session_id`, `create_time`),
  KEY `idx_chat_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `food_item` (
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
  `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `food_record` (
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
  KEY `idx_food_record_user` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `sys_user`
(`id`, `username`, `password`, `nickname`, `role`, `status`, `profile_prompt_required`)
VALUES
(1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 'admin', 'ADMIN', 1, 1),
(2, 'demo', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 'demo', 'USER', 1, 1);

INSERT INTO `achievement` (`icon`, `name`, `description`, `category`, `rarity`, `target`, `sort_order`) VALUES
('seed', 'First Workout', 'Complete your first workout session.', 'training', 'COMMON', 1, 1),
('streak', 'Week Streak', 'Stay active for 7 days.', 'training', 'COMMON', 7, 2),
('steel', 'Iron Will', 'Complete 30 workout sessions.', 'training', 'RARE', 30, 3),
('hundred', 'Centurion', 'Log 100 workout sessions.', 'training', 'EPIC', 100, 4),
('legend', 'No Days Off', 'Train continuously for 30 days.', 'training', 'LEGENDARY', 30, 5),
('lift', 'Volume King', 'Accumulate 10000 kg total training volume.', 'training', 'RARE', 10000, 6),
('meal', 'First Meal', 'Record your first diet entry.', 'nutrition', 'COMMON', 1, 7),
('protein', 'Protein Target', 'Hit your protein target for 14 days.', 'nutrition', 'RARE', 14, 8),
('macro', 'Nutrition Expert', 'Meet all macro targets for 30 days.', 'nutrition', 'EPIC', 30, 9),
('post', 'First Post', 'Publish your first community post.', 'social', 'COMMON', 1, 10),
('social', 'Social Star', 'Receive 100 likes on your posts.', 'social', 'RARE', 100, 11),
('network', 'Core Network', 'Reach 50 followers and follow 50 users.', 'social', 'EPIC', 50, 12);

INSERT INTO `food_item`
(`id`, `name`, `name_en`, `calories`, `protein`, `carbs`, `fat`, `fiber`, `serving_size`)
VALUES
('1', 'Apple', 'Apple', 52, 0.3, 14, 0.2, 2.4, '100g'),
('2', 'Banana', 'Banana', 91, 1.1, 23, 0.3, 2.6, '100g'),
('3', 'Rice', 'Rice', 130, 2.7, 28, 0.3, 0.4, '100g'),
('4', 'Chicken Breast', 'Chicken Breast', 165, 31, 0, 3.6, 0, '100g'),
('5', 'Broccoli', 'Broccoli', 34, 2.8, 7, 0.4, 2.6, '100g'),
('6', 'Egg', 'Egg', 143, 13, 1.1, 10, 0, '1 piece'),
('7', 'Milk', 'Milk', 42, 3.4, 5, 1, 0, '100ml'),
('8', 'Bread', 'Bread', 250, 8.8, 49, 3.2, 2.7, '100g'),
('9', 'Beef', 'Beef', 250, 26, 0, 17, 0, '100g'),
('10', 'Fish', 'Fish', 120, 20, 0, 5, 0, '100g'),
('11', 'Oatmeal', 'Oatmeal', 389, 17, 66, 7, 10, '100g'),
('12', 'Yogurt', 'Yogurt', 100, 2.8, 12, 5, 0, '100g'),
('13', 'Orange', 'Orange', 47, 1, 12, 0.2, 2.4, '100g'),
('14', 'Spinach', 'Spinach', 23, 2.9, 4, 0.4, 2.2, '100g'),
('15', 'Potato', 'Potato', 77, 2.5, 18, 0.2, 1.6, '100g'),
('16', 'Cucumber', 'Cucumber', 16, 0.6, 3.6, 0.1, 0.5, '100g'),
('17', 'Tomato', 'Tomato', 18, 0.9, 3.9, 0.2, 1.2, '100g'),
('18', 'Nuts', 'Nuts', 585, 15, 20, 49, 7, '100g'),
('19', 'Tofu', 'Tofu', 70, 8, 2, 4, 0.5, '100g'),
('20', 'Sweet Potato', 'Sweet Potato', 86, 1.6, 20, 0.1, 3, '100g');

SET FOREIGN_KEY_CHECKS = 1;
