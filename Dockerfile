# Build Stage: Gradle 이용해 jar 파일 생성
FROM eclipse-temurin:21-jdk-jammy AS build
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew bootJar

# Run Stage: 가벼운 JRE 환경에서 실행
