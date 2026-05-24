# FitMind

FitMind 是一个面向训练、饮食与 AI 教练协同场景的全栈健身平台。

仓库包含：

- `fitmind-web`: 前端应用，基于 Vue 3 + Vite
- `fitmind-server`: 后端服务，基于 Spring Boot 3
- `fitmind.sql`: 数据库初始化参考文件
- `prompts/`: AI 提示词相关资源

## 功能概览

- 训练计划生成、编辑与执行
- 饮食计划生成与餐次联动
- AI 教练对话与历史会话
- 食物识别与营养记录
- 身体档案、成就、排行榜、社区
- 3D 肌肉图谱与动作数据

## 技术栈

前端：

- Vue 3
- Vite
- TypeScript
- Pinia
- Vue Router
- Element Plus
- Three.js
- ECharts

后端：

- Java 17
- Spring Boot 3.2
- Spring Security
- MyBatis-Plus
- MySQL
- Redis
- JWT

## 目录结构

```text
.
├─ fitmind-web/                 # 前端
│  ├─ src/
│  ├─ public/
│  └─ package.json
├─ fitmind-server/              # 后端
│  ├─ src/main/java/
│  ├─ src/main/resources/
│  └─ pom.xml
├─ prompts/                     # 提示词资源
├─ fitmind.sql                  # 数据库脚本
└─ README.md
```

## 本地启动

### 1. 启动后端

要求：

- JDK 17
- Maven 3.9+
- MySQL
- Redis

进入后端目录：

```bash
cd fitmind-server
```

启动前需要准备以下环境变量：

```bash
AI_API_KEY=your_deepseek_key
QWEN_API_KEY=your_qwen_key
JWT_SECRET=your_jwt_secret
DB_PASSWORD=your_mysql_password
REDIS_PASSWORD=your_redis_password
```

可选环境变量：

```bash
AI_API_URL=https://api.deepseek.com/v1
QWEN_API_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
QWEN_VISION_MODEL=qwen3.6-plus
JWT_EXPIRATION=86400000
```

运行：

```bash
mvn spring-boot:run
```

默认端口：

- `http://localhost:8080`

### 2. 启动前端

要求：

- Node.js 20+ 推荐

进入前端目录：

```bash
cd fitmind-web
npm install
```

前端本地可使用 `.env.local` 配置，例如：

```env
VITE_AMAP_KEY=your_amap_web_key
VITE_AMAP_SECURITY_CODE=your_amap_security_code
```

启动开发环境：

```bash
npm run dev
```

构建：

```bash
npm run build
```

## 配置说明

后端配置文件：

- [fitmind-server/src/main/resources/application.yml](fitmind-server/src/main/resources/application.yml)
- [fitmind-server/src/main/resources/application-dev.yml](fitmind-server/src/main/resources/application-dev.yml)

前端请求入口：

- [fitmind-web/src/utils/request.ts](fitmind-web/src/utils/request.ts)

天气相关配置：

- [fitmind-web/src/api/weather.ts](fitmind-web/src/api/weather.ts)

## 数据与安全

本仓库已经移除了公开提交中可识别的 AI 明文 Key，但你在部署前仍应自行提供：

- `AI_API_KEY`
- `QWEN_API_KEY`
- `JWT_SECRET`
- `DB_PASSWORD`
- `REDIS_PASSWORD`

注意：

- 不要提交 `.env.local`、数据库密码、生产 JWT 密钥或任何第三方平台密钥
- 如果你曾在别处暴露过旧 key，建议在对应平台完成轮换
- 前端 `VITE_*` 变量会进入浏览器端，不应视为真正私密信息

## 发布建议

- 前端通过 Nginx 或静态托管服务部署
- 后端单独部署为 Spring Boot 服务
- 生产环境使用独立数据库、Redis 和环境变量管理
- 将前后端统一挂在反向代理后，通过 `/api` 转发到后端

## 备注

仓库中仍保留了一些项目资料与设计辅助文件，例如：

- `FitMind_Project_Plan.md`
- `FitMind_项目规格书.md`
- `3D肌肉.html`

它们不是运行时必需文件，但可作为产品与设计参考。
