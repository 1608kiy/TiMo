# TiMo 后台管理系统 — 实施计划

## 一、管理员角色体系

| 角色 | 标识 | 权限范围 | 说明 |
|------|------|---------|------|
| 超级管理员 | `SUPER_ADMIN` | 全部权限 | 唯一，账号 tangjunhua@timo.com |
| 普通管理员 | `ADMIN` | 受限管理权限 | 可被超级管理员创建，无系统配置/AI配置权限 |
| 普通用户 | `USER` | 学习功能 | 现有用户 |

**普通管理员限制**：不能修改 AI 配置、不能修改系统参数、不能删除其他管理员、不能模拟登录。

---

## 二、隐蔽登录入口

**组合方案：方案 B（预设邮箱）+ 方案 A（连续点击 + 密钥）**

### 方案 B — 日常使用
- 管理员用 `tangjunhua@timo.com` + 密码直接登录
- 后端检测到 `role=SUPER_ADMIN`，JWT 携带角色
- 前端路由守卫自动跳转 `/admin`

### 方案 A — 备用入口（应急用）
- 登录页 TiMo Logo 连续快速点击 5 次（3 秒内）
- 底部滑出「管理员密钥」输入框
- 输入密钥后，当前账号临时获得管理员入口（密钥存在数据库 system_config 表）
- 适用于：忘记管理员邮箱、或需要临时授权其他账号

---

## 三、数据库设计

### 3.1 users 表新增字段
```sql
ALTER TABLE users ADD COLUMN role VARCHAR(20) DEFAULT 'USER' NOT NULL;
-- 值: USER, ADMIN, SUPER_ADMIN
ALTER TABLE users ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL;
-- 值: ACTIVE, BANNED
ALTER TABLE users ADD COLUMN last_login_at DATETIME;
```

### 3.2 新表：ai_provider_config（AI 厂商配置）
```sql
CREATE TABLE ai_provider_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_name VARCHAR(50) NOT NULL,     -- deepseek / openai / qwen / zhipu
    display_name VARCHAR(100) NOT NULL,     -- 显示名称
    base_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(500) NOT NULL,
    model VARCHAR(100) NOT NULL,
    max_tokens INT DEFAULT 2048,
    temperature DOUBLE DEFAULT 0.7,
    is_active BOOLEAN DEFAULT FALSE,        -- 当前启用的厂商
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 3.3 新表：ai_call_log（AI 调用日志）
```sql
CREATE TABLE ai_call_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    provider VARCHAR(50),
    model VARCHAR(100),
    prompt_tokens INT,
    completion_tokens INT,
    total_tokens INT,
    response_time_ms INT,
    status VARCHAR(20),                     -- SUCCESS / FAILED / CIRCUIT_OPEN
    error_message VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 3.4 新表：system_config（系统配置）
```sql
CREATE TABLE system_config (
    config_key VARCHAR(100) PRIMARY KEY,
    config_value VARCHAR(500) NOT NULL,
    description VARCHAR(255),
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

**预设配置项：**
| config_key | 说明 |
|------------|------|
| admin_secret | 管理员密钥（方案 A 用） |
| fsrs_default_stability | FSRS 默认稳定性 |
| fsrs_default_difficulty | FSRS 默认难度 |
| cold_start_mu | 冷启动 μ 值 |
| cold_start_sigma | 冷启动 σ 值 |
| df_theta1 | λ_rt 阈值 |
| df_theta2 | λ_acc 阈值 |
| fatigue_threshold_minutes | 疲劳检测阈值（分钟） |
| circuit_breaker_threshold | 熔断失败次数 |
| circuit_breaker_reset_ms | 熔断恢复时间（毫秒） |

### 3.5 新表：word_import_batch（词库导入批次）
```sql
CREATE TABLE word_import_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    filename VARCHAR(255),
    total_count INT,
    success_count INT,
    fail_count INT,
    status VARCHAR(20),                     -- PROCESSING / COMPLETED / FAILED
    operator_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

---

## 四、后端模块设计

