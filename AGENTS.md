# AGENTS.md

本仓库 OpenCode / Codex 智能体的工作指南。

## 项目简介

AI Agent 驱动的自适应语境化背单词系统（"TiMo"），面向考试备考。全栈：Vue3+Vite+Element Plus 前端，Spring Boot 3.4+JPA+MyBatis 后端，MySQL+Redis。

## 快速启动

```bash
# 后端（端口 8080）— 需要 MySQL 8（数据库：timo_words）+ Redis 运行
cd backend
./mvnw spring-boot:run
# 数据库凭据：环境变量 DB_USERNAME/DB_PASSWORD（默认 root/root）。Schema 通过 JPA ddl-auto: update 自动创建。
# 种子数据：backend/src/main/resources/import-words.sql

# 前端（端口 3000，代理 /api → localhost:8080）
cd frontend
npm install
npm run dev
```

## 测试

**前端**（vitest + happy-dom）：
```bash
cd frontend
npm test          # 单次运行
npm run test:watch
```
测试文件位于 `src/**/__tests__/` — API 契约测试、Pinia Store 测试、路由测试。

**后端**（JUnit 5，使用 H2 内存数据库 `test` 配置）：
```bash
cd backend
./mvnw test -Dspring.profiles.active=test
```
21 个测试文件，分布在 algorithm/、modules/、common/、infrastructure/。详见 `src/test/resources/application-test.yml`。

无 CI 工作流。

## 核心模式

- **双 ORM**：JPA 管理实体，MyBatis 处理复杂查询（两者共存）
- **FSRS+DF 算法**：`backend/src/main/java/com/timo/words/algorithm/` — 调度器、动态遗忘因子、评分映射、错误强化
- **三种学习模式**：快速记忆、语境深度学习、统一复习
- **TiMo 智能体教练**：DeepSeek API 通过 RestTemplate（JSON 模式）。熔断器：3 次失败 → 降级为本地规则
- **认证**：JWT 存储在 localStorage（键 `token`）。拦截器自动添加 `Bearer` 前缀。401 重定向到 `/login`。
- **事件**：mitt 事件总线（`src/events/`）驱动 TiMo 头像状态机、API 熔断检测、学习反馈
- **数据隔离**：`conversation_quiz_log` 不参与 FSRS。智能体标记 `conversation_mastered`（3 天过滤）不修改 S/D 值。
- **顽固词**：由错误规则自动标记；同模式连续 3 次复习得分 ≥ 3.5 时清除

## 架构

```
智能体教练（TiMo）— 编排器（规划、推荐、诊断、调度）
   ├── 快速记忆         ─┐
   ├── 语境深度学习     ─┤→ 共享 FSRS+DF 引擎
   ├── 统一复习         ─┘
   └── 备考规划（对话驱动）
```

## 前端约定

- `src/api/` 与后端模块 1:1 对应。所有请求通过 `request.js`，带 JWT 拦截器 + `/api` 前缀。
- Pinia Store：`user.js`、`study.js`、`examPlan.js`、`agent.js`
- 路由：`needAuth` meta 标志。登录 token 存储在 `localStorage.token`。
- `@` 别名 → `./src`（在 `vite.config.js` 中配置）
- Element Plus 使用中文区域设置（`zh-cn`）
- 桌面优先（1440px 基准，1024px 断点）

## 后端结构

```
com.timo.words
├── modules/   auth, user, word, study, examplan, review, agent, statistics, calendar
├── algorithm/ fsrs, df, scoring
├── common/    exception, response (Result), constants
├── config/    Security, Redis, CORS
└── infrastructure/  ai (DeepSeek), event
```

## 文档

中文文档位于 `文档/`：
- `文档/基于 AI Agent 与 FSRS 的自适应语境化背单词系统.txt` — 算法规范
- `文档/前端规划.txt` / `文档/后端规划.txt` — 完整接口列表
- `开发进度.md` — 阶段跟踪（1-9 阶段完成，第 10 阶段待完成）

## 智能体 TiMo 素材

`agent形象/` — 5 个 SVG 状态（idle、thinking、alert、success、offline）。由 `tiMoStore` + `TiMoAvatar.vue` 驱动。

## 单词数据

18,142 个单词，涵盖 8 种考试类型，来自 kajweb/dict，使用柯林斯星级 + BNC/COCA 词频（来自 ECDICT）增强。种子数据：`import-words.sql`。

## MCP

通过 `.mcp.json` 使用 Playwright 进行浏览器自动化。
