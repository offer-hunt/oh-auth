FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY build/libs/auth-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh","-c","java -jar /app/app.jar"]
