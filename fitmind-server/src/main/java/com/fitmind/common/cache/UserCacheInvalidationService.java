package com.fitmind.common.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class UserCacheInvalidationService {

    private final CacheManager cacheManager;

    public void evictTrainingPlanData(Long userId) {
        afterCommitOrNow(() -> {
            evict(CacheNames.TRAINING_TODAY, userId);
            evict(CacheNames.DASHBOARD_STATS, userId);
            evict(CacheNames.DASHBOARD_WEEKLY_TRAINING, userId);
            evict(CacheNames.DASHBOARD_HEATMAP, userId);
        });
    }

    public void evictDietPlanData(Long userId) {
        afterCommitOrNow(() -> {
            evict(CacheNames.DIET_TODAY, userId);
            evict(CacheNames.DASHBOARD_STATS, userId);
            evict(CacheNames.DASHBOARD_HEATMAP, userId);
            evict(CacheNames.DASHBOARD_NUTRITION_TODAY, userId);
        });
    }

    public void evictProfileData(Long userId) {
        afterCommitOrNow(() -> evict(CacheNames.DASHBOARD_BODY_METRICS_TREND, userId));
    }

    public void evictNotifications(Long userId) {
        afterCommitOrNow(() -> {
            evict(CacheNames.NOTIFICATION_LIST, userId);
            evict(CacheNames.NOTIFICATION_UNREAD_COUNT, userId);
        });
    }

    public void evictCommunityFeed() {
        afterCommitOrNow(() -> clear(CacheNames.COMMUNITY_FEED));
    }

    private void evict(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    private void clear(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    private void afterCommitOrNow(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }
}
