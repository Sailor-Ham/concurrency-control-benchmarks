# ==========================================
# 1. Builder Stage (빌드 환경)
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Grable 캐싱을 위해 설정 파일들만 먼저 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 실행 권한 부여 및 의존성 다운로드
RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon

# 실제 소스 코드 복사 및 빌드 (테스트 생략)
COPY src src
RUN ./gradlew clean bootJar --no-daemon -x test

# ==========================================
# 2. Runtime Stage (실행 환경)
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 시스템 타임존 설정
ENV TZ=Asia/Seoul
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/${TZ} /etc/localtime && \
    echo "${TZ}" > /etc/timezone

# Builder Stage에서 빌드된 JAR 파일 복사
COPY --from=builder /build/build/libs/*-SNAPSHOT.jar app.jar

# 환경 변수 기본값 설정
ENV SPRING_PROFILES_ACTIVE=bench

# 애플리케이션 포트
EXPOSE 8080

# 컨테이너 실행 명령
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]
