# Builds the s3-archive fork from source and runs it on the official Komga image's runtime
# (same native libraries, JRE and entrypoint). Build from the repository root:
#   docker build -f docker/s3-archive.Dockerfile -t komga:s3-archive .
ARG KOMGA_BASE=gotson/komga:1.29.2

FROM node:22 AS webui
ENV NODE_OPTIONS=--max-old-space-size=4096
WORKDIR /src/komga-webui
COPY komga-webui/package.json komga-webui/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY komga-webui/ ./
RUN npm run build

FROM node:24 AS nextui
ENV NODE_OPTIONS=--max-old-space-size=4096
WORKDIR /src/next-ui
COPY next-ui/package.json next-ui/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY next-ui/ ./
RUN npm run build:with-i18n

FROM eclipse-temurin:21-jdk AS jar
WORKDIR /src
COPY . .
COPY --from=webui /src/komga-webui/dist komga-webui/dist
COPY --from=nextui /src/next-ui/dist next-ui/dist
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon -x :komga:generateGitProperties :komga:webuiCopyIndex :komga:nextuiCopyIndex :komga:bootJar && \
    cp $(ls komga/build/libs/*.jar | grep -v -- -plain.jar) /application.jar
WORKDIR /builder
RUN java -Djarmode=tools -jar /application.jar extract --layers --destination extracted

FROM ${KOMGA_BASE}
RUN rm -rf /app/*
COPY --from=jar /builder/extracted/dependencies/ /app/
COPY --from=jar /builder/extracted/spring-boot-loader/ /app/
COPY --from=jar /builder/extracted/snapshot-dependencies/ /app/
COPY --from=jar /builder/extracted/application/ /app/
LABEL org.opencontainers.image.source="https://github.com/alexcatdad/komga"
