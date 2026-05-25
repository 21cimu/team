# 实验结果汇总

- 生成时间: 2026-05-18 10:37:02
- 数据集: `./processed_data/fitness_dataset.pkl`
- 模型目录: `./trained_models`

## 结果表

| 指标 | 数值 |
| --- | --- |
| 最佳模型轮数 | 35 |
| 最佳验证 Top-1 | 71.00% |
| 最佳验证 Top-3 | 86.62% |
| 测试 Loss | 1.5128 |
| 测试 Top-1 | 68.44% |
| 测试 Top-3 | 81.75% |

## 训练配置

| 参数 | 数值 |
| --- | --- |
| batch_size | 48 |
| learning_rate | 0.0008 |
| num_epochs | 60 |
| hidden_size | 192 |
| num_layers | 2 |
| dropout | 0.35 |
| weight_decay | 0.0005 |
| label_smoothing | 0.05 |
| use_velocity | True |
| seed | 42 |
