package com.nerd.erp.infrastructure.config.mail;

import com.nerd.erp.domain.mail.SmtpSecurity;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Boîte « plateforme » (éditeur) et paramètres transverses d'e-mail. Les secrets viennent de
 * {@code application-local.yml} ou de variables d'environnement, jamais du dépôt.
 */
@ConfigurationProperties(prefix = "nerd.mail")
public record MailProperties(
        boolean enabled,
        String host,
        int port,
        String username,
        String password,
        SmtpSecurity security,
        String fromEmail,
        String fromName,
        String secretsKey,
        String appBaseUrl
) {
    public boolean platformSenderConfigured() {
        return enabled && host != null && !host.isBlank() && fromEmail != null && !fromEmail.isBlank();
    }
}
