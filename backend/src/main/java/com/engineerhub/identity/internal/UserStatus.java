package com.engineerhub.identity.internal;

import java.util.Locale;

enum UserStatus {
  ACTIVE,
  SUSPENDED,
  PENDING_DELETION,
  DELETED;

  String db() {
    return name().toLowerCase(Locale.ROOT);
  }

  static UserStatus fromDb(String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }
}
