FROM openjdk:8-jre-slim
MAINTAINER xuxueli

EXPOSE 8080

ENV TZ=PRC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

ADD xxl-job-admin-*.jar /app.jar

ENTRYPOINT ["sh","-c","java -jar /app.jar"]