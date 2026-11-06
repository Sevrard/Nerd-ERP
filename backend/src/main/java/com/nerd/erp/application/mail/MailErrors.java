package com.nerd.erp.application.mail;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/** Traduction des erreurs SMTP en messages lisibles et décision de nouvelle tentative. */
public final class MailErrors {

    private MailErrors() {
    }

    public static boolean isRetryable(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof MailAuthenticationException || t instanceof AuthenticationFailedException
                    || t instanceof SendFailedException) {
                return false;
            }
            if (t instanceof ConnectException || t instanceof SocketTimeoutException
                    || t instanceof UnknownHostException) {
                return true;
            }
            t = t.getCause();
        }
        return e instanceof MailSendException || e instanceof MessagingException;
    }

    public static String describe(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof MailAuthenticationException || t instanceof AuthenticationFailedException) {
                return "Authentification refusée par le serveur SMTP (identifiant ou mot de passe d'application invalide).";
            }
            if (t instanceof UnknownHostException) {
                return "Serveur SMTP introuvable : " + t.getMessage();
            }
            if (t instanceof ConnectException) {
                return "Connexion au serveur SMTP impossible (hôte / port / pare-feu).";
            }
            if (t instanceof SocketTimeoutException) {
                return "Délai dépassé en contactant le serveur SMTP.";
            }
            if (t instanceof SendFailedException) {
                return "Adresse destinataire refusée par le serveur : " + t.getMessage();
            }
            t = t.getCause();
        }
        String msg = e.getMessage();
        return msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg;
    }

    public static String truncate(String value) {
        return truncate(value, 1024);
    }

    public static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
