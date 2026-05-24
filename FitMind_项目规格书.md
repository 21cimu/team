# FitMind 项目规格书

> 文档版本：v1.0  
> 生成日期：2026-05-11  
> 适用范围：`fitmind-web`、`fitmind-server`、`fitmind.sql` 当前仓库实现

## 1. 文档说明

本规格书基于当前项目源码、数据库脚本与前后端接口实现反向整理而成，目标是描述 **当前系统已经具备或已显式编码的能力**，而不是立项阶段的理想规划。

文档中的功能状态分为三类：

- 已实现：前后端和数据库已有明确实现或可运行闭环
- 部分实现：已有页面或接口，但业务闭环不完整、存在降级逻辑或演示态实现
- 规划中：仓库结构或历史文档提及，但当前代码未形成明确实现

## 2. 项目概述

### 2.1 项目名称

FitMind 智能健身教练平台

### 2.2 项目定位

FitMind 是一个面向个人健身用户的 Web 平台，核心目标是把以下能力整合到一个连续使用场景中：

- 用户账户与身体档案管理
- AI 训练计划与饮食计划生成
- 训练/饮食打卡与历史记录
- 数据看板与趋势展示
- 动作库与 3D 肌肉可视化
- 社区互动、排行榜、成就和通知
- 管理后台

### 2.3 目标用户

- 需要基础训练指导的普通健身用户
- 需要饮食与训练联动建议的减脂/增肌用户
- 需要查看用户、帖子和成就数据的管理员

### 2.4 角色定义

| 角色 | 标识 | 权限范围 |
| --- | --- | --- |
| 普通用户 | `USER` | 登录、维护身体档案、生成/编辑/打卡计划、使用社区与排行榜 |
| 管理员 | `ADMIN` | 拥有普通用户能力，同时可访问后台用户/帖子/成就管理接口 |

## 3. 系统架构

### 3.1 总体架构

系统采用前后端分离结构：

- 前端：`fitmind-web`
- 后端：`fitmind-server`
- 数据库：MySQL
- 缓存/预留：Redis 已在配置中声明，但当前核心业务代码对 Redis 依赖较弱
- AI 服务：DeepSeek 兼容接口

### 3.2 技术栈

#### 前端

- Vue 3
- Vite
- TypeScript
- Vue Router
- Pinia
- Element Plus
- Axios
- ECharts
- Three.js

#### 后端

- Java 17
- Spring Boot 3.2.3
- Spring Security
- MyBatis-Plus
- JWT
- RestTemplate

#### 数据层

- MySQL 8.0+
- Redis（配置已预留）

### 3.3 目录结构

```text
team/
├─ fitmind-web/               # 前端工程
│  ├─ src/api/                # 前端接口封装
│  ├─ src/views/              # 页面视图
│  ├─ src/router/             # 路由定义
│  ├─ src/stores/             # Pinia 状态
│  ├─ src/composables/        # 3D 逻辑封装
│  └─ public/assets/          # 3D 模型等静态资源
├─ fitmind-server/            # 后端工程
│  ├─ module/user             # 用户、档案
│  ├─ module/ai               # AI 对话与历史
│  ├─ module/training         # 训练计划
│  ├─ module/diet             # 饮食计划
│  ├─ module/dashboard        # 数据看板
│  ├─ module/exercise         # 动作库
│  ├─ module/food             # 食物识别与记录
│  ├─ module/community        # 社区
│  ├─ module/achievement      # 成就
│  ├─ module/notification     # 通知
│  └─ module/admin            # 管理后台
└─ fitmind.sql                # 数据库初始化脚本
```

## 4. 运行与部署规格

### 4.1 默认运行端口

- 前端开发端口：Vite 默认 `5173`
- 后端服务端口：`8080`

### 4.2 后端配置项

当前 `application.yml` / `application-dev.yml` 中定义了以下关键配置：