### 4.1 包结构
```
com.timo.words
├── modules/
│   └── admin/
│       ├── controller/
│       │   ├── AdminAuthController.java       # 管理员登录、密钥验证
│       │   ├── AdminDashboardController.java  # 总览数据
│       │   ├── AdminUserController.java       # 用户管理
│       │   ├── AdminWordController.java       # 词库管理 + 批量导入
│       │   ├── AdminAIController.java         # AI 配置 + 调用日志
│       │   ├── AdminStatsController.java      # 全局统计
│       │   ├── AdminSystemController.java     # 系统参数配置
│       │   └── AdminLogController.java        # 操作日志
│       ├── service/
│       │   ├── AdminDashboardService.java
│       │   ├── AdminUserService.java
│       │   ├── AdminWordService.java
│       │   ├── AdminAIService.java
│       │   ├── AdminStatsService.java
│       │   └── AdminSystemService.java
│       ├── entity/
│       │   ├── AiProviderConfig.java
│       │   ├── AiCallLog.java
│       │   ├── SystemConfig.java
│       │   └── WordImportBatch.java
│       ├── repository/
│       │   ├── AiProviderConfigRepository.java
│       │   ├── AiCallLogRepository.java
│       │   ├── SystemConfigRepository.java
│       │   └── WordImportBatchRepository.java
│       └── dto/
│           ├── AdminOverviewDTO.java
│           ├── UserManageDTO.java
│           ├── AIConfigDTO.java
│           └── ...
├── infrastructure/
│   └── security/
│       ├── AdminInterceptor.java              # 管理员鉴权拦截器
│       └── SuperAdminInterceptor.java         # 超级管理员鉴权
```

### 4.2 核心 API 接口

**管理员认证：**
| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/admin/auth/verify-secret` | 验证管理员密钥 | 公开（需已登录） |
| GET | `/api/admin/auth/me` | 获取当前管理员信息 | ADMIN+ |

**仪表盘总览：**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/dashboard/overview` | 总用户、活跃用户、总单词、今日学习次数、AI调用量 |
| GET | `/api/admin/dashboard/trend` | 近 7/30 天用户增长、学习趋势 |
| GET | `/api/admin/dashboard/realtime` | 在线用户数、当前学习中 |

**用户管理：**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/users` | 用户列表（分页、搜索、筛选角色/状态） |
| GET | `/api/admin/users/{id}` | 用户详情（学习数据、FSRS 状态） |
| PUT | `/api/admin/users/{id}/role` | 修改用户角色（仅超级管理员） |
| PUT | `/api/admin/users/{id}/status` | 封禁/解封用户 |
| POST | `/api/admin/users/{id}/impersonate` | 模拟登录（生成 5 分钟临时 token） |
| DELETE | `/api/admin/users/{id}` | 删除用户（仅超级管理员） |

**词库管理：**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/words` | 词库列表（分页、搜索、筛选考试类型） |
| POST | `/api/admin/words` | 新增单词 |
| PUT | `/api/admin/words/{id}` | 编辑单词 |
| DELETE | `/api/admin/words/{id}` | 删除单词 |
| POST | `/api/admin/words/import` | 批量导入（CSV/Excel 上传） |
| GET | `/api/admin/words/import/{batchId}` | 查询导入进度 |
| GET | `/api/admin/words/export` | 导出词库 |

**AI 配置管理：**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/ai/providers` | 获取所有 AI 厂商配置 |
| POST | `/api/admin/ai/providers` | 新增厂商配置 |
| PUT | `/api/admin/ai/providers/{id}` | 修改厂商配置 |
| PUT | `/api/admin/ai/providers/{id}/activate` | 启用该厂商（自动禁用其他） |
| DELETE | `/api/admin/ai/providers/{id}` | 删除厂商配置 |
| GET | `/api/admin/ai/logs` | AI 调用日志（分页、筛选状态/日期） |
| GET | `/api/admin/ai/stats` | AI 调用统计（日均调用量、token 消耗、成功率） |
| POST | `/api/admin/ai/reset-circuit` | 手动重置熔断器 |

**全局统计：**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/stats/learning` | 全局学习统计（平均正确率、模式分布） |
| GET | `/api/admin/stats/retention` | 全局遗忘曲线聚合 |
| GET | `/api/admin/stats/reaction-time` | 反应时分布 |
| GET | `/api/admin/stats/weak-words` | 全局高频错词 Top 50 |

