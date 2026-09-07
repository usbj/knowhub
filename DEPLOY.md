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
| `ghcr.io/usbj/knowhub/backend:latest`  | 后端 fat-jar（JRE 运行） |
| `ghcr.io/usbj/knowhub/portal:latest`   | 前台 dist（nginx 托管） |
| `ghcr.io/usbj/knowhub/admin:latest`    | 后台 dist（nginx 托管） |

其余服务（mysql/redis）用的是公共镜像，构建阶段不碰，`docker compose up` 时才拉。OSS（RustFS）已在外部独立运行，本 compose 不含 OSS 服务。

## 三、导出镜像 tar

```bash
docker save -o knowhub-backend.tar  ghcr.io/usbj/knowhub/backend:latest
docker save -o knowhub-portal.tar   ghcr.io/usbj/knowhub/portal:latest
docker save -o knowhub-admin.tar    ghcr.io/usbj/knowhub/admin:latest
```

> 也可以一次导多个：`docker save -o knowhub.tar ghcr.io/usbj/knowhub/backend:latest ghcr.io/usbj/knowhub/portal:latest ghcr.io/usbj/knowhub/admin:latest`

## 四、服务器上加载 + 运行

把三个 tar（或合并的 knowhub.tar）连同 `docker-compose.yml`、`sql/`、`application.prod.yml`、`deploy/`、`.env` 传到服务器。**镜像文件不用传源码，只需 compose 编排文件 + SQL + 生产配置 + nginx 配置**。

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
| 前台 | `http://<服务器IP>:5070` |
| 后台 | `http://<服务器IP>:5077` |
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

## 八、自动 CI/CD（GitHub Actions → GHCR → Tailscale → UGREEN NAS）

当前自动发布以 GitHub 仓库 `usbj/knowhub` 为主仓库，Gitee 保留为代码镜像。只有 Pull Request **合并到 `develop`** 才触发 `.github/workflows/deploy-develop.yml`；直接 push 到 `develop` 不会自动部署。

合并前的 `.github/workflows/ci-develop.yml` 会对目标为 `develop` 的 PR 执行同一套 Java 和双前端静态构建检查。GitHub 仓库还需要开启 `develop` 分支保护：禁止直接 push/force-push 和删除分支，要求通过 Pull Request，并把 `validate` 设为必需状态检查；如果需要人工复核，再增加至少一名审核人。

流水线顺序如下：

1. 检出合并后的 Commit。
2. 使用 Java 17 构建并打包后端（当前阶段使用 `-DskipTests`，因为现有测试依赖外部 MySQL、Redis、RustFS）；分别对 `knowhub-ui`、`rookie-ui` 执行 `npm ci`、类型检查和生产构建。
3. 构建后端、前台、后台三个多架构 Docker 镜像，推送到 GHCR；每个镜像同时带合并 Commit 标签和 `latest` 标签。
4. GitHub Actions 通过 Tailscale 临时加入 Tailnet，并先检查 NAS 的 Tailscale 地址可达；随后通过 SSH 将 `deploy/deploy-compose.sh` 发送到 NAS。NAS 登录 GHCR 后按 Commit 标签执行 `docker compose pull` 和 `docker compose up -d --no-build`。
5. 检查前台公开 API、前台和后台入口；检查失败时保留当前 Compose 配置并尝试恢复上一个本地镜像版本。

### NAS 首次配置

请在 NAS 上完成以下一次性准备：

- 安装并确认 Docker Engine 与 Docker Compose v2 可用；部署目录中放置 `docker-compose.yml`、`sql/`、三个 nginx 配置文件和生产 `.env`。
- 建立专用部署用户，允许该用户执行 Docker；启用 SSH，并将 GitHub Actions 使用的公钥加入该用户的 `~/.ssh/authorized_keys`。
- NAS 安装并登录 Tailscale，并给 NAS 添加 `tag:nas`；GitHub Actions 使用 Tailscale OAuth Client 创建带 `tag:github-actions` 的临时节点。Tailscale Policy 只允许该标签访问 `tag:nas` 的 TCP 22 端口。
- 当前 workflow 使用普通 SSH over Tailscale，并通过 `NAS_SSH_KEY` 登录；请关闭 NAS 上的 Tailscale SSH 功能，保留 NAS 普通 SSH 服务，并允许专用部署用户使用 SSH 公钥登录。若要保留 Tailscale SSH，则需要另行改造 workflow，不能继续直接复用当前普通 SSH 私钥流程。
- 不要直接暴露 NAS 管理面板或 SSH 到公网；`NAS_HOST` 使用 NAS 的 Tailscale IP 或 MagicDNS 名称。
- 确认 NAS 的 CPU 架构。流水线已发布 `linux/amd64` 和 `linux/arm64` 镜像；若设备是其他架构，需要调整 workflow 的 `platforms`。
- 生产 `.env` 必须保留数据库、Redis、RustFS 等真实配置；流水线会在其中维护 `KNOWHUB_IMAGE_TAG`，镜像前缀已固定为 `ghcr.io/usbj/knowhub`，不要把 `.env` 提交到仓库。

