# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
ENV TZ=Asia/Ho_Chi_Minh
COPY --from=build /workspace/target/attendance-api-*.jar /app/app.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
