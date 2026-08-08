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
COPY --from=build --chown=app:app /app/target/*.jar app.jar

# COPY/RUN above run as root (the default, before USER switches it), so
# /app itself is root-owned unless explicitly handed over — the non-root
# `app` user then has no permission to create anything under it, including
# LocalStorageProvider's default ./uploads directory (STORAGE_PROVIDER=local)
# at runtime. Pre-creating it and chown'ing the whole tree here fixes that
# at the source, rather than special-casing just this one directory.
RUN mkdir -p /app/uploads && chown -R app:app /app

USER app

EXPOSE 8080
# SPRING_PROFILES_ACTIVE=prod plus every var listed in DEPLOYMENT.md is
# expected to be supplied by the hosting platform's environment/secrets
# config, not baked into this image.
ENTRYPOINT ["java", "-jar", "app.jar"]
