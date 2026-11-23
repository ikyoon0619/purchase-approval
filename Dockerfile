# 멀티 스테이지 빌드: 1단계(JDK + Gradle)에서 jar를 만들고, 2단계(JRE만)에는 jar 하나만 담는다.
# 최종 이미지에 소스·Gradle 캐시·JDK가 들어가지 않아 작고(약 300MB대), 공격 면도 줄어든다.

# ---------- 1단계: 빌드 ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성 목록이 바뀌지 않았으면 이 층(layer)을 캐시에서 재사용한다 → 소스만 고치면 다시 받지 않음
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null

COPY src src
# 테스트는 CI(GitHub Actions)에서 따로 돌린다. 이미지 빌드는 jar만 만든다
RUN ./gradlew bootJar --no-daemon -x test

# ---------- 2단계: 실행 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# root가 아닌 사용자로 실행 (컨테이너가 뚫려도 root 권한이 아니게)
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /app/uploads && chown app:app /app/uploads

COPY --from=build /workspace/build/libs/app.jar app.jar

ENV TZ=Asia/Seoul \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"

USER app
EXPOSE 8080
# sh -c 로 감싸야 JAVA_OPTS 환경변수가 펼쳐진다. exec로 java가 PID 1이 되어 docker stop 신호를 직접 받는다
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
