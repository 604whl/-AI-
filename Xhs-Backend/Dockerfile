# Multi-stage build for XhsAgent backend
# Stage 1: Build the application
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 缓存依赖层
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw -B -ntp dependency:go-offline || true

# 复制源码并打包（跳过测试，避免运行时数据库连不上导致构建失败）
COPY src src
RUN ./mvnw -B -ntp -DskipTests package

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

# 时区与中文支持
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

# 非 root 运行
RUN groupadd --system app && useradd --system --gid app --create-home --home-dir /app app

COPY --from=build /workspace/target/*.jar /app/app.jar
RUN chown -R app:app /app

USER app

# Spring Boot Actuator 健康检查
EXPOSE 8125

# 生产 profile 必须显式设置；启动失败立即退出，避免错误部署
ENV SPRING_PROFILES_ACTIVE=prod

# 启动入口
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
