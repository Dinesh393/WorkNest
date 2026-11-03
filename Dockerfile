FROM tomcat:9.0.123-jdk17-temurin AS build

WORKDIR /worknest
COPY src/main/java/ src/main/java/
COPY src/main/webapp/ src/main/webapp/

RUN set -eux; \
    mkdir -p /tmp/worknest-classes /opt/worknest; \
    find src/main/java -type f -name '*.java' > /tmp/worknest-sources.txt; \
    javac --release 17 -encoding UTF-8 \
      -cp 'src/main/webapp/WEB-INF/lib/*' \
      -d /tmp/worknest-classes @/tmp/worknest-sources.txt; \
    cp -a src/main/webapp/. /opt/worknest/; \
    rm -f /opt/worknest/WEB-INF/lib/servlet-api.jar; \
    mkdir -p /opt/worknest/WEB-INF/classes; \
    cp -a /tmp/worknest-classes/. /opt/worknest/WEB-INF/classes/

FROM tomcat:9.0.123-jdk17-temurin

RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /opt/worknest/ /usr/local/tomcat/webapps/ROOT/
COPY docker-entrypoint.sh /usr/local/bin/worknest-entrypoint
RUN chmod 0755 /usr/local/bin/worknest-entrypoint

EXPOSE 10000
ENTRYPOINT ["/usr/local/bin/worknest-entrypoint"]