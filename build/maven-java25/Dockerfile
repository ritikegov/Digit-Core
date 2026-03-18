# ──────────────── BUILD STAGE ────────────────
FROM eclipse-temurin:25-jdk AS build
ARG WORK_DIR
WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy POM, entrypoint script, and source
COPY ${WORK_DIR}/pom.xml       ./pom.xml
COPY build/maven-java25/start.sh ./start.sh
COPY ${WORK_DIR}/src           ./src

# Build the app (skip tests for speed)
RUN mvn -B -f pom.xml package -DskipTests


# ─────────────── RUNTIME STAGE ───────────────
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /opt/egov

# Pull in the fat JAR and our startup script
COPY --from=build /app/target/*.jar ./application.jar
COPY --from=build /app/start.sh    ./start.sh

RUN chmod +x ./start.sh

ENTRYPOINT ["sh", "./start.sh"]
