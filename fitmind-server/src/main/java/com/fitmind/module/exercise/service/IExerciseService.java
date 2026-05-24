package com.fitmind.module.exercise.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fitmind.module.exercise.entity.Exercise;

import java.util.List;

public interface IExerciseService extends IService<Exercise> {
    List<Exercise> searchExercises(String keyword, String category);
}
