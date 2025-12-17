FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml ./
COPY src ./src
COPY lib ./lib


RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jdk

RUN apt-get update && apt-get install -y netcat-openbsd && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /build/target/MqttOpcUaAgent*-jar-with-dependencies.jar ./MqttOpcUaAgent-jar-with-dependencies.jar
COPY --from=builder /build/target/SampleConsoleServer*-jar-with-dependencies.jar ./SampleConsoleServer-jar-with-dependencies.jar
COPY --from=builder /build/target/TimescaleAgent*-jar-with-dependencies.jar ./TimescaleAgent-jar-with-dependencies.jar
COPY --from=builder /build/target/HydrationAgent*-jar-with-dependencies.jar ./HydrationAgent-jar-with-dependencies.jar

COPY lib ./lib

EXPOSE 52520


CMD bash -c "\
  echo 'Starting SampleConsoleServer ' && \
  java --add-opens java.base/java.net=ALL-UNNAMED \
       -cp SampleConsoleServer-jar-with-dependencies.jar:lib/* \
       com.prosysopc.ua.samples.server.SampleConsoleServer & \
  echo 'Waiting for OPC UA Server to be ready on port 52520 ' && \
  while ! nc -z localhost 52520; do \
    echo 'Server not ready yet, waiting 3s '; \
    sleep 3; \
  done; \
  echo 'OPC UA Server is ready!' && \
  echo 'Starting MqttOpcUaAgent ' && \
  java --add-opens java.base/java.net=ALL-UNNAMED \
       -cp MqttOpcUaAgent-jar-with-dependencies.jar:lib/* \
       com.prosysopc.ua.samples.agent.MqttOpcUaAgent"
