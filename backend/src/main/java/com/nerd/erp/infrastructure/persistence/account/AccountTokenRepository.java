package com.nerd.erp.infrastructure.persistence.account;

import com.nerd.erp.domain.account.AccountToken;
import com.nerd.erp.domain.account.AccountTokenKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {
    Optional<AccountToken> findByTokenHash(String tokenHash);
    List<AccountToken> findByUserIdAndKindAndUsedAtIsNull(UUID userId, AccountTokenKind kind);
}
