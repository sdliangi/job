FROM openjdk:8-jre-slim
MAINTAINER xuxueli

EXPOSE 8080
EXPOSE 9090

ENV TZ=PRC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

ADD xxl-job-executor-sample-springboot-*.jar /app.jar

ENTRYPOINT ["sh","-c","java -jar /app.jar"]