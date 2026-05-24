package com.fitmind.module.exercise.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.module.exercise.data.AceExerciseCatalog;
import com.fitmind.module.exercise.entity.Exercise;
import com.fitmind.module.exercise.service.IExerciseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 动作库数据初始化器。
 * <p>
 * 策略：以 AceExerciseCatalog 中的 id 为准，检测数据库中是否已有与 ACE id 集合匹配的数据。
 * - 若数据库为空，直接导入全量 ACE 数据
 * - 若数据库中存在非 ACE 来源的旧数据（id 不在 ACE id 集合内），清空后重新导入
 * - 若数据库已有 ACE 数据，跳过（幂等）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExerciseDataInitializer implements ApplicationRunner {

    private final IExerciseService exerciseService;

    @Override
    public void run(ApplicationArguments args) {
        List<Exercise> aceExercises = AceExerciseCatalog.defaults();
        long dbCount = exerciseService.count();

        if (dbCount == 0) {
            // 数据库为空，直接导入
            log.info("[ExerciseDataInitializer] 动作库为空，开始导入 ACE 动作库（共 {} 条）...", aceExercises.size());
            exerciseService.saveBatch(aceExercises);
            log.info("[ExerciseDataInitializer] ACE 动作库导入完成");
            return;
        }

        // 检测是否存在非 ACE 来源的旧数据
        // ACE id 范围：ace-exercises.json 中的 id 均为正整数，自增 id 从 1 开始
        // 判断策略：取数据库中最小 id，如果不在 ACE id 集合中，说明是旧数据
        boolean hasNonAceData = detectNonAceData(aceExercises);
        if (hasNonAceData) {
            log.warn("[ExerciseDataInitializer] 检测到数据库中存在非 ACE 来源的旧动作数据，将清空并重新导入 ACE 动作库...");
            // 物理删除全部旧数据（exercise 表无逻辑删除字段）
            exerciseService.remove(new LambdaQueryWrapper<>());
            exerciseService.saveBatch(aceExercises);
            log.info("[ExerciseDataInitializer] ACE 动作库重新导入完成（共 {} 条）", aceExercises.size());
        } else {
            log.info("[ExerciseDataInitializer] 动作库已有 {} 条数据，与 ACE 数据一致，跳过导入", dbCount);
        }
    }

    /**
     * 检测数据库中是否存在非 ACE 来源的旧数据。
     * 逻辑：从数据库中随机取最多 5 条记录，检查其 id 是否全部在 ACE id 集合中。
     * 若有任何一条不在 ACE id 集合中，则认为存在旧数据。
     */
    private boolean detectNonAceData(List<Exercise> aceExercises) {
        // 构建 ACE id 集合
        java.util.Set<Long> aceIds = new java.util.HashSet<>();
        for (Exercise e : aceExercises) {
            if (e.getId() != null) {
                aceIds.add(e.getId());
            }
        }

        // 取数据库前 10 条记录做抽样检测
        List<Exercise> sample = exerciseService.list(
                new LambdaQueryWrapper<Exercise>().last("LIMIT 10")
        );

        for (Exercise dbExercise : sample) {
            if (dbExercise.getId() != null && !aceIds.contains(dbExercise.getId())) {
                log.warn("[ExerciseDataInitializer] 发现非 ACE 数据：id={}, name={}", dbExercise.getId(), dbExercise.getName());
                return true;
            }
        }
        return false;
    }
}
