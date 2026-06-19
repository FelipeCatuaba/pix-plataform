FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY services/pix-api/pom.xml services/pix-api/pom.xml
RUN chmod +x mvnw && ./mvnw -q -f services/pix-api/pom.xml -DskipTests dependency:go-offline

COPY services/pix-api/src services/pix-api/src
RUN ./mvnw -q -f services/pix-api/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/services/pix-api/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
