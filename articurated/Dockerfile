FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S articurated && adduser -S articurated -G articurated
COPY --from=builder /app/target/*.jar app.jar
RUN mkdir -p /tmp/invoices && chown articurated:articurated /tmp/invoices
USER articurated
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
