# 模型部署文档

本文档用于说明 `Fitness model` 项目的部署方式，覆盖以下三类场景：

1. 本机直接运行动作识别
2. 作为 Python 推理服务对外提供接口
3. 对接 `Spring Boot` 项目

## 1. 项目概览

当前项目并不是一个“只加载 `.pth` 就能推理”的纯分类器，而是一整条推理链路，包含：

- `MediaPipe Pose` 提取人体 33 个关键点
- `train_model.py` 中的特征构造 `build_features()`
- `PyTorch` 时序分类模型 `ActionRecognitionModel`
- `visualize_action_scores.py` 中的后处理纠偏逻辑
- 视频可视化界面与中文说明框

因此部署时建议把“姿态提取 + 特征工程 + 模型推理 + 纠偏规则”作为一个整体来部署，而不是只单独搬运权重文件。

## 2. 推荐部署方式

### 方案 A：Python 本机部署

适用场景：

- 本地直接测试摄像头或视频
- 单机演示
- 桌面环境使用

优点：

- 改动最少
- 能完全保留当前识别效果和纠偏规则
- 能直接显示动作分数和可视化界面

### 方案 B：Spring Boot + Python 推理服务

适用场景：

- 前后端分离项目
- Web 系统上传视频识别
- Java 业务系统接入模型能力

优点：

- 最适合当前项目
- `Spring Boot` 负责业务流程、用户、文件上传、任务状态
- Python 负责模型推理，避免把 `MediaPipe` 和后处理逻辑全部重写成 Java

结论：

- 当前项目最推荐使用“`Spring Boot` 调 Python 服务”的方式部署

### 方案 C：导出 ONNX 后接入 Java

适用场景：

- 希望尽量减少 Python 运行时依赖
- 可以接受额外的 Java 重写工作

限制：

- 不仅要加载 ONNX 模型，还要在 Java 侧重写姿态提取、特征构造、类别映射和纠偏规则
- 开发成本明显高于方案 B

当前项目里已有 `export_onnx.py`，可以作为备用方案，但不建议作为首选方案。

## 3. 运行环境要求

推荐环境：

- 操作系统：Windows
- Python：3.10
- Conda 环境：`fitness_py310`
- 推荐解释器路径：`C:\Users\Lenovo\.conda\envs\fitness_py310\python.exe`

关键依赖：

- `torch`
- `torchvision`
- `mediapipe==0.10.14`
- `opencv-python`
- `numpy`
- `scikit-learn`
- `tqdm`
- `pillow`

说明：

- 当前代码使用 `mp.solutions.pose`
- 为了兼容现有实现，推荐使用 `mediapipe==0.10.14`
- 如果使用其他新版 `mediapipe`，可能会出现 `module 'mediapipe' has no attribute 'solutions'`

## 4. 本机部署步骤

### 4.1 创建环境

```bash
conda create -n fitness_py310 python=3.10
conda activate fitness_py310
```

### 4.2 安装依赖

CPU 环境推荐安装：

```bash
pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
pip install mediapipe==0.10.14 opencv-python numpy scikit-learn tqdm pillow
```

如果你希望明确使用指定解释器，也可以执行：

```powershell
& "C:\Users\Lenovo\.conda\envs\fitness_py310\python.exe" -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
& "C:\Users\Lenovo\.conda\envs\fitness_py310\python.exe" -m pip install mediapipe==0.10.14 opencv-python numpy scikit-learn tqdm pillow
```

### 4.3 检查关键文件

部署前请确认以下文件存在：

- `trained_models/best_model.pth`
- `processed_data/class_mapping.json`
- `visualize_action_scores.py`
- `train_model.py`

如果 `trained_models/best_model.pth` 不存在，可以先执行：

```bash
python evaluate_optimized.py
```

这个脚本会先训练，再输出真实评估结果。

### 4.4 启动本地识别

摄像头：

```bash
python visualize_action_scores.py --source 0
```

本地视频：

```bash
python visualize_action_scores.py --source "D:\Fitness model\你的视频.mp4"
```

可调参数示例：

```bash
python visualize_action_scores.py --source 0 --top-k 3 --min-frames 20 --score-threshold 0.1 --ui-scale 1.2
```

说明：

- 按 `q` 退出
- 当前显示逻辑会尽量让预览窗口适配屏幕
- 视频帧本身保持原始内容，不再主动修改推理输入含义

## 5. 作为 Python 推理服务部署

如果你准备接入 Web 系统，推荐把当前模型包装成一个独立 Python 服务。

推荐技术：

- `FastAPI`
- `uvicorn`

推荐接口：

- `GET /health`
  - 用于健康检查
- `POST /predict/video`
  - 上传视频文件，返回动作结果
