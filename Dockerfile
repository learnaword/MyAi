# ---- Build Stage ----
FROM maven:3.9-eclipse-temurin-20 AS builder

WORKDIR /app

# 依赖缓存
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# 编译
COPY src ./src
RUN mvn package -DskipTests -B \
    && cp target/interview-agent-*.jar /app/app.jar

# ---- Runtime Stage ----
FROM eclipse-temurin:20-jre-alpine

RUN apk add --no-cache ca-certificates tzdata \
    && addgroup -S app && adduser -S app -G app

ENV TZ=Asia/Shanghai
ENV SERVER_PORT=9090

WORKDIR /app

COPY --from=builder /app/app.jar app.jar

EXPOSE 9090

USER app

ENTRYPOINT ["java", "-jar", "app.jar"]
