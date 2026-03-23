FROM eclipse-temurin:25-jdk AS build
ARG WORK_DIR
ARG nexusUsername
ARG nexusPassword
WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# copy the project files
COPY ${WORK_DIR}/pom.xml ./pom.xml
COPY ${WORK_DIR}/settings.xml ./settings.xml
COPY ${WORK_DIR}/src ./src

RUN mvn -B -f /app/pom.xml test verify deploy -s settings.xml  \
    -Dnexus.user=${nexusUsername} -Dnexus.password=${nexusPassword}
FROM scratch