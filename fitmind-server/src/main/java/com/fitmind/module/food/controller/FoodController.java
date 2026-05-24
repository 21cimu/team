package com.fitmind.module.food.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmind.common.api.Result;
import com.fitmind.module.food.entity.FoodItem;
import com.fitmind.module.food.entity.FoodRecord;
import com.fitmind.module.food.mapper.FoodRecordMapper;
import com.fitmind.module.food.service.IFoodItemService;
import com.fitmind.module.user.entity.SysUser;
import com.fitmind.module.user.mapper.SysUserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/food")
@RequiredArgsConstructor
@Slf4j
public class FoodController {

    private final IFoodItemService foodItemService;
    private final FoodRecordMapper foodRecordMapper;
    private final SysUserMapper sysUserMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.qwen.api-url}")
    private String qwenApiUrl;

    @Value("${ai.qwen.api-key:}")
    private String qwenApiKey;

    @Value("${ai.qwen.vision-model:qwen3-vl-plus}")
    private String qwenVisionModel;

    private SysUser getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new RuntimeException("用户未认证");
        }
        return user;
    }

    @PostMapping("/recognize")
    public Result<Map<String, Object>> recognizeFood(@RequestBody Map<String, String> request) {
        String image = request.get("image");

        if (image == null || image.isEmpty()) {
            return Result.error("图片不能为空");
        }

        try {
            Map<String, Object> result = recognizeWithVision(image);
            return Result.success(result);
        } catch (Exception e) {
            log.warn("Vision recognition failed, falling back to mock: {}", e.getMessage());
            return Result.success(recognizeWithMock());
        }
    }

    private Map<String, Object> recognizeWithVision(String base64Image) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (qwenApiKey == null || qwenApiKey.isBlank()) {
            throw new IllegalStateException("Qwen vision API key is not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(qwenApiKey);

        String imageUrl;
        if (base64Image.startsWith("data:image")) {
            imageUrl = base64Image;
        } else {
            imageUrl = "data:image/jpeg;base64," + base64Image;
        }

        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", "Identify all food items in this image. For each food, provide: name (Chinese), nameEn (English), calories (per 100g), protein (g), carbs (g), fat (g), fiber (g), servingSize, confidence (0-1). Return ONLY a valid JSON array: [{\"name\":\"...\",\"nameEn\":\"...\",\"calories\":0,\"protein\":0,\"carbs\":0,\"fat\":0,\"fiber\":0,\"servingSize\":\"100g\",\"confidence\":0.9}]");

        Map<String, Object> imageUrlContent = new HashMap<>();
        imageUrlContent.put("type", "image_url");
        Map<String, Object> urlObj = new HashMap<>();
        urlObj.put("url", imageUrl);
        imageUrlContent.put("image_url", urlObj);

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", List.of(textContent, imageUrlContent));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", qwenVisionModel);
        requestBody.put("messages", List.of(userMsg));
        requestBody.put("temperature", 0.3);

        HttpEntity<Map<String, Object>> httpReq = new HttpEntity<>(requestBody, headers);

        String response = restTemplate.postForObject(qwenApiUrl + "/chat/completions", httpReq, String.class);
        JsonNode root = objectMapper.readTree(response);
        String content = root.path("choices").get(0).path("message").path("content").asText();

        if (content.startsWith("```json")) {
            content = content.replace("```json", "").replace("```", "").trim();
        }
        if (content.startsWith("```")) {
            content = content.replace("```", "").trim();
        }

        JsonNode foodsArray = objectMapper.readTree(content);

        List<Map<String, Object>> foods = new ArrayList<>();
        double totalCalories = 0, totalProtein = 0, totalCarbs = 0, totalFat = 0;

        for (JsonNode item : foodsArray) {
            Map<String, Object> food = new HashMap<>();
            food.put("id", java.util.UUID.randomUUID().toString());
            food.put("name", item.path("name").asText("Unknown"));
            food.put("nameEn", item.path("nameEn").asText("Unknown"));
            food.put("calories", item.path("calories").asDouble(0));
            food.put("protein", item.path("protein").asDouble(0));
            food.put("carbs", item.path("carbs").asDouble(0));
            food.put("fat", item.path("fat").asDouble(0));
            food.put("fiber", item.path("fiber").asDouble(0));
            food.put("servingSize", item.path("servingSize").asText("100g"));
            food.put("confidence", item.path("confidence").asDouble(0.5));
            foods.add(food);

            totalCalories += item.path("calories").asDouble(0);
            totalProtein += item.path("protein").asDouble(0);
            totalCarbs += item.path("carbs").asDouble(0);
            totalFat += item.path("fat").asDouble(0);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Vision识别成功");
        result.put("source", "qwen-vision");
        result.put("foods", foods);
        result.put("totalCalories", Math.round(totalCalories));
        result.put("totalProtein", Math.round(totalProtein * 10) / 10.0);
        result.put("totalCarbs", Math.round(totalCarbs * 10) / 10.0);
        result.put("totalFat", Math.round(totalFat * 10) / 10.0);

        return result;
    }

    private Map<String, Object> recognizeWithMock() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "识别成功 (模拟数据)");
        result.put("source", "mock");

        List<Map<String, Object>> foods = new ArrayList<>();

        String[][] foodData = {
            {"苹果", "Apple", "52", "0.3", "14", "0.2", "2.4", "100g", "0.95"},
            {"香蕉", "Banana", "91", "1.1", "23", "0.3", "2.6", "100g", "0.92"},
            {"米饭", "Rice", "130", "2.7", "28", "0.3", "0.4", "100g", "0.88"},
            {"鸡胸肉", "Chicken Breast", "165", "31", "0", "3.6", "0", "100g", "0.96"},
            {"西兰花", "Broccoli", "34", "2.8", "7", "0.4", "2.6", "100g", "0.93"},
            {"鸡蛋", "Egg", "143", "13", "1.1", "10", "0", "1个", "0.98"},
            {"牛奶", "Milk", "42", "3.4", "5", "1", "0", "100ml", "0.94"},
            {"面包", "Bread", "250", "8.8", "49", "3.2", "2.7", "100g", "0.85"},
        };

        Random random = new Random();
        int count = random.nextInt(3) + 1;

        double totalCalories = 0;
        double totalProtein = 0;
        double totalCarbs = 0;
        double totalFat = 0;

        for (int i = 0; i < count; i++) {
            String[] data = foodData[random.nextInt(foodData.length)];
            Map<String, Object> food = new HashMap<>();
            food.put("id", java.util.UUID.randomUUID().toString());
            food.put("name", data[0]);
            food.put("nameEn", data[1]);
            food.put("calories", Double.parseDouble(data[2]));
            food.put("protein", Double.parseDouble(data[3]));
            food.put("carbs", Double.parseDouble(data[4]));
            food.put("fat", Double.parseDouble(data[5]));
            food.put("fiber", Double.parseDouble(data[6]));
            food.put("servingSize", data[7]);
            food.put("confidence", Double.parseDouble(data[8]));
            foods.add(food);

            totalCalories += Double.parseDouble(data[2]);
            totalProtein += Double.parseDouble(data[3]);
            totalCarbs += Double.parseDouble(data[4]);
            totalFat += Double.parseDouble(data[5]);
        }

        result.put("foods", foods);
        result.put("totalCalories", Math.round(totalCalories));
        result.put("totalProtein", Math.round(totalProtein * 10) / 10.0);
        result.put("totalCarbs", Math.round(totalCarbs * 10) / 10.0);
        result.put("totalFat", Math.round(totalFat * 10) / 10.0);

        return result;
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
            foods = generateMockFoods();
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
            SysUser user = getCurrentUser();
            FoodRecord record = new FoodRecord();
            record.setUserId(user.getId());
            record.setFoodId(request.getFoodId());
            record.setFoodName(request.getFoodName());
            record.setCalories(request.getCalories());
            record.setProtein(request.getProtein());
            record.setCarbs(request.getCarbs());
            record.setFat(request.getFat());
            record.setServingSize(request.getServingSize());
            record.setCreateTime(java.time.LocalDateTime.now());
            foodRecordMapper.insert(record);
            return Result.success("饮食记录添加成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/records")
    public Result<List<FoodRecord>> getMyFoodRecords() {
        try {
            SysUser user = getCurrentUser();
            List<FoodRecord> records = foodRecordMapper.selectList(
                    new LambdaQueryWrapper<FoodRecord>()
                            .eq(FoodRecord::getUserId, user.getId())
                            .orderByDesc(FoodRecord::getCreateTime));
            return Result.success(records);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Data
    static class FoodRecordRequest {
        private String foodId;
        private String foodName;
        private Double calories;
        private Double protein;
        private Double carbs;
        private Double fat;
        private String servingSize;
    }

    private List<FoodItem> generateMockFoods() {
        List<FoodItem> foods = new ArrayList<>();
        
        String[][] foodData = {
            {"苹果", "Apple", "52", "0.3", "14", "0.2", "2.4", "100g"},
            {"香蕉", "Banana", "91", "1.1", "23", "0.3", "2.6", "100g"},
            {"米饭", "Rice", "130", "2.7", "28", "0.3", "0.4", "100g"},
            {"鸡胸肉", "Chicken Breast", "165", "31", "0", "3.6", "0", "100g"},
            {"西兰花", "Broccoli", "34", "2.8", "7", "0.4", "2.6", "100g"},
            {"鸡蛋", "Egg", "143", "13", "1.1", "10", "0", "1个"},
            {"牛奶", "Milk", "42", "3.4", "5", "1", "0", "100ml"},
            {"面包", "Bread", "250", "8.8", "49", "3.2", "2.7", "100g"},
        };

        for (String[] data : foodData) {
            FoodItem item = new FoodItem();
            item.setId(java.util.UUID.randomUUID().toString());
            item.setName(data[0]);
            item.setNameEn(data[1]);
            item.setCalories(Double.parseDouble(data[2]));
            item.setProtein(Double.parseDouble(data[3]));
            item.setCarbs(Double.parseDouble(data[4]));
            item.setFat(Double.parseDouble(data[5]));
            item.setFiber(Double.parseDouble(data[6]));
            item.setServingSize(data[7]);
            foods.add(item);
        }

        return foods;
    }
}
