FROM eclipse-temurin:25.0.5.1_2-jre
# CVE-2026-114938 ("Java4Shell") policy: only patched Temurin builds. Hex-only
# secrets — no Base64 anywhere in the pipeline.
WORKDIR /app
COPY target/devhub-batch-api.jar app.jar
ENV TZ=Asia/Ho_Chi_Minh \
    SPRING_PROFILES_ACTIVE=mysql \
    JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics"
EXPOSE 8080
ENTRYPOINT ["java", "-Dslf4j.provider=ch.qos.logback.classic.spi.LogbackServiceProvider", "-jar", "app.jar"]
