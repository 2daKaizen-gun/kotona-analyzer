# Build Stage: Gradle 이용해 jar 파일 생성
FROM eclipse-temurin:21-jdk-jammy AS build
# 작업 디렉터리를 정한다. 없으면 소스가 이미지의 / 에 그대로 풀린다.
WORKDIR /workspace
COPY . .
RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon

# Run Stage: 가벼운 JRE 환경에서 실행
FROM eclipse-temurin:21-jre-jammy
# root 로 돌지 않는다. 이 프로세스가 뚫리면 컨테이너 안의 모든 것이 그 권한으로 열린다.
# 앱이 쓰는 곳은 /tmp(Tomcat 임시 파일) 뿐이고, 그곳은 누구나 쓸 수 있다.
RUN groupadd --system kotona && useradd --system --gid kotona --no-create-home kotona
WORKDIR /app
COPY --from=build --chown=kotona:kotona /workspace/build/libs/app.jar app.jar
USER kotona
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
