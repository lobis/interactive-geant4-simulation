FROM gradle:jdk11-hotspot
COPY . /home/src

RUN ["/bin/bash"]


FROM gradle:jdk11-hotspot AS build

COPY . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon

FROM openjdk:11-jre-slim

EXPOSE 8080

RUN mkdir /app

COPY --from=build /home/gradle/src/build/libs/*.jar /app/interactive-geant4-simulation.jar

ENTRYPOINT ["java", "-XX:+UnlockExperimentalVMOptions", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/interactive-geant4-simulation.jar"]
