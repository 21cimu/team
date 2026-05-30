package com.fitmind.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fitmind.common.cache.UserCacheInvalidationService;
import com.fitmind.module.user.entity.SysUser;
import com.fitmind.module.user.entity.UserBodyMetricLog;
import com.fitmind.module.user.entity.UserBodyProfile;
import com.fitmind.module.user.mapper.UserBodyMetricLogMapper;
import com.fitmind.module.user.mapper.SysUserMapper;
import com.fitmind.module.user.mapper.UserBodyProfileMapper;
import com.fitmind.module.user.service.IUserBodyProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserBodyProfileServiceImpl extends ServiceImpl<UserBodyProfileMapper, UserBodyProfile> implements IUserBodyProfileService {

    private final SysUserMapper sysUserMapper;
    private final UserBodyMetricLogMapper userBodyMetricLogMapper;
    private final UserCacheInvalidationService cacheInvalidationService;

    @Override
    public UserBodyProfile getByUsername(String username) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return this.getOne(new LambdaQueryWrapper<UserBodyProfile>().eq(UserBodyProfile::getUserId, user.getId()));
    }

    @Override
    public void saveOrUpdateProfile(String username, UserBodyProfile profile) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        UserBodyProfile existingProfile = this.getOne(new LambdaQueryWrapper<UserBodyProfile>().eq(UserBodyProfile::getUserId, user.getId()));
        
        profile.setUserId(user.getId());
        if (profile.getHasInjury() == null) {
            profile.setHasInjury(false);
        }
        if (!Boolean.TRUE.equals(profile.getHasInjury())) {
            profile.setInjuryParts(null);
        }
        if ((profile.getFitnessGoal() == null || profile.getFitnessGoal().isBlank())
                && profile.getTrainingGoals() != null && !profile.getTrainingGoals().isBlank()) {
            profile.setFitnessGoal(profile.getTrainingGoals().split(",")[0]);
        }
        profile.setProfileCompleted(true);
        profile.setLastProfileUpdateTime(LocalDateTime.now());
        profile.setUpdateTime(LocalDateTime.now());
        
        if (existingProfile == null) {
            profile.setCreateTime(LocalDateTime.now());
            this.save(profile);
        } else {
            profile.setId(existingProfile.getId());
            this.updateById(profile);
        }

        saveMetricSnapshot(profile);

        user.setProfilePromptRequired(false);
        sysUserMapper.updateById(user);
        cacheInvalidationService.evictProfileData(user.getId());
    }

    private void saveMetricSnapshot(UserBodyProfile profile) {
        BigDecimal currentWeight = profile.getWeight();
        BigDecimal currentBodyFat = profile.getBodyFatPercentage();
        if (currentWeight == null && currentBodyFat == null) {
            return;
        }

        UserBodyMetricLog latestLog = userBodyMetricLogMapper.selectOne(
                new LambdaQueryWrapper<UserBodyMetricLog>()
                        .eq(UserBodyMetricLog::getUserId, profile.getUserId())
                        .orderByDesc(UserBodyMetricLog::getRecordTime)
                        .orderByDesc(UserBodyMetricLog::getCreateTime)
                        .last("LIMIT 1"));

        if (latestLog != null
                && metricEquals(latestLog.getWeight(), currentWeight)
                && metricEquals(latestLog.getBodyFatPercentage(), currentBodyFat)) {
            return;
        }

        LocalDateTime recordTime = profile.getLastProfileUpdateTime() != null ? profile.getLastProfileUpdateTime() : LocalDateTime.now();
        LocalDate recordDate = recordTime.toLocalDate();
        UserBodyMetricLog sameDayLog = userBodyMetricLogMapper.selectOne(
                new LambdaQueryWrapper<UserBodyMetricLog>()
                        .eq(UserBodyMetricLog::getUserId, profile.getUserId())
                        .ge(UserBodyMetricLog::getRecordTime, recordDate.atStartOfDay())
                        .lt(UserBodyMetricLog::getRecordTime, recordDate.plusDays(1).atStartOfDay())
                        .orderByDesc(UserBodyMetricLog::getRecordTime)
                        .orderByDesc(UserBodyMetricLog::getCreateTime)
                        .last("LIMIT 1"));

        if (sameDayLog != null) {
            sameDayLog.setHeight(profile.getHeight());
            sameDayLog.setWeight(currentWeight);
            sameDayLog.setBodyFatPercentage(currentBodyFat);
            sameDayLog.setRecordTime(recordTime);
            userBodyMetricLogMapper.updateById(sameDayLog);
            return;
        }

        UserBodyMetricLog log = new UserBodyMetricLog();
        log.setUserId(profile.getUserId());
        log.setHeight(profile.getHeight());
        log.setWeight(currentWeight);
        log.setBodyFatPercentage(currentBodyFat);
        log.setRecordTime(recordTime);
        log.setCreateTime(LocalDateTime.now());
        userBodyMetricLogMapper.insert(log);
    }

    private boolean metricEquals(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
    }
}
