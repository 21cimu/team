package com.fitmind.module.exercise.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fitmind.module.exercise.entity.Exercise;
import com.fitmind.module.exercise.mapper.ExerciseMapper;
import com.fitmind.module.exercise.service.IExerciseService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExerciseServiceImpl extends ServiceImpl<ExerciseMapper, Exercise> implements IExerciseService {

    @Override
    public List<Exercise> searchExercises(String keyword, String category) {
        LambdaQueryWrapper<Exercise> wrapper = new LambdaQueryWrapper<>();

        if (category != null && !category.isEmpty() && !"ALL".equals(category)) {
            wrapper.eq(Exercise::getCategory, category);
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w
                    .like(Exercise::getName, keyword)
                    .or().like(Exercise::getTarget, keyword)
                    .or().like(Exercise::getDescription, keyword)
                    .or().like(Exercise::getPrimaryMuscle, keyword)
            );
        }

        wrapper.orderByAsc(Exercise::getSortOrder);
        return this.list(wrapper);
    }
}
