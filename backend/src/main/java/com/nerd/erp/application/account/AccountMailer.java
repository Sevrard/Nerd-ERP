package com.nerd.erp.application.account;

import com.nerd.erp.application.mail.MailService;
import com.nerd.erp.application.mail.MailTemplates;
import com.nerd.erp.application.mail.OutgoingEmail;
import com.nerd.erp.domain.mail.EmailKind;
import com.nerd.erp.domain.tenancy.AppUser;
import com.nerd.erp.domain.tenancy.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * E-mails de compte (invitation, accès ajouté, mot de passe oublié), toujours émis depuis la boîte
 * plateforme. Chaque méthode indique si l'envoi a pu être programmé : sans boîte plateforme, l'appelant
 * peut remettre le lien à l'administrateur par un autre canal.
 */
@Component
public class AccountMailer {

    private static final Logger log = LoggerFactory.getLogger(AccountMailer.class);
    private static final String FOOTER = "Nerd ERP — cet e-mail a été envoyé automatiquement, merci de ne pas y répondre.";

    private final MailService mailService;

    public AccountMailer(MailService mailService) {
        this.mailService = mailService;
    }

    public String activationUrl(String token) {
        return mailService.appBaseUrl() + "/activate?token=" + token;
    }

    public String resetUrl(String token) {
        return mailService.appBaseUrl() + "/reset-password?token=" + token;
    }

    public String loginUrl() {
        return mailService.appBaseUrl() + "/login";
    }

    public boolean available() {
        return mailService.platformSenderAvailable();
    }

    public boolean sendInvitation(AppUser user, Tenant tenant, String inviterName, String url) {
        String title = "Votre accès à " + tenant.getName();
        String html = MailTemplates.layout(title,
                MailTemplates.paragraph("Bonjour " + user.getDisplayName() + ",")
                        + MailTemplates.paragraph(inviterName + " vous invite à rejoindre l'espace « " + tenant.getName()
                        + " » sur Nerd ERP. Choisissez votre mot de passe pour activer votre compte :")
                        + MailTemplates.button("Activer mon compte", url)
                        + MailTemplates.muted("Ce lien est valable 7 jours. Identifiant : " + user.getEmail()
                        + " — compte : " + tenant.getSlug() + "."),
                FOOTER);
        String text = "Bonjour " + user.getDisplayName() + ",\n\n" + inviterName + " vous invite à rejoindre « "
                + tenant.getName() + " » sur Nerd ERP.\nActivez votre compte (lien valable 7 jours) : " + url
                + "\n\nIdentifiant : " + user.getEmail() + " — compte : " + tenant.getSlug() + "\n";
        return send(tenant, user.getEmail(), title, html, text);
    }

    public boolean sendAccessGranted(AppUser user, Tenant tenant, String inviterName) {
        String title = "Nouvel accès : " + tenant.getName();
        String url = loginUrl();
        String html = MailTemplates.layout(title,
                MailTemplates.paragraph("Bonjour " + user.getDisplayName() + ",")
                        + MailTemplates.paragraph(inviterName + " vous a donné accès à l'espace « " + tenant.getName()
                        + " ». Connectez-vous avec votre mot de passe habituel et le compte « " + tenant.getSlug() + " ».")
                        + MailTemplates.button("Se connecter", url),
                FOOTER);
        String text = "Bonjour " + user.getDisplayName() + ",\n\n" + inviterName + " vous a donné accès à « "
                + tenant.getName() + " » (compte : " + tenant.getSlug() + ").\nConnexion : " + url + "\n";
        return send(tenant, user.getEmail(), title, html, text);
    }

    public boolean sendPasswordReset(AppUser user, Tenant logTenant, String url) {
        String title = "Réinitialisation de votre mot de passe";
        String html = MailTemplates.layout(title,
                MailTemplates.paragraph("Bonjour " + user.getDisplayName() + ",")
                        + MailTemplates.paragraph("Une réinitialisation du mot de passe de votre compte Nerd ERP ("
                        + user.getEmail() + ") a été demandée. Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail.")
                        + MailTemplates.button("Choisir un nouveau mot de passe", url)
                        + MailTemplates.muted("Ce lien est valable 1 heure et ne peut être utilisé qu'une fois."),
                FOOTER);
        String text = "Bonjour " + user.getDisplayName() + ",\n\nPour choisir un nouveau mot de passe (lien valable 1 heure) : "
                + url + "\n\nSi vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail.\n";
        return send(logTenant, user.getEmail(), title, html, text);
    }

    private boolean send(Tenant tenant, String to, String subject, String html, String text) {
        if (!available()) {
            log.warn("Boîte plateforme non configurée : e-mail de compte « {} » pour {} non envoyé", subject, to);
            return false;
        }
        mailService.send(OutgoingEmail.of(EmailKind.ACCOUNT, tenant.getId(), null, to, subject, html, text));
        return true;
    }
}