- MySQL：`jdbc:mysql://localhost:3306/fitmind`
- Redis：`localhost:6379`
- JWT 密钥：环境变量 `JWT_SECRET`
- JWT 有效期：默认 `86400000` 毫秒
- DeepSeek API 地址：默认 `https://api.deepseek.com/v1`
- DeepSeek API Key：环境变量 `AI_API_KEY`

### 4.3 数据初始化

`fitmind.sql` 会初始化以下内容：

- 数据库与表结构
- `admin`、`demo` 两个默认用户
- 成就基础数据
- 食物基础数据

## 5. 认证与接口规范

### 5.1 认证机制

- 采用 JWT 无状态认证
- 登录成功后返回 token
- 前端将 token 存入 `localStorage`
- 前端所有 `/app/**` 路由要求本地存在 token
- 后端除登录、注册外默认要求认证

### 5.2 放行接口

后端当前明确放行：

- `POST /api/user/login`
- `POST /api/user/register`

### 5.3 管理员鉴权

`/api/admin/**` 接口除要求登录外，还会在控制器内二次校验 `role` 是否为 `ADMIN`。

### 5.4 通用响应格式

```json
{
  "code": 200,
  "message": "Success",
  "data": {}
}
```

业务约定：

- `code = 200`：成功
- `code = 401`：未认证或 token 失效
- 其他：业务错误

## 6. 核心业务流程

### 6.1 新用户启用流程

1. 用户注册账号
2. 用户登录，后端返回 token 与 `profilePromptRequired`
3. 用户进入身体档案页填写身高、体重、年龄、体型、训练目标、伤病信息
4. 用户保存档案后，系统将 `profileCompleted` 置为 `true`
5. 用户可生成 AI 训练计划与 AI 饮食计划

### 6.2 训练闭环

1. 用户进入训练页
2. 选择 AI 自动生成计划，或手动创建计划
3. 用户查看当日计划，支持编辑动作内容
4. 用户完成训练后执行打卡
5. 系统写入通知，并在近期完成率达到条件时尝试生成动态调整建议
6. Dashboard、历史记录、成就等模块读取训练数据

### 6.3 饮食闭环

1. 用户进入饮食页
2. 选择 AI 自动生成饮食计划，或手动创建计划
3. 用户查看并编辑餐次内容
4. 用户完成饮食计划后打卡
5. 系统写入通知
6. Dashboard、历史记录、成就等模块读取饮食数据

### 6.4 社区闭环

1. 用户发布动态
2. 其他用户可点赞、评论、关注
3. 社区统计、热词、排行榜读取社区与训练/饮食数据
4. 成就系统会根据社区发帖量解锁部分成就

## 7. 功能规格

## 7.1 账户与身份模块

### 状态

已实现

### 前端页面

- `/login`
- `/register`

### 后端接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/user/login` | POST | 用户登录，返回 token 与档案提示标记 |
| `/api/user/register` | POST | 用户注册 |
| `/api/user/me` | GET | 获取当前用户信息 |
| `/api/user/update-profile` | PUT | 更新昵称、邮箱、手机号、头像 |
| `/api/user/change-password` | PUT | 修改密码 |

### 业务规则

- 用户名不可重复
- 密码使用 BCrypt 存储
- 注册用户默认角色为 `USER`
- 登录时会更新 `lastLoginTime`
- 若 3 天未登录、档案未完成或显式要求补档，会返回 `profilePromptRequired = true`

## 7.2 身体档案模块

### 状态

已实现

### 前端页面

- `/app/profile`

### 后端接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/profile/me` | GET | 获取当前用户身体档案 |
| `/api/profile/save` | POST | 创建或更新身体档案 |

### 采集字段

- 身高
- 体重
- 年龄
- 性别
- 体型
- 体脂率
- 主要健身目标
- 多训练目标标签
- 活动水平
- 是否有伤病
- 伤病部位

### 业务规则

