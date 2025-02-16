FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/memeswap-bot-1.0-SNAPSHOT-jar-with-dependencies.jar /app/memeswap-bot.jar
CMD ["java", "-jar", "/app/memeswap-bot.jar"]