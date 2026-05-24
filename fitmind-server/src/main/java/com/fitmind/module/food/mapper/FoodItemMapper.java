package com.fitmind.module.food.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fitmind.module.food.entity.FoodItem;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FoodItemMapper extends BaseMapper<FoodItem> {
    List<FoodItem> searchByName(String keyword);
    List<FoodItem> findCommonFoods();
}