- 每个用户只保留一份身体档案
- 保存后自动设置 `profileCompleted = true`
- 如果未勾选伤病，`injuryParts` 置空
- `fitnessGoal` 可由 `trainingGoals` 首项自动回填

## 7.3 AI 教练对话模块

### 状态

已实现

### 前端页面

- `/app/coach`

### 后端接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/ai/chat` | POST | 与 AI 教练对话 |
| `/api/ai/chat/history` | GET | 获取指定会话历史 |

### 功能说明

- 支持创建新会话
- 支持多轮上下文对话
- 会话消息会持久化到 `chat_message`
- 系统会将用户身体档案摘要注入到系统提示词中

### 实现细节

- 每次请求最多携带最近 20 条历史消息
- 模型使用 `deepseek-chat`
- 当 API Key 仍为占位值时，系统返回模拟回复

## 7.4 训练计划模块

### 状态

已实现

### 前端页面

- `/app/training`

### 后端接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/ai/generate/training` | POST | AI 生成今日训练计划 |
| `/api/ai/training/today` | GET | 获取今日训练计划 |
| `/api/training/plan` | POST | 手动创建今日训练计划 |
| `/api/training/plan/{id}` | PUT | 编辑训练计划 |
| `/api/ai/training/checkin/{planId}` | POST | 训练打卡 |

### 页面能力

- AI 一键生成训练计划
- 手动创建训练计划
- 编辑计划名称、目标肌群、预计时长和动作列表
- 支持三类动作：
  - 力量
  - 有氧
  - 柔韧
- 页面内可记录组数、重量、次数、时长、配速等执行数据
- 完成后可打卡

### 数据结构

训练计划主体保存于 `ai_training_plan`：

- `plan_name`
- `target_muscle_group`
- `estimated_duration`
- `status`
- `content`：JSON 格式动作明细

### AI 生成规格

AI 输出为 1 日训练 JSON，至少包含：

- `planName`
- `targetMuscleGroup`
- `estimatedDuration`
- `exercises[]`

动作项允许的字段包括：

- `name`
- `type`
- `sets`
- `reps`
- `restSeconds`
- `duration`
- `distance`
- `pace`
- `incline`
- `holdTime`
- `rounds`

### 业务规则

- 每个用户每天仅保留一份训练计划，新建会覆盖当日旧计划
- 打卡后状态置为已完成
- 打卡后发送训练完成通知
- 最近 7 天完成情况满足阈值时，系统会尝试生成“动态调整建议”

### 当前实现约束

- 页面中的组间记录、AI 干预日志、强度调节属于前端交互层，未完整持久化到后端
- “动态调整建议”目前主要表现为日志与通知，不直接自动改写未来计划

## 7.5 饮食计划模块

### 状态

已实现

### 前端页面

- `/app/diet`

### 后端接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/ai/generate/diet` | POST | AI 生成今日饮食计划 |
| `/api/ai/diet/today` | GET | 获取今日饮食计划 |
| `/api/diet/plan` | POST | 手动创建今日饮食计划 |
| `/api/diet/plan/{id}` | PUT | 编辑饮食计划 |
| `/api/ai/diet/checkin/{planId}` | POST | 饮食打卡 |

### 页面能力

- AI 一键生成饮食计划
- 手动配置总热量、蛋白质、碳水、脂肪
- 手动配置多餐次、多食物项
- 计划编辑与打卡
- 页面中内置基础营养知识与三类饮食建议卡片

### 数据结构

饮食计划保存于 `ai_diet_plan`：

- `total_calories`
- `protein`
- `carbs`
- `fat`
- `status`
- `content`：JSON 格式餐次明细

### AI 生成规格

AI 输出为 1 日饮食 JSON，至少包含：

- `totalCalories`
- `protein`
- `carbs`
- `fat`
- `meals[]`

### 业务规则

- 每个用户每天仅保留一份饮食计划
- 打卡后状态置为已完成
- 打卡后发送饮食完成通知

