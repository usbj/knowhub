# syntax=docker/dockerfile:1.7

# ---------------------------------------------------------------------------
# knowhub 后端运行镜像（纯拷贝预构建 fat-jar，不在镜像内编译）
# ---------------------------------------------------------------------------
# 构建前提：宿主机已执行 mvn package，产物在 rookie-admin/target/rookie-admin-*.jar。
# 本镜像只负责把 fat-jar 拷进 JRE 基础镜像运行，不依赖 Maven / 源码，构建快、体积小。
# 若需在镜像内编译，参考注释掉的 builder 阶段（需把 context 改回仓库根并恢复 mvn 步骤）。
# ---------------------------------------------------------------------------

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 时区东八区，与 application.yml jackson time-zone: GMT+8 对齐
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx1024m -Duser.timezone=Asia/Shanghai"

# 本地存储模式文件落盘目录（access_mode=local 时 objectKey 即相对此目录）
# 与 application.yml storage.local-base-path 默认 ./knowhub-upload 对应；挂卷持久化
RUN mkdir -p /app/knowhub-upload /app/upload

# 拷入预构建 fat-jar（构建上下文需能访问到该路径，见 docker-compose build.context）
COPY knowhub-admin-*.jar /app/app.jar

# 8080 为后端服务端口（application.yml server.port）
EXPOSE 8080

# 数据库/Redis/OSS 连接地址全部走环境变量覆盖 application.yml 默认值（Spring relaxed binding）
# 见 docker-compose backend.environment
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
