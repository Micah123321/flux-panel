# syntax=docker/dockerfile:1
# 构建阶段
# $BUILDPLATFORM 固定为构建机原生平台（amd64）。javac 产物是平台无关的字节码，
# 因此两个目标平台共用同一次原生构建，避免 arm64 在 QEMU 模拟下执行 Maven 构建（慢 10 倍以上）。
FROM --platform=$BUILDPLATFORM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# 运行阶段
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV JAVA_OPTS="-Xmx512m -Xms256m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"
ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8

RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu wget \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar
EXPOSE 6365

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
