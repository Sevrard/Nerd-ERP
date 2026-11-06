package com.nerd.erp.application.account;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.domain.account.AccountToken;
import com.nerd.erp.domain.account.AccountTokenKind;
import com.nerd.erp.infrastructure.persistence.account.AccountTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Émission et consommation des jetons à usage unique. Le jeton en clair (256 bits, Base64 URL) n'est
 * jamais stocké : seule son empreinte SHA-256 l'est, ce qui rend une fuite de la base inexploitable.
 */
@Service
public class AccountTokenService {

    public static final String TOKEN_INVALID = "TOKEN_INVALID";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";

    private final AccountTokenRepository repository;
    private final SecureRandom random = new SecureRandom();

    public AccountTokenService(AccountTokenRepository repository) {
        this.repository = repository;
    }

    /** Crée un jeton et révoque les précédents du même type encore valides pour cet utilisateur. */
    @Transactional
    public String issue(UUID userId, UUID tenantId, AccountTokenKind kind, UUID createdByUserId) {
        Instant now = Instant.now();
        for (AccountToken previous : repository.findByUserIdAndKindAndUsedAtIsNull(userId, kind)) {
            previous.setUsedAt(now);
            repository.save(previous);
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        AccountToken token = new AccountToken();
        token.setUserId(userId);
        token.setTenantId(tenantId);
        token.setKind(kind);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(now.plus(kind.validity()));
        token.setCreatedByUserId(createdByUserId);
        repository.save(token);
        return raw;
    }

    /** Jeton encore utilisable (sans le consommer) ; 400 TOKEN_INVALID / TOKEN_EXPIRED sinon. */
    @Transactional(readOnly = true)
    public AccountToken requireUsable(String raw, AccountTokenKind expectedKind) {
        if (raw == null || raw.isBlank()) {
            throw invalid();
        }
        AccountToken token = repository.findByTokenHash(hash(raw.trim())).orElseThrow(AccountTokenService::invalid);
        if (expectedKind != null && token.getKind() != expectedKind) {
            throw invalid();
        }
        if (token.getUsedAt() != null) {
            throw invalid();
        }
        if (!token.getExpiresAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, TOKEN_EXPIRED,
                    "Ce lien a expiré. Demandez-en un nouveau.");
        }
        return token;
    }

    /** Vérifie puis marque le jeton comme utilisé. */
    @Transactional
    public AccountToken consume(String raw, AccountTokenKind expectedKind) {
        AccountToken token = requireUsable(raw, expectedKind);
        token.setUsedAt(Instant.now());
        return repository.save(token);
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, TOKEN_INVALID,
                "Ce lien est invalide ou a déjà été utilisé.");
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
