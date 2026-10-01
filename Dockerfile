# syntax=docker/dockerfile:1

# ---- Build stage ----
# Maven resolves everything through CFA JFrog using your own ~/.m2 settings,
# mounted as build secrets: they exist only for the RUN step and never land in
# an image layer. The dependency cache mount keeps rebuilds fast.
FROM maven:3.9-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS build
WORKDIR /src

COPY .mvn ./.mvn
COPY pom.xml ./
COPY src ./src

RUN --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml,required=true \
    --mount=type=secret,id=maven_settings_security,target=/root/.m2/settings-security.xml \
    --mount=type=cache,target=/root/.m2/repository \
    mvn -B --strict-checksums package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre@sha256:8da0490fa9a3c26867012019565948eef0ee69438f5c75ac28146967bae984b5
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app

COPY --from=build /src/target/marketplace.jar /app/marketplace.jar

EXPOSE 8080
USER app
ENTRYPOINT ["java", "-jar", "/app/marketplace.jar"]
