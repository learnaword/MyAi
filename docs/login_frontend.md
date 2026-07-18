# 登录 / 角色分流 — 前端修改文档

## 涉及页面

- `static/login.html`（新建）— 登录
- `static/register.html`（新建）— 注册
- `static/forgot-password.html`（新建）— 找回密码
- `static/change-password.html`（新建）— 修改密码
- `static/index.html` — 面试界面（USER；误入 ADMIN 自动跳 obs）
- `static/obs.html` — 观测界面（ADMIN；误入 USER 自动跳 index；**去掉 Admin Token 输入**）
- `static/js/auth.js`（新建）— token / role / 跳转 / 限流错误处理
- `static/js/app.js` — WS 带 token（仅 USER）
- `static/js/obs.js` — 仅 `Authorization: Bearer` ADMIN JWT

完整契约见 [docs/login/API设计.md](./login/API设计.md)。

## API

| 方法 | 路径 / WS | 说明 |
|------|-----------|------|
| POST | `/api/register` | 注册 USER，`200` + token + role |
| POST | `/api/login` | 登录；按 role 分流；429 限流 |
| POST | `/api/password/forgot/send-code` | 发码；邮箱不存在 → `EMAIL_NOT_FOUND` |
| POST | `/api/password/forgot/reset` | 验证码重置 |
| POST | `/api/password/change` | 改密后强制重登 |
| GET | `/api/me` | 含 `emailBound` |
| POST | `/api/me/bind-email` | 历史用户绑邮箱 |
| POST | `/api/admin/users` | ADMIN 创建管理员 |
| WS | `/ws?token=` | 仅 USER |
| GET | `/api/observability/**` | 仅 `Authorization: Bearer <admin-jwt>` |

**登录请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 是 | 用户名 |
| password | String | 是 | 密码 |

**登录响应：**

```json
{
  "token": "<jwt>",
  "userId": 1,
  "username": "alice",
  "email": "alice@example.com",
  "role": "USER"
}
```

**错误示例：** `{ "error": "USER_NOT_FOUND", "message": "user not found" }`

## 前端修改点

### 1. 按钮/弹窗/表格等

```html
<!-- login.html -->
<form id="loginForm">
  <input id="username" autocomplete="username" required />
  <input id="password" type="password" autocomplete="current-password" required />
  <button type="submit" id="btnLogin">登录</button>
  <p id="loginError" role="alert"></p>
  <a href="/register.html">注册</a>
  <a href="/forgot-password.html">找回密码</a>
</form>

<!-- index.html：emailBound===false 时提示 -->
<div id="bindEmailBanner" hidden>
  请绑定邮箱后使用找回密码
  <input id="bindEmail" type="email" />
  <button type="button" id="btnBindEmail">绑定</button>
</div>
<span id="currentUser"></span>
<button type="button" id="btnLogout">登出</button>
```

### 2. data 数据

```js
const AUTH_KEYS = {
  token: 'ia_token',
  userId: 'ia_userId',
  username: 'ia_username',
  role: 'ia_role'
};

function saveAuth(res) { /* localStorage 写入 */ }
function clearAuth() { /* 清除 */ }
function getToken() { return localStorage.getItem(AUTH_KEYS.token) || ''; }
function getRole() { return localStorage.getItem(AUTH_KEYS.role) || ''; }
```

### 3. 方法

```js
function redirectByRole(role) {
  location.replace(role === 'ADMIN' ? '/obs.html' : '/index.html');
}

/** 误入自动跳转（已拍板） */
function guardPage(expectedRole) {
  if (!getToken()) {
    location.replace('/login.html');
    return false;
  }
  if (getRole() !== expectedRole) {
    redirectByRole(getRole());
    return false;
  }
  return true;
}

async function handleLogin(username, password) {
  const res = await fetch('/api/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  const body = await res.json().catch(() => ({}));
  if (res.status === 429) throw new Error('尝试过于频繁，请稍后再试');
  if (body.error === 'USER_NOT_FOUND') throw new Error('用户不存在');
  if (body.error === 'BAD_CREDENTIALS') throw new Error('密码错误');
  if (!res.ok) throw new Error(body.message || '登录失败');
  saveAuth(body);
  redirectByRole(body.role);
}

function connectWs() {
  const token = getToken();
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  return new WebSocket(
    `${proto}://${location.host}/ws?token=${encodeURIComponent(token)}`
  );
}

/** obs.js：仅 Bearer，无 Admin Token 输入框 */
function obsHeaders() {
  return {
    Accept: 'application/json',
    Authorization: 'Bearer ' + getToken()
  };
}

async function changePassword(oldPassword, newPassword) {
  const res = await fetch('/api/password/change', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer ' + getToken()
    },
    body: JSON.stringify({ oldPassword, newPassword })
  });
  if (!res.ok) throw new Error('改密失败');
  clearAuth();
  location.replace('/login.html'); // 强制重新登录
}

function logout() {
  clearAuth();
  location.replace('/login.html');
}
```

## 注意事项

- 观测页**删除** `X-Obs-Admin-Token` 输入与存储。
- 找回：`EMAIL_NOT_FOUND` 需明确提示「邮箱未注册」。
- 改密成功必须清 token 并回登录页。
- `emailBound === false` 时展示绑定引导。
- ADMIN 创建管理员可用观测页简单表单调 `POST /api/admin/users`（可选 MVP UI）。
- 勿把 JWT 提交进 git。
