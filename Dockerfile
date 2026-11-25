# ---- Build stage -----------------------------------------------------------
# Bytecode target is Java 21; any JDK >= 21 works for the build.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src src
RUN mvn -B -ntp package -DskipTests

# ---- Runtime stage ---------------------------------------------------------
# CVE-2026-114938 ("Java4Shell") policy: run only on a patched Temurin build.
# 25.0.5.1 contains the HotSpot fix (released 2026-11-17). Do NOT downgrade.
FROM eclipse-temurin:25.0.5.1_1-jre-jammy
WORKDIR /app

COPY --from=build /build/target/devhub-batch-core.jar app.jar

# Defense in depth: the app never calls the Base64 decoder, but also disable
# its HotSpot intrinsics. IgnoreUnrecognizedVMOptions keeps the flag harmless
# on JDK builds where it no longer exists.
ENTRYPOINT ["java", "-XX:+IgnoreUnrecognizedVMOptions", "-XX:-UseBASE64Intrinsics", "-jar", "/app/app.jar"]
