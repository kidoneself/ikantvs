# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先拷 pom 下载依赖，利用 Docker 层缓存
COPY pom.xml .
RUN mvn -B dependency:go-offline

# 再拷源码构建
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN apk add --no-cache wget

COPY --from=build /app/target/jyinshi-server.jar app.jar

EXPOSE 8888

# Java 17 模块开放（Hutool 反射兼容）
ENTRYPOINT ["java", \
  "--add-opens", "java.base/java.net=ALL-UNNAMED", \
  "--add-opens", "java.base/java.lang=ALL-UNNAMED", \
  "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED", \
  "--add-opens", "java.base/sun.net.www.protocol.https=ALL-UNNAMED", \
  "-jar", "app.jar"]
