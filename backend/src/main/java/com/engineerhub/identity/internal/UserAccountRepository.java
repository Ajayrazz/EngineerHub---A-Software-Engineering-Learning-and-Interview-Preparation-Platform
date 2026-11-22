package com.engineerhub.identity.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

  Optional<UserAccount> findByKeycloakSub(String keycloakSub);

  /** Status only — the hot path of the suspension guard. */
  @Query("select u.status from UserAccount u where u.keycloakSub = :sub")
  Optional<String> findStatusBySub(@Param("sub") String sub);
}
