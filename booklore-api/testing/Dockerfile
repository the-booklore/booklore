FROM eclipse-temurin:21-jdk-alpine
VOLUME /tmp
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar
# Create a non-root user and group
RUN addgroup -S booklore && adduser -S booklore -G booklore
# Change ownership of the application jar
RUN chown booklore:booklore /app.jar
# Switch to the non-root user
USER booklore
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
