# knowhub Docker 部署指南

在虚拟机里构建镜像 → 导出 tar → 传到服务器 → 加载运行。

## 一、前置：本地把三类产物准备好

镜像不在容器内编译，直接拷本地已构建好的产物，所以构建前先确认产物齐了：

```bash
# 后端 fat-jar（Maven 多模块，根目录执行）
mvn clean package -DskipTests
ls rookie-admin/target/rookie-admin-*.jar   # 应看到 rookie-admin-1.0.5.jar

# 前台 dist
cd knowhub-ui && npm run build && cd ..      # 产物在 knowhub-ui/dist/
ls knowhub-ui/dist/index.html

# 后台 dist
cd rookie-ui && npm run build && cd ..       # 产物在 rookie-ui/dist/
ls rookie-ui/dist/index.html
```

> Windows 本地构建用 `mvnw.cmd` / `npm.cmd`，Linux 虚拟机用 `./mvnw` / `npm`。

## 二、在虚拟机里构建镜像

把整个项目目录（含 `Dockerfile` / `docker-compose.yml` / `knowhub-ui` / `rookie-ui` / `rookie-admin/target` / `sql` / `deploy`）拷进虚拟机，然后：

```bash
cd <项目根>
cp .env.example .env          # 按需改密码/凭据
docker compose build          # 只构建镜像，不启动
```

构建完得到三个镜像：

| 镜像 | 内容 |
|---|---|
| `knowhub/backend:latest`  | 后端 fat-jar（JRE 运行） |
| `knowhub/portal:latest`   | 前台 dist（nginx 托管） |
| `knowhub/admin:latest`    | 后台 dist（nginx 托管） |

其余服务（mysql/redis）用的是公共镜像，构建阶段不碰，`docker compose up` 时才拉。OSS（RustFS）已在外部独立运行，本 compose 不含 OSS 服务。

## 三、导出镜像 tar

```bash
docker save -o knowhub-backend.tar  knowhub/backend:latest
docker save -o knowhub-portal.tar   knowhub/portal:latest
docker save -o knowhub-admin.tar    knowhub/admin:latest
```

> 也可以一次导多个：`docker save -o knowhub.tar knowhub/backend:latest knowhub/portal:latest knowhub/admin:latest`

## 四、服务器上加载 + 运行

把三个 tar（或合并的 knowhub.tar）连同 `docker-compose.yml`、`sql/`、`deploy/`、`.env` 传到服务器。**镜像文件不用传源码，只需 compose 编排文件 + SQL + nginx 配置**。

```bash
# 1. 加载镜像
docker load -i knowhub-backend.tar
docker load -i knowhub-portal.tar
docker load -i knowhub-admin.tar
docker images | grep knowhub      # 确认三个镜像就位

# 2. 启动（首次会拉 mysql/redis 公共镜像 + 灌库；OSS 已在外部就绪，本 compose 不含 OSS 服务）
docker compose up -d
```

服务器上**不需要 `docker compose build`**——`docker-compose.yml` 里每个 `build:` 段配了对应 `image:` 名，
`docker compose up` 发现镜像已存在就直接用，不会再构建。

## 五、访问

| 入口 | 地址 |
|---|---|
| 前台 | `http://<服务器IP>:8081` |
| 后台 | `http://<服务器IP>:8082` |
| 后端 Swagger | `http://<服务器IP>:8080/swagger-ui.html` |

## 六、文件访问模式切换（中转 / 直链）

OSS（RustFS）已在外部独立部署并运行。后台「系统设置」里改 `knowhub.file.access_mode`，**运行时生效无需重启**：

- `transfer`（默认，中转）：前端→后端 `/file/proxy`→后端用 `STORAGE_ENDPOINT` 拉外部 OSS 字节流回写。
  只要 backend 容器能访问到 `STORAGE_ENDPOINT` 即可，**开箱即用**。
- `direct`（直链）：前端浏览器直连 `direct_base_url` 访问外部 OSS。切到本模式后，把
  `knowhub.file.direct_base_url` 改成**浏览器能访问到外部 RustFS**的地址，即你在路由器内网给 RustFS 配的地址
  （如 `http://<RustFS内网IP>:9000`）。backend 仍用 `STORAGE_ENDPOINT` 签预签名，再把 host 改写成 `direct_base_url` 发前端。

> `access_mode` 与 `direct_base_url` 都是系统设置（存 `sys_config` 表，SysConfigUtil 只读 Redis/DB 不读环境变量），
> 不是环境变量，`.env` 改不了，只能在后台改。初始值 `http://100.82.86.85:9000` 是开发占位，部署后必须改。

## 七、生产收紧建议

`docker-compose.yml` 里 mysql / redis / backend 的 `ports` 对外映射是调试方便，生产建议去掉这三个，只留 portal / admin 对外，后端走内网被 nginx 反代。

## 文件清单

| 文件 | 作用 |
|---|---|
| `Dockerfile` | 后端镜像（拷 fat-jar，JRE 运行） |
| `knowhub-ui/Dockerfile` + `nginx-portal.conf` | 前台镜像 |
| `rookie-ui/Dockerfile` + `nginx-admin.conf` | 后台镜像 |
| `sql/00-init.sh` | MySQL 首启按依赖序灌 37 个脚本 |
| `docker-compose.yml` | 五服务编排（mysql/redis/backend/portal/admin；OSS 外部已就绪） |
| `.env.example` | 环境变量样例 |
| `.dockerignore` / `knowhub-ui/.dockerignore` / `rookie-ui/.dockerignore` | 构建上下文瘦身 |
