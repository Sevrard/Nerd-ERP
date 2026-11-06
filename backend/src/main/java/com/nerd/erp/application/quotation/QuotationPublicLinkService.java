package com.nerd.erp.application.quotation;

import com.nerd.erp.application.mail.MailService;
import com.nerd.erp.domain.quotation.Quotation;
import com.nerd.erp.infrastructure.persistence.quotation.QuotationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Lien public d'acceptation d'un devis : jeton aléatoire 256 bits dont seule l'empreinte SHA-256
 * est stockée. Chaque envoi par e-mail ré-émet un jeton (le précédent est invalidé) ; la décision
 * du client ou un changement de statut le révoque.
 */
@Service
public class QuotationPublicLinkService {

    private final QuotationRepository quotationRepository;
    private final MailService mailService;
    private final SecureRandom random = new SecureRandom();

    public QuotationPublicLinkService(QuotationRepository quotationRepository, MailService mailService) {
        this.quotationRepository = quotationRepository;
        this.mailService = mailService;
    }

    /** Génère un nouveau jeton, le stocke haché et retourne l'URL publique complète. */
    @Transactional
    public String issueLink(Quotation quotation) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        quotation.setPublicTokenHash(hash(raw));
        quotationRepository.save(quotation);
        return publicUrl(raw);
    }

    public String publicUrl(String rawToken) {
        return mailService.appBaseUrl() + "/q/" + rawToken;
    }

    @Transactional(readOnly = true)
    public Optional<Quotation> findByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 128) {
            return Optional.empty();
        }
        return quotationRepository.findByPublicTokenHash(hash(rawToken.trim()));
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
