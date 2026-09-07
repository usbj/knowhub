# syntax=docker/dockerfile:1.7

# ---------------------------------------------------------------------------
# rookie 后台管理运行镜像（纯拷贝预构建 dist，不在镜像内 npm build）
# ---------------------------------------------------------------------------
# 构建前提：宿主机已在 rookie-ui/ 下执行 npm run build，产物在 rookie-ui/dist/。
# 本镜像只把 dist 拷进 nginx 静态根，不依赖 Node / 源码。
#
# 关键点：
#   - 后台 baseURL 同样默认 '/api'，生产由 nginx 同名转发到后端。
#   - 路由 createWebHistory，nginx try_files 回退 index.html 防深链 404。
#   - 文件模块所有接口走 /file 前缀（public/proxy/proxy-upload/list 等），nginx 同名转发。
# ---------------------------------------------------------------------------

FROM nginx:1.27-alpine

LABEL org.opencontainers.image.source="https://github.com/usbj/knowhub"

RUN rm -f /etc/nginx/conf.d/default.conf
COPY nginx-admin.conf /etc/nginx/conf.d/knowhub-admin.conf

COPY dist/ /usr/share/nginx/html

EXPOSE 80
