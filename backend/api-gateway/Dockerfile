# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY inventory-service/pom.xml inventory-service/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY product-service/pom.xml product-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml

COPY api-gateway/src api-gateway/src

RUN --mount=type=cache,target=/root/.m2 mvn -q -pl api-gateway -am -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=build /workspace/api-gateway/target/api-gateway.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
