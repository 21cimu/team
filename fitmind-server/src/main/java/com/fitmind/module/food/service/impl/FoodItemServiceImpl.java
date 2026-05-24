package com.fitmind.module.food.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fitmind.module.food.entity.FoodItem;
import com.fitmind.module.food.mapper.FoodItemMapper;
import com.fitmind.module.food.service.IFoodItemService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FoodItemServiceImpl extends ServiceImpl<FoodItemMapper, FoodItem> implements IFoodItemService {

    @Override
    public List<FoodItem> searchByName(String keyword) {
        return getBaseMapper().searchByName(keyword);
    }

    @Override
    public List<FoodItem> findCommonFoods() {
        return getBaseMapper().findCommonFoods();
    }

    @Override
    public FoodItem getById(String id) {
        return getBaseMapper().selectById(id);
    }
}