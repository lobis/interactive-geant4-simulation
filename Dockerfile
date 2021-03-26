FROM gradle:jdk11-hotspot AS build

ARG source=/home/gradle/src
COPY . $source
WORKDIR $source
RUN gradle installDist

#
FROM openjdk:11-jre-slim
ARG source=/home/gradle/src

EXPOSE 8080

RUN mkdir /app

COPY --from=build $source/build/install /app/

ENTRYPOINT ["/bin/bash", "app/interactive-simulation/bin/interactive-simulation"]
