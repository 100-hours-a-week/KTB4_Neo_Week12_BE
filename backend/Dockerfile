# -----------------------------
# 1단계: Spring Boot JAR 빌드
# -----------------------------

# Java 17 JDK 이미지를 빌드 전용 단계로 사용 (builder라는 이름을 붙여 나중 단계에서 사용)
FROM eclipse-temurin:17-jdk-jammy AS builder

# 컨테이너 내부 작업 디렉터리
WORKDIR /app

# Gradle Wrapper 실행 파일 복사
COPY gradlew gradlew

# Gradle Wrapper 관련 파일 복사 (사용할 설정과 JAR 파일)
COPY gradle gradle

# 프로젝트 빌드 설정 복사
# 의존성 및 프로젝트 빌드 설정 파일을 복사
# 소스 파일만 변경된 경우 의존성 다운로드 캐시를 재사용하기 위함.
COPY build.gradle settings.gradle gradle.properties ./

# Linux 컨테이너에서 gradlew를 실행할 수 있도록 권한 부여
RUN chmod +x gradlew

# 프로젝트 의존성 미리 받는다.
RUN ./gradlew dependencies --no-daemon

# Java 소스와 resources를 복사
COPY src src

# 배포용 Spring Boot JAR 생성
RUN ./gradlew clean bootJar -x test --no-daemon

# plain JAR를 제외한 Spring Boot JAR를 찾아 고정 이름으로 복사한다.
RUN JAR_FILE=$(find build/libs -maxdepth 1 -type f \
        -name "*.jar" ! -name "*-plain.jar" | head -n 1) \
    && test -n "$JAR_FILE" \
    && cp "$JAR_FILE" /app/application.jar


# -----------------------------
# 2단계: Spring Boot 실행
# -----------------------------

# 실행단계에서는 JRE 이미지 사용 (컴파일러 필요 없음)
FROM eclipse-temurin:17-jre-jammy AS runtime

# 애플리케이션 실행 폴더를 지정
WORKDIR /app

# root 대신 사용할 Spring 사용자와 그룹 생성
RUN groupadd --system spring \
    && useradd --system --gid spring spring

# 업로드 이미지 저장 디렉토리 생성
RUN mkdir -p /app/uploads \
    && chown -R spring:spring /app

# builder 단계에서 생성한 JAR만 최종 이미지 복사
COPY --from=builder --chown=spring:spring /app/application.jar /app/application.jar

# 이후 Spring 사용자 권한으로 명령 실행
USER spring

# 이 컨테이너의 Spring 서버가 8080 포트를 사용
# 외부에 포트가 공개되지는 않음
EXPOSE 8080

# 제힌된 컨테이너 메모리에서 JVM이 너무 큰 Heap을 잡지 않도록 함
# t3.micro의 제한된 메모리를 고려한 옵션
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0"

# 컨테이너가 시작 명령
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
