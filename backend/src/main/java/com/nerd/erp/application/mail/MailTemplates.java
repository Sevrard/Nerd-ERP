package com.nerd.erp.application.mail;

import org.springframework.web.util.HtmlUtils;

/**
 * Gabarits HTML sobres, compatibles avec les clients mail (tableaux, styles inline). Toute valeur
 * dynamique passe par {@link #esc(String)}.
 */
public final class MailTemplates {

    private MailTemplates() {
    }

    public static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    /** Page complète : titre, blocs de contenu HTML déjà échappé, pied de page (nom de l'émetteur). */
    public static String layout(String title, String bodyHtml, String footer) {
        return """
                <!doctype html>
                <html lang="fr"><head><meta charset="utf-8"><title>%s</title></head>
                <body style="margin:0;padding:0;background:#f4f5f7;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#1f2933;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f4f5f7;padding:24px 0;">
                <tr><td align="center">
                <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="max-width:600px;width:100%%;background:#ffffff;border-radius:12px;border:1px solid #e4e7eb;">
                <tr><td style="padding:28px 32px 8px 32px;font-size:20px;font-weight:600;">%s</td></tr>
                <tr><td style="padding:8px 32px 28px 32px;font-size:15px;line-height:1.55;">%s</td></tr>
                </table>
                <p style="max-width:600px;margin:16px 0 0 0;font-size:12px;color:#7b8794;">%s</p>
                </td></tr></table>
                </body></html>
                """.formatted(esc(title), esc(title), bodyHtml, esc(footer));
    }

    public static String paragraph(String text) {
        return "<p style=\"margin:0 0 14px 0;\">" + esc(text) + "</p>";
    }

    /** Paragraphe dont le contenu HTML est déjà échappé par l'appelant. */
    public static String rawParagraph(String html) {
        return "<p style=\"margin:0 0 14px 0;\">" + html + "</p>";
    }

    public static String button(String label, String url) {
        return "<p style=\"margin:22px 0;\"><a href=\"" + esc(url) + "\" style=\"display:inline-block;background:#0f766e;color:#ffffff;"
                + "text-decoration:none;padding:11px 20px;border-radius:8px;font-weight:600;\">" + esc(label) + "</a></p>"
                + "<p style=\"margin:0 0 14px 0;font-size:12px;color:#7b8794;\">Si le bouton ne fonctionne pas, copiez ce lien : "
                + esc(url) + "</p>";
    }

    public static String muted(String text) {
        return "<p style=\"margin:0;font-size:13px;color:#7b8794;\">" + esc(text) + "</p>";
    }
}
