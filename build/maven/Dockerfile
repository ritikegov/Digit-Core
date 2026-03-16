# ──────────────── BUILD STAGE ────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
ARG WORK_DIR
WORKDIR /app

# Copy project files
COPY ${WORK_DIR}/pom.xml ./pom.xml
COPY build/maven/start.sh ./start.sh
COPY ${WORK_DIR}/src ./src

# Build the project
RUN mvn -B -f /app/pom.xml package -DskipTests

# ─────────────── RUNTIME STAGE ───────────────
FROM eclipse-temurin:17-jre

WORKDIR /opt/egov

# Copy artifacts from the build stage
COPY --from=build /app/target/*.jar ./application.jar
COPY --from=build /app/start.sh ./start.sh

RUN chmod +x ./start.sh

ENTRYPOINT ["sh", "./start.sh"]