### GitHub Actions Secrets

在 GitHub 仓库的 Settings → Secrets and variables → Actions 中设置：

| Secret | 内容 |
|---|---|
| `TS_OAUTH_CLIENT_ID` | Tailscale OAuth Client ID；仅授予 `auth_keys` 写权限并绑定 `tag:github-actions` |
| `TS_OAUTH_SECRET` | Tailscale OAuth Client Secret |
| `NAS_HOST` | NAS 的 Tailscale IP 或 MagicDNS 名称 |
| `NAS_PORT` | SSH 端口，不填时 workflow 使用 `22` |
| `NAS_USER` | 专用部署用户 |
| `NAS_SSH_KEY` | 该用户对应的 SSH 私钥，多行原文 |
| `NAS_KNOWN_HOSTS` | 针对 NAS Tailscale IP/MagicDNS 名称、经人工核验后的 `ssh-keyscan` 公钥行，不能留空绕过主机校验 |
| `NAS_DEPLOY_PATH` | NAS 上 Compose 项目目录的绝对路径，路径不要包含空格 |
| `GHCR_USERNAME` | 能读取 GHCR 包的 GitHub 用户名 |
| `GHCR_READ_TOKEN` | GitHub classic PAT，仅授予 `read:packages`，供 NAS 拉取私有镜像 |

首次部署前，在 NAS 的 `.env` 中确认数据库/RustFS配置正确，并保证当前运行版本的三个应用镜像已存在；这样第一次自动发布失败时才具备本地回滚基础。第一次成功发布后，后续部署会按 Commit 标签自动保留可回滚版本。Tailscale OAuth Client 的 Secret 只放在 GitHub Actions Secrets 中，不要提交到仓库。

GitHub Actions 构建阶段使用工作流内置的 `GITHUB_TOKEN` 推送 GHCR；NAS 拉取私有包需要单独的最小权限 `GHCR_READ_TOKEN`。GHCR 包也可以改为公开，此时 NAS 可移除 registry 登录，但不建议因此暴露生产镜像。

### 手工验证与回滚

合并后在 GitHub Actions 的 workflow run 中确认三个镜像构建成功、Deploy job 成功且 NAS 日志出现 `knowhub deployment succeeded`。部署失败时先查看 NAS 上的 `docker compose ps` 和容器日志；脚本已经尝试自动恢复，若需人工恢复，可将 `.env` 的 `KNOWHUB_IMAGE_TAG` 改回上一个 Commit 标签后执行：

```bash
docker compose up -d --no-build backend portal admin
```

若 GHCR 在 NAS 所处网络不可稳定访问，需要同步修改 workflow 的 `IMAGE_PREFIX`、Compose 三个 `image` 固定值、registry 登录地址和 NAS 登录凭据，不改变分支触发规则。

## 文件清单

| 文件 | 作用 |
|---|---|
| `Dockerfile` | 后端镜像（拷 fat-jar，JRE 运行） |
| `knowhub-ui/Dockerfile` + `nginx-portal.conf` | 前台镜像 |
| `rookie-ui/Dockerfile` + `nginx-admin.conf` | 后台镜像 |
| `sql/00-init.sh` | MySQL 首启按依赖序灌 37 个脚本 |
| `application.prod.yml`（服务器私有） | NAS 上覆盖 Spring Boot 默认配置，不提交仓库 |
| `docker-compose.yml` | 五服务编排（mysql/redis/backend/portal/admin；NAS 端口为 5070/5077；OSS 外部已就绪） |
| `.github/workflows/ci-develop.yml` | PR 合并前的构建与测试门禁 |
| `.github/workflows/deploy-develop.yml` | 合并到 `develop` 后的 CI/CD 流程 |
| `deploy/deploy-compose.sh` | NAS 侧拉取、健康检查和回滚脚本 |
| `.env`（服务器私有） | 数据库、Redis、RustFS 与镜像版本配置；不要提交真实凭据 |
| `.dockerignore` / `knowhub-ui/.dockerignore` / `rookie-ui/.dockerignore` | 构建上下文瘦身 |
