# ──────────────── BUILD STAGE ────────────────
FROM --platform=linux/amd64 maven:3.9-eclipse-temurin-17 AS build
ARG WORK_DIR
WORKDIR /app

# Download dependencies as a separate layer — only re-runs when pom.xml changes
COPY ${WORK_DIR}/pom.xml ./pom.xml
RUN mvn -B dependency:go-offline -f /app/pom.xml

# Compile — re-runs on every source change, but deps are already cached above
COPY ${WORK_DIR}/src ./src
RUN mvn -B -f /app/pom.xml package -DskipTests

# ─────────────── RUNTIME STAGE ───────────────
FROM gcr.io/distroless/java17-debian12:nonroot

WORKDIR /opt/egov

# Copy artifact from the build stage
COPY --from=build /app/target/*.jar ./application.jar

ENTRYPOINT ["java", "-jar", "application.jar"]