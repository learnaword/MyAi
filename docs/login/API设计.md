# 登录功能 — API 设计

> **需求来源：** [需求分析.md](./需求分析.md) · [技术方案.md](./技术方案.md)  
> **功能短名：** `login`  
> **产出路径：** `docs/login/API设计.md`  
> **状态：** DRAFT — 对外契约；原待确认已拍板并写入  
> **配套前端修改文档：** [docs/login_frontend.md](../login_frontend.md)  
> **风格：** 与现有 `/api/login` 一致——HTTP 状态码表意，body 为 JSON 资源/错误，**不用**统一 `code/msg` 包装

---

## 1. 设计原则

| 原则 | 说明 |
|------|------|
| 角色 | `USER` \| `ADMIN`；JWT claim `role` |
| 分流 | 登录/注册响应带 `role`，前端据此跳转；服务端再拦一层 |
| 强制登录 | `AUTH_ENABLED=true`：WS 仅 USER JWT；观测仅 ADMIN JWT（**无** Admin Token） |
| 密码 | ≥8 位，须同时含字母与数字 |
| 无 refresh | 过期后重新登录；改密后 `password_version`+1 强制旧 token 失效 |
| 登录限流 | 登录失败/尝试触发 `429 TOO_MANY_REQUESTS` |
| 错误体 | `{ "error": "<CODE>", "message": "<可读说明>" }` |

---

## 2. 鉴权总览

### 2.1 JWT

| 项 | 约定 |
|----|------|
| 请求头（HTTP） | `Authorization: Bearer <token>` |
| Claims | `sub` = username；`uid` = userId；`role` = `USER` \| `ADMIN`；`pv` = password_version；`iat` / `exp` |
| 存储（前端） | `localStorage`：`ia_token`、`ia_userId`、`ia_username`、`ia_role` |
| 默认有效期 | 建议 7 天（`app.jwt.expiration`） |

### 2.2 接口访问矩阵（`AUTH_ENABLED=true`）

| 接口 / 通道 | 匿名 | USER | ADMIN |
|-------------|------|------|-------|
| `POST /api/register` / `/api/login` / 找回公开接口 | ✅ | ✅ | ✅ |
| `POST /api/password/change` | ❌ | ✅ | ✅ |
| `POST /api/me/bind-email` | ❌ | ✅ | ✅ |
| `GET /api/me` | ❌ | ✅ | ✅ |
| `POST /api/admin/users` | ❌ | ❌ | ✅ |
| WebSocket `/ws` | ❌ | ✅ | ❌ |
| `/api/observability/**`（除 status 探活） | ❌* | ❌ | ✅ |

\* `/api/observability/status` 可保持「仅受 OBS_ENABLED 约束、可不带凭证」。

### 2.3 观测 API 鉴权（已拍板：完全改造）

旧：`X-Obs-Admin-Token`。  
新：**仅** `Authorization: Bearer <jwt>` 且 `role=ADMIN`（及 `pv` 有效）。**去除** `OBS_ADMIN_TOKEN` / `X-Obs-Admin-Token`。

| 情况 | HTTP | error |
|------|------|-------|
| 观测关闭 | 503 | `OBS_DISABLED` |
| 无/无效 Bearer | 401 | `UNAUTHORIZED` |
| USER JWT | 403 | `FORBIDDEN` |

> 观测路径与响应体见 [ai_full_link_analysis API](../ai_full_link_analysis/api_design_ai_full_link_analysis.md)；**列表与全部 stats 均支持可选 `userId`**。

---

