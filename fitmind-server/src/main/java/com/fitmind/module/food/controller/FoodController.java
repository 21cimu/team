package com.fitmind.module.food.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.common.api.Result;
import com.fitmind.common.security.CurrentUserProvider;
import com.fitmind.module.food.dto.FoodRecordRequest;
import com.fitmind.module.food.entity.FoodItem;
import com.fitmind.module.food.entity.FoodRecord;
import com.fitmind.module.food.service.FoodRecognitionService;
import com.fitmind.module.food.service.FoodRecordService;
import com.fitmind.module.food.service.IFoodItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/food")
@RequiredArgsConstructor
public class FoodController {

    private final CurrentUserProvider currentUserProvider;
    private final IFoodItemService foodItemService;
    private final FoodRecognitionService foodRecognitionService;
    private final FoodRecordService foodRecordService;

    @PostMapping("/recognize")
    public Result<Map<String, Object>> recognizeFood(@RequestBody Map<String, String> request) {
        try {
            return Result.success(foodRecognitionService.recognizeFood(request.get("image")));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/search")
    public Result<Map<String, Object>> searchFood(@RequestParam String keyword) {
        List<FoodItem> foods = foodItemService.searchByName(keyword);
        
        if (foods.isEmpty()) {
            foods = foodItemService.findCommonFoods();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("foods", foods);
        return Result.success(result);
    }

    @GetMapping("/common")
    public Result<Map<String, Object>> getCommonFoods() {
        List<FoodItem> foods = foodItemService.findCommonFoods();
        
        if (foods.isEmpty()) {
            foods = foodRecognitionService.generateMockFoods();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("foods", foods);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<FoodItem> getFoodDetail(@PathVariable String id) {
        FoodItem food = foodItemService.getById(id);
        if (food == null) {
            return Result.error("食物不存在");
        }
        return Result.success(food);
    }

    @PostMapping("/record")
    public Result<String> addFoodRecord(@RequestBody FoodRecordRequest request) {
        try {
            foodRecordService.addFoodRecord(currentUserProvider.getCurrentUserId(), request);
            return Result.success("饮食记录添加成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/records")
    public Result<List<FoodRecord>> getMyFoodRecords() {
        try {
            return Result.success(foodRecordService.getUserFoodRecords(currentUserProvider.getCurrentUserId()));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
