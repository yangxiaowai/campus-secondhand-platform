# 成员6：多阶段构建，Tomcat 9（Servlet 3.1+）与 Spring 5.3 静态资源兼容
FROM maven:3.9-eclipse-temurin-8 AS builder
WORKDIR /build

COPY pom.xml .
COPY src ./src
COPY docker/campus-db.properties docker/campus-redis-config.properties ./docker/

RUN mvn -q -DskipTests package \
    && mkdir -p inj/WEB-INF/classes \
    && cp docker/campus-db.properties inj/WEB-INF/classes/db.properties \
    && cp docker/campus-redis-config.properties inj/WEB-INF/classes/redis-config.properties \
    && cd target \
    && jar uf secondhand-market.war -C ../inj WEB-INF/classes/db.properties \
    && jar uf secondhand-market.war -C ../inj WEB-INF/classes/redis-config.properties

FROM tomcat:9.0-jre8-temurin
RUN rm -rf /usr/local/tomcat/webapps/*

COPY --from=builder /build/target/secondhand-market.war /usr/local/tomcat/webapps/ROOT.war

RUN mkdir -p /data/upload && chmod a+rwx /data/upload

# 容器默认 POSIX 区域时，JSP/日志等易出现编码问题；与 UTF-8 应用一致
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8
ENV CATALINA_OPTS="-Xms256m -Xmx512m -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"

EXPOSE 8080
