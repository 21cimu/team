package com.fitmind.module.exercise.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fitmind.module.exercise.entity.Exercise;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExerciseMapper extends BaseMapper<Exercise> {
}
