# =========================================================
# RWG Backend - Dockerfile (multi-stage)
# Stage 1: build jar bằng Maven + Temurin 21 (bỏ qua test vì
#          integration test cần Docker - chạy riêng bằng mvn verify)
# Stage 2: runtime JRE nhỏ gọn
# =========================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependencies trước để layer sau chỉ build source
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd -r rwg && useradd -r -g rwg rwg
COPY --from=build /build/target/rwg-backend-*.jar app.jar

USER rwg
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
