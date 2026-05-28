# TiMo - AI自适应背单词系统

<p align="center">
    <strong>🧠 基于FSRS算法的AI驱动自适应词汇学习系统</strong>
</p>

<p align="center">
    <img src="https://img.shields.io/badge/java-21-ED8B00.svg?logo=openjdk&logoColor=white" alt="Java">
    <img src="https://img.shields.io/badge/vue-3-4FC08D.svg?logo=vue.js&logoColor=white" alt="Vue">
    <img src="https://img.shields.io/badge/spring_boot-3.4-6DB33F.svg?logo=spring&logoColor=white" alt="Spring Boot">
    <img src="https://img.shields.io/badge/mysql-8.0-4479A1.svg?logo=mysql&logoColor=white" alt="MySQL">
    <img src="https://img.shields.io/badge/redis-cache-DC382D.svg?logo=redis&logoColor=white" alt="Redis">
</p>

---

## 📖 项目简介

TiMo 是一个 **AI Agent驱动的自适应语境化背单词系统**，专为考试备考设计。

核心创新：用 **FSRS（自由间隔重复调度）+ 动态遗忘因子** 替代传统的"认识/不认识"二元反馈，通过多维行为数据（反应时间、正确率历史、提示使用、拼写表现）自动调整复习策略。

## ✨ 核心特性

### 🧠 智能学习算法

- **FSRS算法** — 基于记忆科学的间隔重复调度
- **动态遗忘因子** — 根据行为数据实时调整
- **多维度评估** — 反应时间 + 正确率 + 提示使用 + 拼写
- **自适应难度** — 自动识别薄弱词，增加复习频率

### 🤖 AI Agent "TiMo"

- **智能问答** — 解答单词相关问题
- **语境生成** — 自动生成例句和语境
- **考试辅导** — 针对考试重点讲解
- **JSON模式** — 结构化输出

### 📚 三种学习模式

| 模式 | 说明 |
|------|------|
| **学习模式** | 学习新单词 |
| **复习模式** | 基于FSRS调度复习 |
| **测试模式** | 检验掌握程度 |

### 📊 数据分析

- **学习日历** — 每日学习热力图
- **掌握度统计** — 词汇掌握分布
- **遗忘曲线** — 记忆保持率可视化
- **错词本** — 薄弱词汇集中管理

## 🏗️ 技术架构

```
┌─────────────────────────────────────────┐
│           Vue3 + Element Plus           │
│    (ECharts / Pinia / Axios / SCSS)     │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────┴──────────────────────┐
│         Spring Boot 3.4 + JPA           │
│    (Spring Security / JWT / Redis)      │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────┴──────────────────────┐
│       MySQL 8.0 (主数据) + Redis (缓存)  │
└─────────────────────────────────────────┘
                   │
┌──────────────────┴──────────────────────┐
│         DeepSeek API (AI Agent)         │
└─────────────────────────────────────────┘
```

## 🚀 快速开始

### 环境要求

- Java 21+
- Maven
- MySQL 8.0
- Redis
- Node.js 18+

### 后端

```bash
cd backend

# 创建数据库
mysql -u root -p -e "CREATE DATABASE timo_words CHARACTER SET utf8mb4;"

# 配置环境变量
export DB_USERNAME=root
export DB_PASSWORD=root

# 运行
./mvnw spring-boot:run

# 测试（使用H2内存数据库）
./mvnw test -Dspring.profiles.active=test
```

### 前端

```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:3000
```

## 📁 项目结构

```
TiMo/
├── backend/
│   ├── src/main/java/com/timo/words/
│   │   ├── algorithm/        # FSRS算法实现
│   │   ├── controller/       # REST API
│   │   ├── entity/           # JPA实体
│   │   ├── repository/       # 数据访问
│   │   ├── service/          # 业务逻辑
│   │   └── security/         # JWT认证
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── views/            # 页面组件
│   │   ├── components/       # 通用组件
│   │   ├── stores/           # Pinia状态
│   │   └── api/              # API封装
│   └── package.json
└── AGENTS.md                 # AI Agent开发指南
```

## 🧪 开发阶段

| 阶段 | 内容 | 状态 |
|------|------|------|
| 1-5 | 基础架构 + 认证 + 词库 + FSRS | ✅ |
| 6-8 | 三种学习模式 + 考试计划 | ✅ |
| 9-10 | AI Agent + 数据统计 + 集成 | ✅ |
| 11 | 管理后台 | ✅ |

## 📄 License

MIT License