## 7.6 Dashboard 数据看板模块

### 状态

已实现

### 前端页面

- `/app/dashboard`

### 后端接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/dashboard/stats` | GET | 汇总统计 |
| `/api/dashboard/weekly-training` | GET | 周训练分钟数 |
| `/api/dashboard/heatmap` | GET | 训练/饮食热力图 |
| `/api/dashboard/nutrition-today` | GET | 今日营养概览 |
| `/api/dashboard/body-metrics-trend` | GET | 身体指标趋势 |

### 展示指标

- 连续活跃天数
- 本周训练频次
- 平均热量
- 计划完成率
- 周训练柱状图
- 身体指标趋势图
- 近 4 个月热力图
- 今日或最近一次营养摄入结构

### 当前实现约束

- 身体指标趋势图并非基于独立历史表，而是基于当前身体档案生成模拟趋势点
- 热力图按计划日期与完成状态聚合，不区分真实执行细节

## 7.7 动作库与 3D 肌肉图谱模块

### 状态

部分实现

### 前端页面

- `/app/exercise-atlas`

### 后端接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/exercise/list` | GET | 动作列表与搜索 |
| `/api/exercise/{id}` | GET | 动作详情 |

### 页面能力

- 加载 `body.glb` 3D 模型
- 支持旋转、缩放、平移
- 鼠标悬停肌肉区域显示部位说明
- 动作列表按肌群或关键词筛选
- 选中动作后高亮相关肌群
- 可跳转至训练计划页或 AI 教练页

### 动作数据来源

- 优先读取数据库 `exercise` 表
- 若数据库为空，则后端生成默认动作数据返回

### 当前实现约束

- 3D 可视化与动作推荐主要依赖关键词映射，不是医学级肌肉模型标注系统
- 动作与肌肉映射规则以静态配置和字符串匹配为主

## 7.8 食物识别与饮食记录模块

### 状态

部分实现

### 前端页面

- `/app/food-recognition`

### 后端接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/food/recognize` | POST | 图片识别食物 |
| `/api/food/search` | GET | 搜索食物库 |
| `/api/food/common` | GET | 获取常见食物 |
| `/api/food/{id}` | GET | 获取食物详情 |
| `/api/food/record` | POST | 添加饮食记录 |
| `/api/food/records` | GET | 获取个人饮食记录 |

### 页面能力

- 上传图片进行食物识别
- 展示热量、蛋白质、碳水、脂肪汇总
- 从常见食物库或搜索结果中手动加入记录
- 识别结果会驱动前端页面中的训练计划建议文案变化

### AI 识别规格

后端会向视觉模型发送图片和文本提示，要求只返回 JSON 数组，字段包括：

- `name`
- `nameEn`
- `calories`
- `protein`
- `carbs`
- `fat`
- `fiber`
- `servingSize`
- `confidence`

### 降级逻辑

- 视觉识别失败时，自动返回模拟识别结果
- 常见食物库为空时，后端返回内置模拟食物数据

### 当前实现约束

- 食物识别后的“训练计划调整”仅在前端页面本地展示，不会同步回训练计划表
- `food_record` 与 `ai_diet_plan` 当前是并行记录，没有自动汇总成真实饮食达成分析

## 7.9 历史记录模块

### 状态

已实现

### 前端页面

- `/app/history`

### 后端接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/history/training` | GET | 训练历史 |
| `/api/history/diet` | GET | 饮食历史 |

### 功能说明

- 查看当前用户训练计划历史
- 查看当前用户饮食计划历史
- 数据按日期倒序返回

## 7.10 社区互动模块

### 状态

已实现

### 前端页面

- `/app/community`

