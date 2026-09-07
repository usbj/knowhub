# syntax=docker/dockerfile:1.7

# ---------------------------------------------------------------------------
# knowhub 前台运行镜像（纯拷贝预构建 dist，不在镜像内 npm build）
# ---------------------------------------------------------------------------
# 构建前提：宿主机已在 knowhub-ui/ 下执行 npm run build，产物在 knowhub-ui/dist/。
# 本镜像只把 dist 拷进 nginx 静态根，不依赖 Node / 源码。
#
# 关键点：
#   - 前台 baseURL 默认 '/api'，生产由 nginx 同名转发到后端，build 时无需注入地址。
#   - 路由 createWebHistory（HTML5 history），nginx try_files 回退 index.html 防深链 404。
#   - 文件回显走相对路径 /file/resolve/{id}、/file/public/{id}（<img src> 浏览器直发），
#     nginx 同名 location 转发到后端，与 vite dev 的 '/file' 代理对齐。
# ---------------------------------------------------------------------------

FROM nginx:1.27-alpine

LABEL org.opencontainers.image.source="https://github.com/usbj/knowhub"

# 清默认 server 块，换 knowhub 前台专用配置
RUN rm -f /etc/nginx/conf.d/default.conf
COPY nginx-portal.conf /etc/nginx/conf.d/knowhub-portal.conf

# 预构建产物拷入 nginx 静态根（构建上下文需含 dist/，见 docker-compose build.context / .dockerignore）
COPY dist/ /usr/share/nginx/html

EXPOSE 80
