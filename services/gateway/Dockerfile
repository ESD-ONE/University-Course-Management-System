# Pinned by digest: a rebuild gets exactly this JRE until the pin is bumped on purpose.
FROM eclipse-temurin:17.0.20.1_1-jre-alpine@sha256:c4a33e6ddb738fe4be0a28a613ea0940cec42d25d7263c3e2238c43354cb19b0
RUN addgroup -S ucms && adduser -S -G ucms -H -s /sbin/nologin ucms
WORKDIR /app
# Owned by root: the service user can read and run it but not change it.
COPY target/*.jar app.jar
USER ucms
EXPOSE 8080
# sun.net.inetaddr.ttl=5: DNS answers are re-read every few seconds, so load-balanced
# calls notice replicas coming and going (config/DnsDiscoveryClient).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Dsun.net.inetaddr.ttl=5", "-jar", "app.jar"]
