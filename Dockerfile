FROM --platform=$BUILDPLATFORM gradle:8.4-jdk17 AS builder

RUN mkdir /code
WORKDIR /code
ENV GRADLE_USER_HOME=/code/.gradlecache \
    GRADLE_OPTS="-Djdk.lang.Process.launchMechanism=vfork -Dorg.gradle.vfs.watch=false"

COPY ./build.gradle.kts ./gradle.properties ./settings.gradle.kts /code/
COPY ./buildSrc /code/buildSrc

RUN gradle downloadDependencies copyDependencies startScripts

COPY ./src /code/src

RUN gradle jar

FROM eclipse-temurin:17-jre

LABEL description="RADAR-base data mapper"

ENV MAPPER_CONFIG="/etc/radar-mapper/mapper.yml" \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxHeapFreeRatio=10 -XX:MinHeapFreeRatio=10"

COPY --from=builder /code/build/third-party/* /usr/lib/
COPY --from=builder /code/build/scripts/* /usr/bin/
COPY --from=builder /code/build/libs/* /usr/lib/

RUN mkdir -p /data/odm /data/output /etc/radar-mapper \
    && chown -R 101:101 /data /etc/radar-mapper

VOLUME ["/data/odm", "/data/output", "/etc/radar-mapper"]

USER 101:101

ENTRYPOINT ["radar-mapper"]