### 后端接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/community/post` | POST | 发布动态 |
| `/api/community/post/{postId}` | DELETE | 删除动态 |
| `/api/community/feed` | GET | 获取动态流 |
| `/api/community/like/{postId}` | POST | 点赞 |
| `/api/community/comment/{postId}` | POST | 评论 |
| `/api/community/comments/{postId}` | GET | 评论列表 |
| `/api/community/follow/{targetUserId}` | POST | 关注 |
| `/api/community/unfollow/{targetUserId}` | POST | 取关 |
| `/api/community/following` | GET | 我的关注列表 |
| `/api/community/trending` | GET | 热门话题 |
| `/api/community/stats` | GET | 社区侧边统计 |

### 页面能力

- 发布文本动态
- 删除本人动态
- 点赞
- 评论输入
- 关注/取关
- 展示关注列表
- 展示热门话题与社区统计

### 业务规则

- 不能关注自己
- 关注关系去重
- 帖子流支持分页
- 热门标签从最近 100 条帖子中提取 `#话题`

### 当前实现约束

- 点赞接口是累加逻辑，前端做了“本地切换 liked”效果，但后端没有“取消点赞”语义
- 评论区在前端有乐观更新，但当前页面未调用后端评论列表接口进行展开加载
- 社区统计中的 `postsToday` 实际取的是帖子总数，不是严格意义的“今日帖子数”

## 7.11 排行榜模块

### 状态

已实现

### 前端页面

- `/app/leaderboard`

### 后端接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/community/leaderboard` | GET | 获取排行榜 |

### 支持分类

- `training`
- `streak`
- `calories`
- `social`

### 当前实现约束

- `period` 参数当前未实际参与计算
- 排名波动值 `change` 为随机生成展示字段

## 7.12 成就模块

### 状态

已实现

### 前端页面

- `/app/achievements`

### 后端接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/achievement/list` | GET | 获取个人成就列表 |
| `/api/achievement/check` | POST | 主动触发成就检查 |

### 成就逻辑

系统按类别计算进度：

- `training`：依据已完成训练计划数量
- `nutrition`：依据饮食计划数量
- `social`：依据发帖数量

### 解锁行为

- 首次访问成就列表会触发检查
- 达成目标后会创建通知

### 当前实现约束

- 当前实现未严格覆盖所有初始化成就文案的实际判定逻辑，例如“点赞数”“关注数”等社交型描述，在代码中仍按发帖数近似计算

## 7.13 通知模块

### 状态

已实现

### 后端接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/notification/list` | GET | 获取通知列表 |
| `/api/notification/unread-count` | GET | 获取未读数量 |
| `/api/notification/read/{id}` | PUT | 标记单条已读 |
| `/api/notification/read-all` | PUT | 全部已读 |

### 通知触发来源

- 训练计划打卡完成
- 饮食计划打卡完成
- 成就解锁
- 训练动态调整建议

### 限制

- 当前通知列表最多返回最近 50 条

## 7.14 管理后台模块

### 状态

已实现

### 前端页面

- `/app/admin`

### 后端接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/admin/dashboard` | GET | 后台统计 |
| `/api/admin/users` | GET | 用户分页列表 |
| `/api/admin/users/{id}/status` | PUT | 启停用户 |
| `/api/admin/users/{id}/role` | PUT | 修改角色 |
| `/api/admin/users/{id}` | DELETE | 删除用户 |
| `/api/admin/posts` | GET | 帖子分页列表 |
| `/api/admin/posts/{id}` | DELETE | 删除帖子 |
| `/api/admin/achievements` | POST | 创建成就 |
| `/api/admin/achievements/{id}` | PUT | 修改成就 |
| `/api/admin/achievements/{id}` | DELETE | 删除成就 |

### 页面能力

- 查看总用户数、总帖子数、总成就数
- 搜索用户
- 启用/禁用用户
- 升降用户角色
- 删除普通用户
- 查看/删除帖子
- 创建/删除成就

### 当前实现约束

- 后台成就列表当前前端使用的是“个人成就接口”而非独立的成就管理列表接口，展示数据更偏用户视角而非全量配置视角

## 8. 数据模型规格

### 8.1 核心表

