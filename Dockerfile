FROM jboss/keycloak:15.1.1

ENV DB_VENDOR H2

ADD target/keycloak-verify-email-by-code-*-SNAPSHOT.jar /opt/jboss/keycloak/standalone/deployments/
