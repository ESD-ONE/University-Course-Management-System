# Keycloak for UCMS: the stock image plus the BCrypt password-hash provider (so accounts
# migrated from identity-access-service keep their passwords) and the ucms realm
# definition, pre-built for Postgres so the container starts with `start --optimized`.

FROM maven:3.9.11-eclipse-temurin-17@sha256:e4a7ace3dc0d645ed97f8d9ad0b0d3f0b14fa8d150138f27f116d7105a639b82 AS provider
WORKDIR /build
COPY bcrypt-provider/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY bcrypt-provider/src ./src
RUN mvn -q -B package

# Must match <keycloak.version> in bcrypt-provider/pom.xml.
FROM quay.io/keycloak/keycloak:26.0.7@sha256:4388e2379b7e870a447adbe7b80bd61f5fbf04e925832b19669fda4957f05a81 AS builder
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_HTTP_RELATIVE_PATH=/auth
COPY --from=provider /build/target/keycloak-bcrypt-provider-1.0.0.jar /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.0.7@sha256:4388e2379b7e870a447adbe7b80bd61f5fbf04e925832b19669fda4957f05a81
COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY realm/ /opt/keycloak/data/import/
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]