| 表名 | 用途 |
| --- | --- |
| `sys_user` | 用户基础信息、角色、状态、登录时间、补档标记 |
| `user_body_profile` | 身体档案与训练目标 |
| `ai_training_plan` | 训练计划主表 |
| `ai_diet_plan` | 饮食计划主表 |
| `chat_message` | AI 对话消息 |
| `food_item` | 食物基础库 |
| `food_record` | 用户食物记录 |
| `community_post` | 社区帖子 |
| `community_comment` | 帖子评论 |
| `user_follow` | 关注关系 |
| `achievement` | 成就定义 |
| `user_achievement` | 用户成就进度 |
| `notification` | 通知消息 |
| `exercise` | 动作库 |

### 8.2 关键字段约束

#### `sys_user`

- `username` 唯一
- `email` 唯一
- `role` 默认 `USER`
- `status`：`1` 正常，`0` 禁用

#### `user_body_profile`

- `user_id` 唯一，一人一档

#### `ai_training_plan`

- 按 `user_id + plan_date` 查询频繁
- `content` 为 JSON 文本
- `status`：`0` 待执行，`1` 已完成，`2` 部分完成（表结构支持，当前主流程主要使用 `0/1`）

#### `ai_diet_plan`

- 按 `user_id + plan_date` 查询频繁
- `content` 为 JSON 文本

#### `user_follow`

- `(follower_id, following_id)` 唯一

## 9. 非功能规格

### 9.1 安全性

- 密码加密存储
- JWT 鉴权
- 未登录接口受限
- 管理接口有角色校验

### 9.2 可维护性

- 前后端模块划分清晰
- 前端接口封装集中在 `src/api`
- 后端按业务域划分 `module/*`

### 9.3 可扩展性

- AI 服务调用已集中封装，后续可替换模型供应商
- 训练/饮食计划内容使用 JSON 字段，便于扩展动作/餐次结构
- 动作策略工厂、Redis 配置已预留更深扩展空间

### 9.4 用户体验

- 前端包含 Dashboard、社区、饮食、训练、3D 图谱等完整入口
- 大部分页面已适配移动端响应式布局
- 请求失败时前端有统一错误提示

## 10. 当前实现边界与风险

### 10.1 AI 依赖风险

- DeepSeek API Key 未配置时，聊天和计划生成将走模拟输出
- 视觉识别失败时返回模拟识别数据

### 10.2 数据真实性风险

- 体重/体脂趋势并非真实历史采样
- 排行榜涨跌值为展示随机值
- 社区“今日帖子”统计口径与字段名不完全一致

### 10.3 业务闭环不足

- 训练执行过程中的详细记录尚未入库
- 食物识别对训练计划的调整仅停留在前端演示层
- 成就规则与成就文案存在部分不一致
- 管理后台成就列表未完全采用管理员视角的数据源

### 10.4 权限与审计

- 当前后台以角色字段判定管理员，尚未见更细粒度权限模型
- 缺少系统级操作审计日志

## 11. 结论

从当前代码实现看，FitMind 已经不是单一演示页项目，而是一个具备完整前后端结构的智能健身平台原型，已经覆盖：

- 账户体系
- 身体档案
- AI 对话
- 训练与饮食计划
- 打卡与历史
- 数据看板
- 动作图谱
- 食物识别
- 社区
- 成就通知
- 管理后台

但项目当前仍带有明显的“原型产品 + 演示增强”特征，主要体现在以下方面：

- 部分数据为模拟或降级结果
- 某些业务页面已很完整，但后端持久化闭环尚未完全跟上
- 个别统计与规则实现仍偏近似值

如果后续要进入正式交付阶段，优先建议补齐以下三项：

1. 训练执行数据、饮食记录与 Dashboard 的真实闭环
2. 成就、排行榜、社区统计的精确定义与一致性修正
3. AI 生成、视觉识别、后台管理的生产级容错与权限控制
