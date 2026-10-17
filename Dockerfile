FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV SPRING_H2_CONSOLE_ENABLED=false
ENV JAVA_TOOL_OPTIONS="-Xmx300m -XX:+UseSerialGC"
CMD ["sh", "-c", "java -jar app.jar --server.port=${PORT:-10000}"]