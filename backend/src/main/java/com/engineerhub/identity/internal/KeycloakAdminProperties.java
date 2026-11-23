package com.engineerhub.identity.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials of the dedicated Keycloak service-account client (client_credentials) used for role
 * management. Blank = not configured. The secret comes from the environment only.
 */
@ConfigurationProperties("engineerhub.keycloak.admin")
record KeycloakAdminProperties(String clientId, String clientSecret) {

  boolean configured() {
    return clientId != null
        && !clientId.isBlank()
        && clientSecret != null
        && !clientSecret.isBlank();
  }
}
