# 功能：鉴权（JWT，可选）

<!-- AUTO-GENERATED: feature/auth -->

## 做什么

提供注册/登录与 JWT 校验；默认关闭，便于本地联调。

## 开关

```env
AUTH_ENABLED=false   # 默认
JWT_SECRET=...       # 开启鉴权时请更换
```

## 入口

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/register` | 注册，返回 token |
| POST | `/api/login` | 登录，返回 token |

请求体：`{ "username", "password" }`。

## 关键类

| 类 | 职责 |
|----|------|
| `auth/AuthController` | 注册登录 API |
| `auth/JwtService` | 签发/解析 JWT |
| `auth/JwtAuthFilter` | Bearer 校验 |
| `config/SecurityConfig` | 过滤器链；鉴权关则全放行 |
| `model/entity/UserEntity` | 用户表 |

## 开启鉴权时的行为

- 白名单：`/`、静态资源、`/api/register`、`/api/login`、`/health`、`/ws/**`
- 其他请求需 `Authorization: Bearer <token>`

## 持久化

表 `users`：`username`、`password_hash`（BCrypt）、`created_at`。

## 相关文档

- [记忆系统](./memory.md)
- 总览 [tech.md](../tech.md)
