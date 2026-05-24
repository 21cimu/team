package com.fitmind.module.community.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommunitySchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `community_post_like` (
                  `id` BIGINT NOT NULL AUTO_INCREMENT,
                  `post_id` BIGINT NOT NULL,
                  `user_id` BIGINT NOT NULL,
                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_community_post_like_post_user` (`post_id`, `user_id`),
                  KEY `idx_community_post_like_user` (`user_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }
}
