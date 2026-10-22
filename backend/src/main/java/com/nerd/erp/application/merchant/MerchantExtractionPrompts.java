package com.nerd.erp.application.merchant;

/**
 * Prompts d'extraction IA par slug marchand (source pour seed / mise à jour BDD).
 */
public final class MerchantExtractionPrompts {

    public static final String SLUG_ETSY = "etsy";

    public static final String ETSY = """
            Tu es un parseur de factures expert pour l'ERP de Nerd-Forge / Nerd-Group.
            Analyse le document fourni (reçu de commande Etsy Nerd-Forge) et extrait les données en respectant strictement les repères visuels et textuels suivants :

            1. "merchant": Toujours "Etsy" (ou extrait depuis le bas/haut du document).
            2. "order_number": Situé juste après le texte "Commande n°" en haut à gauche (ex: "3921929504").
            3. "invoice_date": Situé à côté du libellé "Date de la commande" (ex: "24 déc. 2025" à convertir au format "YYYY-MM-DD").
            4. "customer":
               - "full_name": Le nom complet situé sous "Pays de destination" (ex: "Aude Archambault"). Ignore le pseudo entre parenthèses s'il y a lieu.
               - "address": La ligne de rue située sous le nom (ex: "Bordéeneuve").
               - "postal_code": Le code postal (ex: "32220").
               - "city": La ville (ex: "SAINT LOUBE").
               - "country": Le pays (ex: "France").
            5. "items": Liste les articles situés dans la colonne de droite sous "X articles". Pour chaque article, extrais :
               - "description": Le texte principal de l'article (ex: "Susuwatari...").
               - "size": La taille ou le format indiqué sous l'article (ex: "Lot de 3 - Petit", ou null si absent).
               - "color": La couleur ou les options secondaires indiquées sous l'article (ex: "14. Blanc", ou null si absent).
               - "quantity": Le chiffre avant le "x" (ex: 1).
               - "unit_price": Le prix unitaire après le "x" (ex: 13.00).
            6. "shipping_fees": Le montant indiqué en face de "Total des frais de livraison" (ex: 4.00).
            7. "discount_amount": Le montant de la réduction si présent en face de "Réduction de la boutique" (ex: 5.00).
            8. "total_amount": Le montant final en face de "Total de la commande" (ex: 32.00).

            Tu dois retourner un JSON strict sans aucun texte autour, respectant exactement ce format :
            {
              "merchant": "string",
              "order_number": "string",
              "invoice_date": "string",
              "customer": {
                "full_name": "string",
                "address": "string",
                "postal_code": "string",
                "city": "string",
                "country": "string"
              },
              "items": [
                {
                  "description": "string",
                  "size": "string ou null",
                  "color": "string ou null",
                  "quantity": 1,
                  "unit_price": 0.00
                }
              ],
              "shipping_fees": 0.00,
              "discount_amount": 0.00,
              "total_amount": 0.00,
              "currency": "EUR"
            }
            """;

    private MerchantExtractionPrompts() {
    }

    public static String templateForSlug(String slug) {
        if (slug == null) {
            return null;
        }
        return switch (slug) {
            case SLUG_ETSY -> ETSY;
            default -> null;
        };
    }
}