## 3. REST API 一览

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/api/register` | 匿名 | 注册普通用户，返回 JWT（role=USER） |
| POST | `/api/login` | 匿名 | 登录，返回 JWT + role |
| POST | `/api/password/forgot/send-code` | 匿名 | 向邮箱发送重置验证码 |
| POST | `/api/password/forgot/reset` | 匿名 | 验证码 + 新密码重置 |
| POST | `/api/password/change` | Bearer | 已登录修改密码（成功后强制重登） |
| GET | `/api/me` | Bearer | 当前用户信息 |
| POST | `/api/me/bind-email` | Bearer | 历史无邮箱用户绑定邮箱 |
| POST | `/api/admin/users` | ADMIN Bearer | 创建另一个管理员 |

---

## 4. 接口明细

### 4.1 注册

`POST /api/register`

**请求**

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "pass1234"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 是 | trim 后非空；唯一；长度 1–64 |
| email | String | 是 | 合法邮箱；唯一 |
| password | String | 是 | ≥8，须含字母与数字 |

**行为**

- 固定创建 `role=USER`（忽略任何客户端传入的 role）
- 成功即签发 JWT，等同自动登录

**响应 `200`**（已拍板保持 200，与现网一致）

```json
{
  "token": "<jwt>",
  "userId": 1,
  "username": "alice",
  "email": "alice@example.com",
  "role": "USER"
}
```

**错误**

| HTTP | error | 典型 message |
|------|-------|----------------|
| 400 | `BAD_REQUEST` | 缺字段 / 密码不合规 / 邮箱格式非法 |
| 409 | `CONFLICT` | `username exists` / `email exists` |

---

### 4.2 登录

`POST /api/login`

**请求**

```json
{
  "username": "alice",
  "password": "pass1234"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 是 | |
| password | String | 是 | |

**响应 `200`**

```json
{
  "token": "<jwt>",
  "userId": 1,
  "username": "alice",
  "email": "alice@example.com",
  "role": "USER"
}
```

管理员示例：`"role": "ADMIN"`（无自助注册；来自种子账号）。

**错误（须区分）**

| HTTP | error | message（约定） |
|------|-------|-----------------|
| 404 | `USER_NOT_FOUND` | `user not found` |
| 401 | `BAD_CREDENTIALS` | `bad password` |
| 400 | `BAD_REQUEST` | 缺字段 |
| 429 | `TOO_MANY_REQUESTS` | 登录限流（已拍板） |

> 相对现网统一 `401 bad credentials` 的**破坏性变更**：前端需按 `error` 分提示。

**前端分流（契约约定，非服务端重定向）**

| role | 跳转 |
|------|------|
| `USER` | `/index.html` |
| `ADMIN` | `/obs.html` |

误入对方页面时前端**自动跳转**到上表落地页。

---

### 4.3 发送找回密码验证码

`POST /api/password/forgot/send-code`

**请求**

```json
{
  "email": "alice@example.com"
}
```

**响应 `200`**（邮箱存在且已发送）

```json
{
  "ok": true
}
```

| HTTP | error | 说明 |
|------|-------|------|
| 400 | `BAD_REQUEST` | 邮箱格式非法 |
| 404 | `EMAIL_NOT_FOUND` | **邮箱未注册（已拍板：不隐藏）** |
| 503 | `MAIL_NOT_CONFIGURED` | SMTP 未配置 |
| 429 | `TOO_MANY_REQUESTS` | 发送过于频繁 |

---

### 4.4 验证码重置密码

`POST /api/password/forgot/reset`

**请求**

```json
{
  "email": "alice@example.com",
  "code": "123456",
  "newPassword": "newpass12"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | String | 是 | |
| code | String | 是 | 邮件验证码 |
| newPassword | String | 是 | 同注册密码规则 |

**响应 `200`**

```json
{
  "ok": true
}
```

成功后 `password_version + 1`；前端清 token 并跳转登录。

**错误**

| HTTP | error |
|------|-------|
| 400 | `BAD_REQUEST`（密码不合规等） |
| 401 | `INVALID_CODE`（码错误/过期/已用） |

---

### 4.5 修改密码（已登录）

`POST /api/password/change`  
**Header:** `Authorization: Bearer <token>`

**请求**

```json
{
  "oldPassword": "pass1234",
  "newPassword": "newpass12"
}
```

**响应 `200`**

```json
{
  "ok": true
}
```

约定（已拍板）：服务端 `password_version + 1`；客户端**清除 token 并跳转登录页**（强制重新登录）。

**错误**

| HTTP | error |
|------|-------|
| 401 | `UNAUTHORIZED`（无/无效 token） |
| 401 | `BAD_CREDENTIALS`（旧密码错误） |
| 400 | `BAD_REQUEST`（新密码不合规） |

---

### 4.6 当前用户

`GET /api/me`  
**Header:** `Authorization: Bearer <token>`

**响应 `200`**

```json
{
  "userId": 1,
  "username": "alice",
  "email": "alice@example.com",
  "role": "USER",
  "emailBound": true
}
```

`emailBound=false` 时前端提示绑定邮箱（历史用户）。

---

### 4.7 绑定邮箱（历史用户）

`POST /api/me/bind-email`  
**Header:** `Authorization: Bearer <token>`

**请求**

```json
{
  "email": "alice@example.com"
}
```

**响应 `200`**

```json
{
  "userId": 1,
  "username": "alice",
  "email": "alice@example.com",
  "role": "USER",
  "emailBound": true
}
```

| HTTP | error |
|------|-------|
| 400 | `BAD_REQUEST` |
| 409 | `CONFLICT`（邮箱已被占用） |
| 401 | `UNAUTHORIZED` |

---

### 4.8 创建管理员（仅 ADMIN）

`POST /api/admin/users`  
**Header:** `Authorization: Bearer <admin-jwt>`

**请求**

```json
{
  "username": "ops2",
  "email": "ops2@example.com",
  "password": "pass1234"
}
```

**行为：** 固定创建 `role=ADMIN`（忽略客户端 role 字段）。

**响应 `200`**

```json
{
  "userId": 9,
  "username": "ops2",
  "email": "ops2@example.com",
  "role": "ADMIN"
}
```

不返回新管理员 token（由其自行登录）。

| HTTP | error |
|------|-------|
| 401 | `UNAUTHORIZED` |
| 403 | `FORBIDDEN`（非 ADMIN） |
| 409 | `CONFLICT` |
| 400 | `BAD_REQUEST` |

---

## 5. WebSocket 契约变更

### 5.1 连接 URL

```text
ws://{host}/ws?token={jwt}
wss://{host}/ws?token={jwt}
```

| 查询参数 | 类型 | 必填 | 说明 |
|----------|------|------|------|
| token | String | 是（`AUTH_ENABLED=true`） | 登录得到的 JWT |

`AUTH_ENABLED=false` 时：可保持现网无 token 行为（紧急联调）。

### 5.2 握手结果

| 情况 | 行为 | 前端提示 |
|------|------|----------|
| 缺 token / 无效 / 过期 | 握手失败或立即关闭 | **用户未登录** |
| `role=ADMIN` | 拒绝 | **管理员请使用观测界面** |
| `role=USER` | 接受；session attributes 存 `userId`/`username`/`role` | 正常 |

### 5.3 业务消息

现有入站 `type` **不变**：`chat` / `start_interview` / `answer` / `upload_questions` / `quit`。  
**禁止**客户端传 `userId` 覆盖身份；服务端只用握手 attributes。

出站消息类型不变；观测开启时可选带 `traceId`（既有约定）。  
业务 trace 创建时 **`userId` 取自 JWT**，不再写 `null`。

### 5.4 角色错误出站（若握手后仍需提示）

若实现选择「先连上再发 error 关闭」，约定：

```json
{
  "type": "error",
  "error": "用户未登录"
}
```

或管理员：

```json
{
  "type": "error",
  "error": "管理员请使用观测界面"
}
```

优先推荐**握手直接失败**，由前端 `onclose` 映射文案。

---

## 6. 观测 API 增量（鉴权 + userId）

### 6.1 鉴权（替换原「仅 Admin Token」描述）

见 §2.3。`obs.html` 主路径使用：

```http
Authorization: Bearer <admin-jwt>
```

### 6.2 Trace 列表增加 userId 筛选

`GET /api/observability/traces`

**新增查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 否 | 按 `ai_trace.user_id` 精确筛选 |

其余参数不变（`from`/`to`/`scene`/…）。  
单条 trace 响应中的 `userId` 字段：USER 产生的链路应为非 null。

统计类接口（`/stats/tokens`、`/stats/rag`、`/stats/tools`、`/stats/agents`）：MVP **均增加可选查询参数 `userId`**（已拍板）。

---

## 7. 静态页与门控（非 REST，但属对外行为）

| 路径 | 说明 |
|------|------|
| `/login.html` | 登录 |
| `/register.html` | 注册（USER） |
| `/forgot-password.html` | 找回 |
| `/change-password.html` | 改密（需 JWT） |
| `/index.html` | USER 面试界面 |
| `/obs.html` | ADMIN 观测界面 |

服务端可不强制静态资源鉴权，但 **API/WS 必须按角色拒绝**；前端按 `role` 自动跳转落地页。

---

## 8. 错误码汇总

| error | 含义 |
|-------|------|
| `BAD_REQUEST` | 参数/格式/密码规则 |
| `USER_NOT_FOUND` | 登录用户名不存在 |
| `BAD_CREDENTIALS` | 密码错误（登录或改密） |
| `CONFLICT` | 用户名/邮箱冲突 |
| `UNAUTHORIZED` | 未登录或 token 无效 |
| `FORBIDDEN` | 角色不允许 |
| `INVALID_CODE` | 重置验证码无效 |
| `MAIL_NOT_CONFIGURED` | 邮件未配置 |
| `TOO_MANY_REQUESTS` | 限流（登录/发码） |
| `EMAIL_NOT_FOUND` | 找回：邮箱未注册 |
| `OBS_DISABLED` | 观测关闭 |

---

## 9. 与旧契约兼容性

| 项 | 变更 |
|----|------|
| `POST /api/register` | 必填 `email`；响应 `email`/`role`；HTTP **200** |
| `POST /api/login` | 响应 `email`/`role`；拆分 `USER_NOT_FOUND`/`BAD_CREDENTIALS`；限流 429 |
| `/ws` | `?token=`；仅 USER |
| 观测 API | **仅** ADMIN JWT；**去除** Admin Token；traces + **全部 stats** 支持 `userId` |
| 新增 | `/api/me/bind-email`、`/api/admin/users` |

---

## 10. 已拍板结论（原待确认）

| 议题 | 结论 |
|------|------|
| 注册成功状态码 | **200** |
| stats 是否加 userId | **全部添加** |
| OBS_ADMIN_TOKEN | **去除** |
| 找回邮箱不存在 | **报错** `EMAIL_NOT_FOUND`（不统一 200） |

---

*Status: DRAFT — API 设计（待确认已关闭）。已输出前端修改文档：`docs/login_frontend.md`（须同步修订）。*
