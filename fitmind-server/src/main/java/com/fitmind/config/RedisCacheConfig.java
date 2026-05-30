package com.fitmind.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fitmind.common.cache.CacheNames;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper redisObjectMapper = new ObjectMapper();
        redisObjectMapper.registerModule(new JavaTimeModule());
        redisObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        redisObjectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put(CacheNames.DASHBOARD_STATS, defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put(CacheNames.DASHBOARD_WEEKLY_TRAINING, defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put(CacheNames.DASHBOARD_HEATMAP, defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigs.put(CacheNames.DASHBOARD_NUTRITION_TODAY, defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put(CacheNames.DASHBOARD_BODY_METRICS_TREND, defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put(CacheNames.TRAINING_TODAY, defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put(CacheNames.DIET_TODAY, defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put(CacheNames.COMMUNITY_FEED, defaultConfig.entryTtl(Duration.ofMinutes(2)));
        cacheConfigs.put(CacheNames.NOTIFICATION_LIST, defaultConfig.entryTtl(Duration.ofMinutes(1)));
        cacheConfigs.put(CacheNames.NOTIFICATION_UNREAD_COUNT, defaultConfig.entryTtl(Duration.ofSeconds(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()
                .build();
    }
}
