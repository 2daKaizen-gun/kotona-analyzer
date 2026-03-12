# Build Stage: Gradle 이용해 jar 파일 생성
FROM eclipse-temurin:21-jdk-jammy AS build
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew bootJar

# Run Stage: 가벼운 JRE 환경에서 실행
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]