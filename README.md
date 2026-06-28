# Rookie

> 基于 Spring Boot 3 + Vue 3 的前后端分离后台管理基础框架。
> 围绕「用户 - 角色 - 菜单 - 字典 - 权限」体系展开，提供可复用的后台工作台与权限基础设施。

本项目为同仓库前后端并存工程：后端为 Maven 多模块，前端为独立 Vue 3 工程挂在 `rookie-ui/` 下。

---

## ✨ 功能特性

- **用户与角色**：用户管理、角色管理、用户-角色关联、角色默认项
- **菜单与权限**：菜单树管理、按钮级权限点、动态路由按菜单注册
- **字典系统**：字典 + 字典数据的统一管理，标签 / 下拉样式由数据项驱动，全枚举走字典映射
- **认证授权**：JWT 鉴权（`Token` 请求头，无 Bearer 前缀）、Spring Security 方法级权限
- **消息通知**：通知全生命周期管理、分组投递、我的通知、已读 / 确认
- **日志管理**：操作日志（@Log 切面采集，含设备 / IP / 耗时）、错误日志（多来源：请求 / 异步 / 定时等）、操作-错误日志关联跳转
- **基础设施**：统一返回结构、全局异常处理、PageHelper 分页、Redis 缓存、请求体可缓存包装

## 🧰 技术栈

**后端**

- Java 17 · Spring Boot 3.4.0 · Spring Security · Spring Data Redis
- MyBatis · PageHelper · MySQL · JWT · Hutool · Knife4j / OpenAPI

**前端**

- Vue 3 · TypeScript · Vite · Pinia · Vue Router · Element Plus
- @kangc/v-md-editor（Markdown 编辑 / 预览）· Axios

## 📦 仓库结构

```text
rookie/
├─ rookie-admin/       后端启动与接口暴露层（启动类 com.rookie.admin.RookieApplication）
├─ rookie-common/      公共基础能力：工具类、缓存、实体、统一返回、异常、分页、JWT
├─ rookie-framework/   框架层：Security 配置、鉴权衔接、AOP、日志切面、异步配置
├─ rookie-system/      系统业务：用户/角色/菜单/字典/通知/日志的 Service、Mapper、Controller
├─ rookie-ui/          Vue 3 前端工程（独立于 Maven 多模块）
├─ sql/                数据库脚本（单文件 rookie.sql：建表 + 关键数据初始化）
├─ pom.xml             Maven 聚合配置
└─ README.dev.md       面向开发者的详细文档（协作约定、模块边界、主题适配清单等）
```

> 各模块的职责边界、协作约定、前端主题适配清单等开发细节，见 [`README.dev.md`](./README.dev.md)；
> 前端工程的目录结构与实现约定见 [`rookie-ui/README.dev.md`](./rookie-ui/README.dev.md)。

## 🚀 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8+ · Redis
- Node.js 18+

### 后端

```bash
# 1. 初始化数据库（在 MySQL 中执行 sql/rookie.sql，含建库 + 14 张表 + 菜单/字典/角色等关键数据）
# 2. 按需修改 rookie-admin/src/main/resources/application.yml 的数据源与 Redis 配置
# 3. 编译并启动
./mvnw clean install -DskipTests
cd rookie-admin && ../mvnw spring-boot:run
# 默认监听 http://localhost:8080
```

> 注：前端开发联调时通过 Vite 代理把 `/api` 转发到 `http://localhost:8080`（见 `rookie-ui/vite.config.ts`）。

### 前端

```bash
cd rookie-ui
npm install
npm run dev          # 开发
npm run build        # 构建产物
npm run type-check   # 类型检查
```

## 🔐 鉴权约定

- 登录成功后由 `Token` 请求头携带 JWT（**无 Bearer 前缀**），后端 `TokenVerifyFilter` 直接读取该字段校验
- 接口鉴权使用 Spring Security `@PreAuthorize`，权限点与菜单 `perm_key` 对齐

## 📖 文档指引

| 文档 | 说明 |
| --- | --- |
| [`README.dev.md`](./README.dev.md) | 面向开发者的详细总览：模块边界、协作约定、主题适配清单 |
| [`rookie-ui/README.dev.md`](./rookie-ui/README.dev.md) | 前端工程结构与实现约定 |

## 📄 License

本项目为个人 / 团队内部学习与演进用途，暂未指定开源协议，使用前请联系维护者。