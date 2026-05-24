package com.fitmind.module.exercise.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.module.exercise.entity.Exercise;
import com.fitmind.module.exercise.mapper.ExerciseMapper;
import com.fitmind.module.exercise.service.impl.ExerciseServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExerciseServiceImplTest {

    @Mock
    private ExerciseMapper exerciseMapper;

    @InjectMocks
    private ExerciseServiceImpl exerciseService;

    private List<Exercise> createSampleExercises() {
        List<Exercise> exercises = new ArrayList<>();
        Exercise e1 = new Exercise();
        e1.setId(1L);
        e1.setName("杠铃深蹲");
        e1.setCategory("LEGS");
        e1.setTarget("股四头肌 / 臀大肌");
        e1.setSortOrder(1);
        exercises.add(e1);

        Exercise e2 = new Exercise();
        e2.setId(2L);
        e2.setName("杠铃卧推");
        e2.setCategory("CHEST");
        e2.setTarget("胸部 / 三头肌");
        e2.setSortOrder(2);
        exercises.add(e2);

        Exercise e3 = new Exercise();
        e3.setId(3L);
        e3.setName("平板支撑");
        e3.setCategory("CORE");
        e3.setTarget("核心");
        e3.setSortOrder(3);
        exercises.add(e3);

        return exercises;
    }

    @Test
    void searchExercises_shouldReturnAllWhenNoFilter() {
        when(exerciseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(createSampleExercises());

        List<Exercise> result = exerciseService.searchExercises(null, null);

        assertNotNull(result);
        assertEquals(3, result.size());
    }

    @Test
    void searchExercises_shouldFilterByCategory() {
        List<Exercise> legExercises = createSampleExercises().stream()
                .filter(e -> "LEGS".equals(e.getCategory()))
                .toList();
        when(exerciseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(legExercises);

        List<Exercise> result = exerciseService.searchExercises(null, "LEGS");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("LEGS", result.get(0).getCategory());
    }

    @Test
    void searchExercises_shouldFilterByKeyword() {
        when(exerciseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(
                createSampleExercises().stream()
                        .filter(e -> e.getName().contains("深蹲"))
                        .toList()
        );

        List<Exercise> result = exerciseService.searchExercises("深蹲", null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("杠铃深蹲", result.get(0).getName());
    }

    @Test
    void searchExercises_shouldIgnoreAllCategory() {
        when(exerciseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(createSampleExercises());

        exerciseService.searchExercises(null, "ALL");

        verify(exerciseMapper).selectList(argThat(wrapper -> true));
    }

    @Test
    void searchExercises_shouldReturnEmptyListWhenNoMatch() {
        when(exerciseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());

        List<Exercise> result = exerciseService.searchExercises("不存在", null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
