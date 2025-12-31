FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY target/gateway-service-1.0.0-SNAPSHOT.jar app.jar
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
