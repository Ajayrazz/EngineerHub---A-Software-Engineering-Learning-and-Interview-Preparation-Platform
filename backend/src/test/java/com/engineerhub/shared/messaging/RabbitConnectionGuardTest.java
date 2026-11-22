package com.engineerhub.shared.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.amqp.autoconfigure.RabbitConnectionDetails;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;

/** The application must not start with an unsafe broker configuration. */
class RabbitConnectionGuardTest {

  private static RabbitConnectionDetails details(String host, String user, String password) {
    return new RabbitConnectionDetails() {
      @Override
      public String getUsername() {
        return user;
      }

      @Override
      public String getPassword() {
        return password;
      }

      @Override
      public String getVirtualHost() {
        return "/";
      }

      @Override
      public List<Address> getAddresses() {
        return host == null ? List.of() : List.of(new Address(host, 5672));
      }
    };
  }

  private static RabbitConnectionGuard guard(
      String host, String user, String password, boolean ssl) {
    RabbitProperties props = new RabbitProperties();
    props.getSsl().setEnabled(ssl);
    return new RabbitConnectionGuard(details(host, user, password), props);
  }

  @Test
  void acceptsALocalBrokerWithRealCredentials() {
    assertThatCode(() -> guard("localhost", "engineerhub", "pw", false).afterPropertiesSet())
        .doesNotThrowAnyException();
    assertThatCode(() -> guard("127.0.0.1", "engineerhub", "pw", false).afterPropertiesSet())
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsARemoteBrokerOnlyOverTls() {
    assertThatCode(
            () -> guard("mq.internal.example", "engineerhub", "pw", true).afterPropertiesSet())
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () -> guard("mq.internal.example", "engineerhub", "pw", false).afterPropertiesSet())
        .hasMessageContaining("plain TCP");
  }

  @Test
  void refusesMissingHostCredentialsAndTheGuestAccount() {
    assertThatThrownBy(() -> guard(null, "u", "p", false).afterPropertiesSet())
        .hasMessageContaining("host");
    assertThatThrownBy(() -> guard("", "u", "p", false).afterPropertiesSet())
        .hasMessageContaining("host");
    assertThatThrownBy(() -> guard("localhost", "", "p", false).afterPropertiesSet())
        .hasMessageContaining("credentials");
    assertThatThrownBy(() -> guard("localhost", "u", "", false).afterPropertiesSet())
        .hasMessageContaining("credentials");
    assertThatThrownBy(() -> guard("localhost", null, null, false).afterPropertiesSet())
        .hasMessageContaining("credentials");
    assertThatThrownBy(() -> guard("localhost", "guest", "guest", false).afterPropertiesSet())
        .hasMessageContaining("guest");
    assertThatThrownBy(() -> guard("localhost", "GUEST", "x", false).afterPropertiesSet())
        .hasMessageContaining("guest");
  }
}
