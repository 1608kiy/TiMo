# AGENTS.md

本文件是针对未来 AI 智能体（如 OpenCode / Codex）的高信噪比工作指南。仅包含那些不看代码极易猜错的核心约定和执行命令。

## 🎯 项目概述

自适应语境化背单词系统（"TiMo"）。
- **全栈架构**：Vue 3 + Vite (前端) / Spring Boot 3.4 + Java 21 (后端)
- **数据库**：MySQL 8 (主数据) + Redis (缓存/分布式锁)
- **状态**：**所有十个开发阶段均已完全结束**，全链路闭环已打通并覆盖了单元测试。请勿被任何历史文档中的“第10阶段待完成”误导。

## 🚀 启动与运行

### 后端 (端口 8080)
前置条件：必须有 MySQL 8 和 Redis 运行。
```bash
cd backend
./mvnw spring-boot:run
```
- **数据库账密**：需配置环境变量 `DB_USERNAME` 和 `DB_PASSWORD`（默认均为 `root`）。数据库需提前创建 `timo_words`。
- **自动建表**：依赖 Spring Data JPA 的 `ddl-auto: update`，不要手写建表 SQL。
- **初始化数据**：使用 `backend/src/main/resources/import-words.sql`。

### 前端 (端口 3000)
```bash
cd frontend
npm install
npm run dev
```
- **API 代理**：Vite 已配置，所有发向 `/api` 的请求均代理到 `localhost:8080`。

## 🧪 核心测试命令

不要假设标准的测试命令能直接工作，请**严格使用以下命令**：

- **后端测试 (JUnit 5 + H2)**:
  ```bash
  cd backend
  ./mvnw test -Dspring.profiles.active=test
  ```
  **极易踩坑**：如果不加 `-Dspring.profiles.active=test`，后端测试将尝试连接本地 MySQL 并可能因库/表不存在而导致测试大面积失败。`test` profile 会激活 H2 内存数据库配置。

- **前端测试 (Vitest + happy-dom)**:
  ```bash
  cd frontend
  npm test
  ```
  测试文件存放在 `src/**/__tests__/`。

## 🏗️ 架构与编码规范

### 后端规范 (Java 21)
1. **Lombok 优先**：项目中已广泛使用 `@Data`、`@Getter`、`@Setter`。**绝不要**手写 Getter/Setter/构造器。
2. **纯 JPA 驱动**：虽然 `pom.xml` 中保留了 `mybatis-spring-boot-starter`，但**所有现有查询均由 Spring Data JPA 实现**。除非必要，否则优先使用 JPA Repository 开发新查询，不要去创建 MyBatis XML Mappers。
3. **统一响应**：所有的 Controller 返回体必须包装在 `com.timo.words.common.response.Result` 中。

### 前端规范 (Vue 3)
1. **网络请求**：所有的后端请求都应当封装在 `src/api/` 中，并使用现有的 `request.js` Axios 实例（该实例已自动处理 `/api` 前缀和 JWT 拦截注入）。
2. **状态管理**：使用 Pinia (`src/stores/`)，`token` 固化存在 `localStorage.token` 中。
3. **事件总线**：跨组件（特别是全局组件如 TiMo 头像对话）状态使用 `mitt` 事件总线 (`src/events/`) 进行通信，而非乱用 Prop 钻取。
4. **组件语法**：统一使用 `<script setup>` 语法和组合式 API。

### 特殊业务逻辑边界
- **FSRS + DF 算法**：集中于 `backend/src/main/java/com/timo/words/algorithm/`，不要轻易修改其中的数学公式和 Clamp 限值。
- **成绩隔离**：普通的背单词记录通过 `quiz_records` 走 FSRS 算法计算遗忘曲线；但是 Agent 对话期间的数据记录在 `conversation_quiz_log`，**此部分成绩刻意隔离，不影响主干 FSRS 参数**（详见架构设计）。
- **DeepSeek API 熔断**：服务拥有熔断机制（3 次失败自动断开）。若需测试对话模块但在无网络环境，系统会自动走“降级本地规则”，无须过度惊解。

## 📚 更多参考
更完整的领域模型、算法公式详情和 API 接口文档，请优先参阅仓库根目录下的 `CLAUDE.md` 及 `文档/` 目录。