- `POST /predict/frame`
  - 上传单帧图片或关键点序列，返回分类结果

推荐返回字段：

- `top1_label`
- `top1_label_zh`
- `top1_score`
- `topk`
- `is_standard`
- `hint`
- `processed_video_path`

推荐服务内部流程：

1. 服务启动时加载 `best_model.pth`
2. 读取 `class_mapping.json`
3. 收到视频后调用 `MediaPipe Pose`
4. 构建与训练一致的特征 `build_features()`
5. 调用模型推理
6. 执行纠偏逻辑
7. 返回 JSON 结果

注意：

- 推理服务启动时加载一次模型，不要每次请求重新加载
- 视频文件建议先落盘，再异步处理
- 较长视频建议走“提交任务 + 轮询结果”模式

## 6. Spring Boot 对接方案

## 架构建议

- 前端上传视频到 `Spring Boot`
- `Spring Boot` 保存文件并调用 Python 推理服务
- Python 返回动作识别结果
- `Spring Boot` 将结果转成自己的业务对象返回给前端

## 推荐职责划分

`Spring Boot` 负责：

- 登录鉴权
- 文件上传
- 任务管理
- 业务数据库
- 结果存储
- 对外 API

Python 服务负责：

- 模型加载
- 姿态提取
- 时序特征构造
- 动作分类
- 后处理纠偏
- 可视化结果生成

## 调用流程

1. 前端上传视频到 `Spring Boot`
2. `Spring Boot` 保存视频到本地目录或对象存储
3. `Spring Boot` 调用 Python 接口，例如 `/predict/video`
4. Python 返回动作类别、分数、提示信息
5. `Spring Boot` 保存结果并返回给前端

## Spring Boot 侧建议返回格式

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "label": "BenchPress",
    "labelZh": "卧推",
    "score": 0.87,
    "topk": [
      {"label": "BenchPress", "score": 0.87},
      {"label": "StillRings", "score": 0.08},
      {"label": "HighJump", "score": 0.03}
    ],
    "hint": "动作很标准"
  }
}
```

## 7. ONNX 部署方案

项目里已有导出脚本：

```bash
python export_onnx.py
```

导出后会生成：

- `trained_models/fitness_action_model.onnx`

适合场景：

- 只部署时序分类器
- Java 侧使用 `ONNX Runtime`

不适合直接忽略的部分：

- `MediaPipe` 姿态提取
- `build_features()` 特征工程
- 纠偏规则

所以如果走 ONNX 路线，需要同步考虑：

1. 是否继续保留 Python 做姿态提取
2. 是否在 Java 重写特征构造
3. 是否在 Java 重写纠偏逻辑

## 8. 生产部署建议

- 使用固定 Python 环境，不要混用系统 Python
- 通过绝对路径启动解释器
- 服务启动时预加载模型
- 为视频处理设置超时和文件大小限制
- 为上传目录和输出目录设置定时清理策略
- 保留 `class_mapping.json` 与模型权重的版本对应关系

如果部署成服务，建议目录结构如下：

```text
deploy/
├── app/
│   ├── trained_models/
│   │   └── best_model.pth
│   ├── processed_data/
│   │   └── class_mapping.json
│   ├── visualize_action_scores.py
│   ├── train_model.py
│   └── service.py
├── logs/
├── uploads/
└── outputs/
```

## 9. 常见问题

### 9.1 `ModuleNotFoundError: No module named 'torch'`

原因：

- 当前解释器不是 `fitness_py310`

解决：

```powershell
& "C:\Users\Lenovo\.conda\envs\fitness_py310\python.exe" -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
```

### 9.2 `ModuleNotFoundError: No module named 'cv2'`

解决：

```bash
pip install opencv-python
```

### 9.3 `module 'mediapipe' has no attribute 'solutions'`

原因：

- `mediapipe` 版本与当前代码不兼容

解决：

```bash
pip uninstall mediapipe -y
pip install mediapipe==0.10.14
```

### 9.4 Trae 终端运行失败或出现 `trae-sandbox`

建议：

- 不要依赖 Trae 内置终端做最终运行验证
- 直接在 `Anaconda Prompt` 或本机 PowerShell 中运行

### 9.5 模型文件不存在

请先确认：

- `trained_models/best_model.pth` 已生成

如果没有，请先训练：

```bash
python evaluate_optimized.py
```

## 10. 部署结论

对于当前项目，推荐按下面优先级部署：

1. 本地演示：直接运行 `visualize_action_scores.py`
2. 系统集成：`Spring Boot + Python 推理服务`
3. 备用方案：导出 `ONNX` 后在 Java 中加载模型

如果你的目标是接入现有 Java 项目，最推荐的落地方式仍然是：

- `Spring Boot` 管业务
- Python 管模型推理

