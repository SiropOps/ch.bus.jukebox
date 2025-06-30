FROM eclipse-temurin:17-jre

LABEL maintainer="SiropOps <cyril@botalista.community>"

ENV TZ=Europe/Zurich

ADD ./target/app.jar /app/

EXPOSE 3335

CMD ["java", "-Xms64m", "-Xmx900m", "-jar", "/app/app.jar"]