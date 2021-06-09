FROM jboss/keycloak:11.0.3

ENV DB_VENDOR H2

ADD target/keycloak-verify-email-by-code-*-SNAPSHOT.jar /opt/jboss/keycloak/standalone/deployments/


