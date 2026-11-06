package com.nerd.erp.infrastructure.mail;

import com.nerd.erp.domain.mail.SmtpSecurity;
import com.nerd.erp.domain.mail.WorkspaceMailSettings;
import com.nerd.erp.infrastructure.config.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Construit les {@link JavaMailSenderImpl} : un pour la boîte plateforme, un par workspace (mis en cache
 * et recréé dès que les paramètres changent).
 */
@Component
public class SmtpSenderFactory {

    /** Identité d'expédition résolue : transport + en-têtes From / Reply-To. */
    public record ResolvedSender(JavaMailSenderImpl sender, String fromEmail, String fromName, String replyTo,
                                 String source) {
        public static final String WORKSPACE = "WORKSPACE";
        public static final String PLATFORM = "PLATFORM";
    }

    private record CacheEntry(long version, JavaMailSenderImpl sender) {
    }

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int IO_TIMEOUT_MS = 20_000;

    private final MailProperties properties;
    private final SecretCipher cipher;
    private final Map<UUID, CacheEntry> workspaceSenders = new ConcurrentHashMap<>();
    private volatile JavaMailSenderImpl platformSender;

    public SmtpSenderFactory(MailProperties properties, SecretCipher cipher) {
        this.properties = properties;
        this.cipher = cipher;
    }

    public ResolvedSender forWorkspace(WorkspaceMailSettings settings) {
        long version = settings.getUpdatedAt() == null ? 0 : settings.getUpdatedAt().toEpochMilli();
        CacheEntry entry = workspaceSenders.compute(settings.getWorkspaceId(), (k, existing) ->
                existing != null && existing.version() == version
                        ? existing
                        : new CacheEntry(version, build(settings.getHost(), settings.getPort(), settings.getSecurity(),
                        settings.getUsername(), cipher.decrypt(settings.getPasswordEncrypted()))));
        return new ResolvedSender(entry.sender(), settings.getFromEmail(), settings.getFromName(),
                settings.getReplyTo(), ResolvedSender.WORKSPACE);
    }

    /** Sender à usage unique pour tester des paramètres non encore enregistrés. */
    public JavaMailSenderImpl transientSender(String host, int port, SmtpSecurity security, String username,
                                              String password) {
        return build(host, port, security, username, password);
    }

    public ResolvedSender platform() {
        if (!properties.platformSenderConfigured()) {
            return null;
        }
        JavaMailSenderImpl sender = platformSender;
        if (sender == null) {
            synchronized (this) {
                if (platformSender == null) {
                    platformSender = build(properties.host(), properties.port(),
                            properties.security() == null ? SmtpSecurity.STARTTLS : properties.security(),
                            properties.username(), properties.password());
                }
                sender = platformSender;
            }
        }
        return new ResolvedSender(sender, properties.fromEmail(), properties.fromName(), null,
                ResolvedSender.PLATFORM);
    }

    public void evict(UUID workspaceId) {
        workspaceSenders.remove(workspaceId);
    }

    private static JavaMailSenderImpl build(String host, int port, SmtpSecurity security, String username,
                                            String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setDefaultEncoding("UTF-8");
        boolean auth = username != null && !username.isBlank();
        if (auth) {
            sender.setUsername(username);
            sender.setPassword(password);
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.connectiontimeout", String.valueOf(CONNECT_TIMEOUT_MS));
        props.put("mail.smtp.timeout", String.valueOf(IO_TIMEOUT_MS));
        props.put("mail.smtp.writetimeout", String.valueOf(IO_TIMEOUT_MS));
        switch (security) {
            case STARTTLS -> {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
            case SSL -> {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case NONE -> {
                // serveur de test local uniquement
            }
        }
        return sender;
    }
}
