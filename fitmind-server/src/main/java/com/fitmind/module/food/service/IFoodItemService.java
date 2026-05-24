package com.fitmind.module.food.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fitmind.module.food.entity.FoodItem;

import java.util.List;

public interface IFoodItemService extends IService<FoodItem> {
    List<FoodItem> searchByName(String keyword);
    List<FoodItem> findCommonFoods();
    FoodItem getById(String id);
}