**系统配置：**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/system/config` | 获取所有系统配置 |
| PUT | `/api/admin/system/config` | 批量更新配置 |
| GET | `/api/admin/system/config/{key}` | 获取单个配置 |
| PUT | `/api/admin/system/config/{key}` | 更新单个配置 |

### 4.3 关键改造

**DeepSeekClient 改造：**
- 改为从数据库读取当前启用的 AI 厂商配置
- 每次请求前检查配置是否有更新（或用 Redis 缓存，5 分钟刷新）
- 调用完成后写入 ai_call_log 表
- 支持多厂商：统一接口，不同 provider 用不同 base_url/model

**JwtUtil 改造：**
- `generate()` 方法增加 `role` 参数
- `parse()` 方法提取 `role`
- 增加 `targetUserId` 字段（模拟登录用）

**SecurityConfig 改造：**
- 新增拦截器：`/api/admin/**` 路径检查 role 为 ADMIN 或 SUPER_ADMIN
- 部分接口（AI 配置、系统配置、角色修改）额外检查 SUPER_ADMIN

---

## 五、前端页面设计

### 5.1 路由结构
```
/admin                          → AdminDashboard（总览）
/admin/users                    → AdminUsers（用户管理）
/admin/users/:id                → AdminUserDetail（用户详情）
/admin/words                    → AdminWords（词库管理）
/admin/words/import             → AdminWordImport（批量导入）
/admin/ai                       → AdminAI（AI 配置 + 日志）
/admin/stats                    → AdminStats（全局统计）
/admin/settings                 → AdminSettings（系统配置）
/admin/logs                     → AdminLogs（操作日志）
```

### 5.2 页面功能明细

**AdminDashboard — 系统总览（答辩重点展示页）**
- 四个核心指标卡片：总用户数 / 今日活跃 / 总单词数 / 今日 AI 调用
- 用户增长趋势折线图（近 7 天 / 30 天）
- 今日学习次数趋势图
- 各学习模式使用占比饼图
- 实时在线用户数
- 系统健康状态（AI 服务状态、MySQL/Redis 连接状态）

**AdminUsers — 用户管理**
- 用户列表表格（头像、昵称、邮箱、角色、状态、注册时间、最后登录）
- 搜索框（按昵称/邮箱搜索）
- 筛选（角色、状态、注册时间范围）
- 操作按钮：查看详情、修改角色、封禁/解封、模拟登录、删除
- 用户详情抽屉：学习统计、FSRS 状态、学习历史

**AdminWords — 词库管理**
- 单词列表表格（单词、音标、词性、考试类型、柯林斯星级、词频）
- 搜索框 + 考试类型筛选
- 操作：编辑、删除、查看详情
- 顶部工具栏：新增单词、批量导入、导出
- 批量导入页面：上传 CSV/Excel → 预览 → 确认导入 → 显示进度

**AdminAI — AI 配置管理**
- 左侧：厂商配置卡片列表（DeepSeek、OpenAI、通义千问、智谱）
- 每个卡片显示：厂商名、模型、Base URL、状态（启用/未启用）
- 操作：编辑配置、启用/禁用、删除
- 右侧：AI 调用日志表格（时间、用户、模型、token、耗时、状态）
- 底部：AI 调用统计图表（日均调用量、token 消耗趋势、成功率）
- 熔断器状态显示 + 手动重置按钮

**AdminStats — 全局统计**
- 全局平均正确率
- 三种学习模式使用分布
- 遗忘曲线聚合图（全局）
- 反应时分布直方图
- 高频错词 Top 50 排行
- 考试类型分布饼图

**AdminSettings — 系统配置**
- 分组展示：FSRS 参数组、冷启动参数组、DF 系数组、疲劳检测组、熔断器组
- 每个配置项：名称 + 说明 + 当前值 + 编辑按钮
- 保存后立即生效（不用重启）
- 超级管理员专属页面

**AdminLogs — 操作日志**
- 管理员操作记录（谁在什么时间做了什么）
- 筛选：操作类型、管理员、时间范围

### 5.3 布局设计

**AdminLayout.vue：**
```
┌─────────────────────────────────────────────┐
│  顶部栏：TiMo 后台管理    [返回用户端] [退出] │
├──────────┬──────────────────────────────────┤
│  侧边栏   │         内容区                   │
│          │                                  │
│  总览     │                                  │
│  用户管理  │                                  │
│  词库管理  │                                  │
│  AI 配置  │                                  │
│  全局统计  │                                  │
│  系统配置  │                                  │
│  操作日志  │                                  │
│          │                                  │
├──────────┴──────────────────────────────────┤
│  底部：当前管理员信息                         │
└─────────────────────────────────────────────┘
```

- 侧边栏宽度 220px，深色背景（#1a1a2e 或类似）
- 内容区白色背景，padding 24px
- 顶部栏高度 56px，显示当前页面标题
- 右上角「返回用户端」链接，点击切换到普通用户 Dashboard

---

## 六、实施步骤（按顺序）

### 第一阶段：后端基础（1-2 天）
1. users 表加 role/status/last_login_at 字段
2. 创建 4 张新表（ai_provider_config、ai_call_log、system_config、word_import_batch）
3. 初始化 system_config 预设数据
4. 创建管理员账号 tangjunhua@timo.com（role=SUPER_ADMIN）
5. JwtUtil 改造（支持 role + targetUserId）
6. SecurityConfig 添加管理员拦截器
7. AdminAuthController（登录验证 + 密钥验证）

### 第二阶段：后端核心模块（2-3 天）
8. AdminDashboardService + Controller（总览数据）
9. AdminUserService + Controller（用户 CRUD + 封禁 + 模拟登录）
10. AdminWordService + Controller（词库 CRUD + 批量导入）
11. AdminAIService + Controller（AI 厂商配置 + 调用日志 + 统计）
12. AdminSystemService + Controller（系统参数 CRUD）
13. DeepSeekClient 改造（从数据库读配置 + 多厂商支持 + 调用日志）

### 第三阶段：前端骨架（1 天）
14. AdminLayout.vue（侧边栏 + 顶部栏 + 内容区）
15. 路由配置 /admin/*
16. 路由守卫（role 判断 + 自动跳转）
17. Login.vue 隐藏入口（连续点击 + 密钥框）

### 第四阶段：前端页面（3-4 天）
18. AdminDashboard.vue（指标卡片 + 趋势图 + 饼图）
19. AdminUsers.vue（表格 + 搜索 + 筛选 + 操作）
20. AdminUserDetail.vue（用户详情抽屉）
21. AdminWords.vue（表格 + 搜索 + 编辑 + 删除）
22. AdminWordImport.vue（文件上传 + 预览 + 进度）
23. AdminAI.vue（厂商配置 + 调用日志 + 统计图表）
24. AdminStats.vue（全局统计图表）
25. AdminSettings.vue（系统参数配置表单）
26. AdminLogs.vue（操作日志表格）

### 第五阶段：联调与打磨（1-2 天）
27. 全链路联调（管理员登录 → 后台操作 → 数据验证）
28. 权限测试（普通用户无法访问 /admin、普通管理员无超级权限）
29. 边界处理（空数据、加载状态、错误提示）
30. 前端样式统一

---

## 七、总页面数

| 类型 | 页面数 |
|------|--------|
| 后台管理页面 | 9 个（Dashboard + Users + UserDetail + Words + WordImport + AI + Stats + Settings + Logs） |
| 布局组件 | 1 个（AdminLayout） |
| 登录页改造 | 1 个（Login.vue 增加隐藏入口） |
| 后端模块 | 1 个 admin 模块（8 个 Controller + 6 个 Service + 4 个 Entity + 4 个 Repository） |

---

## 八、已确认细节

1. **管理员密钥**：`16608117290hj@HJ`
2. **操作日志**：登录、角色修改、封禁/解封、AI 配置修改、系统配置修改、批量导入、模拟登录（共 7 类）
3. **模拟登录**：5 分钟有效期 + 管理员主动退出时立即失效
4. **批量导入**：CSV + Excel (.xlsx) 都支持
5. **模拟视角横条**：橙色固定横条 "正在以管理员身份模拟查看用户 XXX 的数据 | [返回后台]"
