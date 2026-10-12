# syntax=docker/dockerfile:1

# ---- Build stage: compile and package with the project's own Maven wrapper ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Resolve dependencies in their own layer so code-only changes reuse the cache.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

# Tests are run by `./mvnw test` (and CI), not on every image build.
COPY src src
RUN ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/ride-hailing-service-*.jar extract --layers --launcher --destination extracted

# ---- Runtime stage: JRE only, non-root, layered for small rebuilds ----
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app
USER app

# Least-changing layers first: libraries, then the Boot loader, then our classes.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

EXPOSE 8080
# Size the heap from the container's memory limit rather than the host's.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
