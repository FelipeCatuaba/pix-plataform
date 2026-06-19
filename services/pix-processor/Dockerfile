FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY services/pix-processor/pom.xml services/pix-processor/pom.xml
RUN chmod +x mvnw && ./mvnw -q -f services/pix-processor/pom.xml -DskipTests dependency:go-offline

COPY services/pix-processor/src services/pix-processor/src
RUN ./mvnw -q -f services/pix-processor/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/services/pix-processor/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
