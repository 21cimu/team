package com.fitmind.module.food.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.module.food.dto.FoodRecordRequest;
import com.fitmind.module.food.entity.FoodRecord;
import com.fitmind.module.food.mapper.FoodRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodRecordService {

    private final FoodRecordMapper foodRecordMapper;

    public void addFoodRecord(Long userId, FoodRecordRequest request) {
        FoodRecord record = new FoodRecord();
        record.setUserId(userId);
        record.setFoodId(request.getFoodId());
        record.setFoodName(request.getFoodName());
        record.setCalories(request.getCalories());
        record.setProtein(request.getProtein());
        record.setCarbs(request.getCarbs());
        record.setFat(request.getFat());
        record.setServingSize(request.getServingSize());
        record.setCreateTime(LocalDateTime.now());
        foodRecordMapper.insert(record);
    }

    public List<FoodRecord> getUserFoodRecords(Long userId) {
        return foodRecordMapper.selectList(
                new LambdaQueryWrapper<FoodRecord>()
                        .eq(FoodRecord::getUserId, userId)
                        .orderByDesc(FoodRecord::getCreateTime));
    }
}
