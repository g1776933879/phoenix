# ========================================
# 凤凰 Dockerfile - 多阶段构建
# ========================================

# ---- Stage 1: Maven 构建 ----
FROM maven:3.9.8-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# 先复制依赖配置（利用Docker缓存层）
COPY pom.xml .
COPY your-agent-core/pom.xml ./your-agent-core/
COPY your-agent-spring-boot-starter/pom.xml ./your-agent-spring-boot-starter/
COPY your-business-app/pom.xml ./your-business-app/

# 下载依赖（单独层，只改pom才重下载）
RUN mvn dependency:go-offline -B -q

# 复制源码
COPY your-agent-core/src ./your-agent-core/src
COPY your-agent-spring-boot-starter/src ./your-agent-spring-boot-starter/src
COPY your-business-app/src ./your-business-app/src
COPY your-agent-skill-repo ./your-agent-skill-repo

# 编译打包（跳过测试以加速构建，测试在CI阶段执行）
RUN mvn clean package -pl your-business-app -am -DskipTests -B -q

# ---- Stage 2: 精简运行时 ----
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 安装运行时工具
RUN apk add --no-cache curl bash tzdata

# 从builder复制构建产物
COPY --from=builder /build/your-business-app/target/*.jar app.jar

# 复制技能库
COPY --from=builder /build/your-agent-skill-repo/skills /app/skills

# 创建数据目录
RUN mkdir -p /root/.agent

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -sf http://localhost:8080/api/agent/health || exit 1

# 启动
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:--Xms512m -Xmx2g} -jar app.jar --spring.profiles.active=prod"]