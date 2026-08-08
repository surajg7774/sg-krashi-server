# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Dependencies resolve into their own Docker layer, cached separately from
# source changes — editing a .java file no longer re-downloads the entire
# Maven dependency tree on every image rebuild.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
USER app

EXPOSE 8080
# SPRING_PROFILES_ACTIVE=prod plus every var listed in DEPLOYMENT.md is
# expected to be supplied by the hosting platform's environment/secrets
# config, not baked into this image.
ENTRYPOINT ["java", "-jar", "app.jar"]
