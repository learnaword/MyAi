# 功能：鉴权（JWT + 角色）

<!-- feature/auth — 与 docs/login/ 设计对齐；实现后以代码为准 -->

## 做什么

提供注册/登录、JWT、**USER / ADMIN 角色分流**；默认强制鉴权。

- **USER**：登录进入面试界面；WebSocket `/ws?token=`  
- **ADMIN**：登录进入观测界面；观测 API 仅 `Authorization: Bearer`（**已去除** Admin Token）  
- 管理员：环境预置 + ADMIN 可创建另一个 ADMIN  

完整契约：[docs/login/API设计.md](../login/API设计.md)  
前端修改：[docs/login_frontend.md](../login_frontend.md)

## 开关

```env
AUTH_ENABLED=true
JWT_SECRET=...
# OBS_ADMIN_TOKEN 已去除，勿再依赖
```

## 入口

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/register` | 注册 USER |
| POST | `/api/login` | 登录（限流） |
| POST | `/api/password/forgot/send-code` | 发码（邮箱不存在报错） |
| POST | `/api/password/forgot/reset` | 重置 |
| POST | `/api/password/change` | 改密后强制重登 |
| GET | `/api/me` | 当前用户 |
| POST | `/api/me/bind-email` | 绑定邮箱 |
| POST | `/api/admin/users` | 创建管理员 |

## 开启鉴权时的行为

- `/ws`：须 token 且 **role=USER**
- `/api/observability/**`：须 **ADMIN JWT**
- 误入页面：前端自动跳转本角色落地页

## 相关文档

- [login 需求分析](../login/需求分析.md)
- [WebSocket 与前端](./websocket-frontend.md)
- [tech.md](../tech.md)
