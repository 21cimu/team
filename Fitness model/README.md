# 健身动作识别系统

基于 MediaPipe 和深度学习的健身动作识别系统，能够识别 22 种不同的健身动作。

## 📁 项目结构

```
Fitness model/
├── train_model.py                 # 模型训练脚本（核心）
├── evaluate_optimized.py          # 优化版评估（训练+Top-3准确率）
├── visualize_action_scores.py     # 视觉识别展示动作分数
├── README.md                      # 项目文档
├── .gitignore                     # Git 配置
├── fitness_videos/                # 健身视频数据集（22个类别）
├── processed_data/                # 预处理后的数据
│   ├── fitness_dataset.pkl        # 骨架序列数据（132维）
│   └── class_mapping.json         # 类别映射
└── trained_models/                # 训练好的模型
    └── best_model.pth             # 最佳模型权重
```

## 🎯 支持的动作类别（22类）

1. **力量训练**: BenchPress, PushUps, PullUps, BodyWeightSquats, Lunges, WallPushups
2. **有氧运动**: JumpRope, JumpingJack
3. **体操动作**: HandstandPushups, HandstandWalking, FloorGymnastics, ParallelBars, PommelHorse, StillRings, UnevenBars, TrampolineJumping
4. **其他运动**: CleanAndJerk, RockClimbingIndoor, RopeClimbing, HighJump, LongJump, PoleVault

## 📘 部署文档

如果你准备把模型部署到本机、服务器，或者对接 `Spring Boot` 项目，请查看：

- `DEPLOYMENT.md`

## 🚀 快速开始

### 1. 环境配置

```bash
conda create -n fitness_py310 python=3.10
conda activate fitness_py310
pip install mediapipe==0.10.14 opencv-python numpy torch torchvision scikit-learn tqdm pillow
```

如果电脑没有 GPU，推荐安装 CPU 版 PyTorch：

```bash
pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
```

如果你想直接使用本机 Conda 环境里的 Python，也可以执行：

```powershell
& "C:\Users\Lenovo\.conda\envs\fitness_py310\python.exe" -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
& "C:\Users\Lenovo\.conda\envs\fitness_py310\python.exe" -m pip install mediapipe==0.10.14 opencv-python numpy scikit-learn tqdm pillow
```

### 2. 训练模型 + 优化评估

```bash
python evaluate_optimized.py
```

这个脚本会：
1. **自动训练模型**（100 epochs，约 3-4 分钟）
2. **显示完整训练过程**（每个 epoch 的损失和准确率）
3. **自动评估模型**，包括：
   - 原始准确率 (Top-1)
   - 加权准确率
   - **Top-3 准确率**（通常能达到 70%+）
   - 模拟集成准确率
4. **训练结束自动生成实验表格**：
   - `trained_models/training_history_时间戳.csv`：每个 epoch 的训练/验证指标
   - `trained_models/experiment_summary_时间戳.md`：可直接贴到实验报告的结果表

### 3. 视觉识别展示动作分数

使用训练好的模型，对摄像头画面或本地视频进行实时识别，并在画面左上角展示动作分数。

运行前请确认环境中：
1. 已安装可用的 `torch`（CPU 版也可以）
2. 建议安装 `pillow`，用于中文文字渲染
3. 若缺少 `pillow` 或中文字体，脚本仍可运行，但中文显示会自动降级

摄像头实时识别：

```bash
python visualize_action_scores.py --source 0
```

识别本地视频：

```bash
python visualize_action_scores.py --source "test_video.mp4"
```

可选参数示例：

```bash
python visualize_action_scores.py --source 0 --top-k 3 --min-frames 20 --score-threshold 0.1
```

如果你想自己调大或调小界面字号，可以增加：

```bash
python visualize_action_scores.py --source 0 --ui-scale 0.9
python visualize_action_scores.py --source 0 --ui-scale 1.2
```

说明：
1. 需要先训练出 `trained_models/best_model.pth`
2. 按 `q` 可退出识别窗口
3. 默认显示 Top-3 动作分数，并以条形图形式展示，减少读数负担
4. 已支持中文动作名显示，建议安装 `pillow` 以确保中文文字正常渲染
5. 若本机缺少中文字体或 `pillow`，脚本仍可运行，但中文文字会自动降级
6. 当最高分低于 60 分时，会给出中文提示；超过 60 分时会提示“动作很标准”
7. 当前会优先保持原始视频帧内容，只调整预览窗口大小去适配屏幕
8. 当前界面已放大字号，本地视频会尽量按原帧率播放，处理跟不上时自动丢帧，避免慢镜头感
9. 提示信息已移动到底部中间，右上角面板更紧凑，减少对主体动作的遮挡

## 📊 当前性能

- **原始准确率 (Top-1)**: 55-60%
- **Top-3 准确率**: **74%+** ✅
- **目标准确率**: 70%（已达成）

### 💡 准确率说明

- **Top-1 准确率**: 模型预测的第1个类别必须完全正确
- **Top-3 准确率**: 正确类别在模型预测的前3个类别中即算对
- Top-3 在实际应用中更有意义，用户可以看前3个建议

## 🔧 下一步优化方向

1. **数据增强**: 添加旋转、缩放、噪声等增强策略
2. **模型改进**: 尝试 ST-GCN、Transformer 等更先进的架构
3. **特征工程**: 使用关节速度、加速度等衍生特征
4. **增加数据**: 收集更多训练样本

## 📝 技术栈

- **姿态估计**: MediaPipe Pose (33个关键点)
- **深度学习框架**: PyTorch
- **模型架构**: LSTM / ST-GCN
- **数据处理**: OpenCV, NumPy
- **视频数据集**: UCF101

## ⚠️ 注意事项

- 需要 Python 3.10 环境
- MediaPipe 版本推荐使用 0.10.14（兼容当前 `mp.solutions.pose` 写法）
- 首次运行需要下载 UCF101 数据集并筛选健身视频
- 训练时间约 2-3 小时（CPU）

## 📋 实验输出表格

模型训练完成后，会在终端打印结果表，并在 `trained_models/` 目录下保存两份文件：

| 文件 | 内容 |
| --- | --- |
| `training_history_时间戳.csv` | 每轮 epoch 的 `train_loss / train_top1 / train_top3 / val_loss / val_top1 / val_top3 / learning_rate` |
| `experiment_summary_时间戳.md` | 最佳轮数、验证集 Top-1/Top-3、测试集 Top-1/Top-3、测试 Loss 的汇总表 |

这样实验跑完后就有可复用的表格，方便直接截图、贴论文或写周报。


1.实验跑完要有表格
2.测试集比如b站视频
3.训练好的模型通过视觉识别展示动作分数
