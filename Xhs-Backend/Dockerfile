# syntax=docker/dockerfile:1

# ============================================================
# Stage 1: Builder — 编译 + 依赖下载
# ============================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# 利用 Docker layer cache：先复制 pom，再下载依赖
COPY pom.xml .
RUN mvn dependency:go-offline -B -q || true

# 复制源码并编译
COPY src ./src
RUN mvn package -B -DskipTests -q

# ============================================================
# Stage 2: Runtime — 仅运行时依赖
# ============================================================
FROM eclipse-temurin:21-jre-alpine

# 安装健康检查依赖（wget/curl）
RUN apk add --no-cache wget curl

# 安全：非 root 运行
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# 只复制 JAR 包和必要资源
COPY --from=builder /app/target/*.jar app.jar
COPY --from=builder /app/src/main/resources ./resources

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8125/api/actuator/health/liveness || exit 1

EXPOSE 8125

# JVM 优化（Alpine musl libc 兼容）
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
