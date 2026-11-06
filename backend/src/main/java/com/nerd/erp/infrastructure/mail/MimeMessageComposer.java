package com.nerd.erp.infrastructure.mail;

import com.nerd.erp.application.mail.OutgoingEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;

/** Construit et envoie un {@link MimeMessage} multipart (texte + HTML + pièces jointes). */
public final class MimeMessageComposer {

    private MimeMessageComposer() {
    }

    public static void send(SmtpSenderFactory.ResolvedSender resolved, OutgoingEmail email)
            throws MessagingException, UnsupportedEncodingException {
        JavaMailSenderImpl sender = resolved.sender();
        MimeMessage message = sender.createMimeMessage();
        // Multipart systématique : alternative texte/HTML, plus pièces jointes éventuelles.
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        if (resolved.fromName() != null && !resolved.fromName().isBlank()) {
            helper.setFrom(resolved.fromEmail(), resolved.fromName());
        } else {
            helper.setFrom(resolved.fromEmail());
        }
        if (resolved.replyTo() != null && !resolved.replyTo().isBlank()) {
            helper.setReplyTo(resolved.replyTo());
        }
        helper.setTo(email.to());
        helper.setSubject(email.subject());
        String text = email.text() == null || email.text().isBlank() ? stripHtml(email.html()) : email.text();
        helper.setText(text, email.html());
        for (OutgoingEmail.Attachment attachment : email.attachments()) {
            helper.addAttachment(attachment.filename(), new ByteArrayResource(attachment.content()),
                    attachment.contentType());
        }
        sender.send(message);
    }

    private static String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .trim();
    }
}
