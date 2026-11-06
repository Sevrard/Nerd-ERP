/**
 * Textes des CGU / CGV. La version doit rester alignée avec `TermsPolicy.CURRENT_VERSION` côté backend :
 * toute évolution substantielle des CGU implique d'incrémenter les deux pour redemander l'acceptation.
 * Les mentions « [à compléter] » sont à renseigner par l'éditeur avant mise en production.
 */
export const TERMS_VERSION = '2026-01';

export const LEGAL_EDITOR = {
  name: 'Nerd Group [forme juridique et capital à compléter]',
  address: '[adresse du siège à compléter]',
  registration: 'SIREN [à compléter]',
  email: 'contact@nerd-group.fr',
} as const;

export interface LegalSection {
  title: string;
  paragraphs: string[];
}

export const CGU_SECTIONS: LegalSection[] = [
  {
    title: 'Objet',
    paragraphs: [
      `Les présentes conditions générales d’utilisation (CGU) régissent l’accès et l’usage du logiciel Nerd ERP, service en ligne de gestion commerciale (clients, catalogue, devis, factures, paiements) édité par ${LEGAL_EDITOR.name}.`,
      'Toute connexion au service vaut acceptation pleine et entière des présentes CGU par l’utilisateur et par le client (l’entité titulaire du compte) qu’il représente.',
    ],
  },
  {
    title: 'Accès au service',
    paragraphs: [
      'L’accès s’effectue par identifiant de compte, adresse e-mail et mot de passe personnels. L’utilisateur s’engage à conserver ses identifiants confidentiels et à signaler sans délai tout usage non autorisé.',
      'L’éditeur met en œuvre des moyens raisonnables pour assurer la disponibilité du service, sans garantie de continuité. Des interruptions pour maintenance peuvent survenir ; elles sont, dans la mesure du possible, annoncées à l’avance.',
    ],
  },
  {
    title: 'Comptes, rôles et responsabilités',
    paragraphs: [
      'Le titulaire du compte (rôle Admin) est responsable des utilisateurs qu’il invite, des droits qu’il leur attribue et des données saisies dans son espace.',
      'L’utilisateur s’interdit tout usage contraire à la loi, à l’ordre public ou aux droits de tiers, ainsi que toute tentative d’accès à des données d’autres clients, d’altération ou de surcharge du service.',
    ],
  },
  {
    title: 'Données et confidentialité',
    paragraphs: [
      'Les données saisies (clients, documents commerciaux, pièces jointes) restent la propriété du client. L’éditeur n’y accède qu’aux fins d’exploitation, de support et de sécurité du service.',
      'Les données à caractère personnel sont traitées conformément au RGPD. Le client est responsable de traitement pour les données de ses propres clients ; l’éditeur agit en qualité de sous-traitant. Les personnes concernées peuvent exercer leurs droits auprès de ' +
        LEGAL_EDITOR.email +
        '.',
      'Des traitements automatisés (extraction de données de factures PDF par un service d’intelligence artificielle tiers) peuvent être activés par le client ; les documents transmis à cette fin le sont uniquement pour la durée du traitement.',
    ],
  },
  {
    title: 'Documents commerciaux',
    paragraphs: [
      'Les devis, factures et avoirs générés par le service sont établis à partir des informations fournies par le client (mentions légales, taux de TVA, coordonnées bancaires). Le client demeure seul responsable de leur exactitude et de leur conformité aux obligations comptables et fiscales qui lui incombent.',
      'Les factures validées ne sont plus modifiables ; les corrections passent par l’émission d’un avoir, conformément à la réglementation applicable.',
    ],
  },
  {
    title: 'Propriété intellectuelle',
    paragraphs: [
      'Le logiciel, son interface, ses marques et sa documentation sont protégés. Le client bénéficie d’un droit d’usage personnel, non exclusif et non cessible, limité à la durée de son abonnement.',
    ],
  },
  {
    title: 'Responsabilité',
    paragraphs: [
      'L’éditeur est tenu d’une obligation de moyens. Sa responsabilité ne saurait être engagée en cas de perte de données imputable au client, d’indisponibilité de services tiers (messagerie, IA, hébergement) ou de force majeure, ni pour les dommages indirects (perte de chiffre d’affaires, de clientèle ou d’image).',
    ],
  },
  {
    title: 'Évolution des CGU',
    paragraphs: [
      'L’éditeur peut faire évoluer les présentes CGU. Toute nouvelle version est notifiée lors de la connexion et doit être acceptée pour poursuivre l’utilisation du service. La version applicable est identifiée par son numéro, indiqué en tête du document.',
    ],
  },
  {
    title: 'Droit applicable',
    paragraphs: [
      'Les présentes CGU sont soumises au droit français. En cas de litige et à défaut d’accord amiable, les tribunaux compétents sont ceux du ressort du siège de l’éditeur, sous réserve des règles impératives applicables aux consommateurs.',
    ],
  },
];

export const CGV_SECTIONS: LegalSection[] = [
  {
    title: 'Champ d’application',
    paragraphs: [
      `Les présentes conditions générales de vente (CGV) s’appliquent à toute souscription au service Nerd ERP auprès de ${LEGAL_EDITOR.name} (${LEGAL_EDITOR.registration}). Elles prévalent sur tout document du client, sauf accord écrit contraire.`,
    ],
  },
  {
    title: 'Offre et prix',
    paragraphs: [
      'Le service est proposé par abonnement, selon les modules activés sur le compte du client (socle commercial, maintenance, import de factures, etc.). Les prix sont exprimés en euros hors taxes et détaillés dans le devis ou le bon de commande accepté par le client. [grille tarifaire à compléter]',
      'L’éditeur peut réviser ses prix ; toute révision est notifiée au moins 30 jours avant son application et ne concerne que la période d’abonnement suivante.',
    ],
  },
  {
    title: 'Durée et résiliation',
    paragraphs: [
      'L’abonnement est conclu pour la durée indiquée au devis, renouvelable tacitement par périodes identiques, sauf dénonciation par l’une des parties au moins 30 jours avant l’échéance.',
      'En cas de manquement grave (défaut de paiement, usage illicite), l’éditeur peut suspendre l’accès après mise en demeure restée sans effet pendant 15 jours.',
    ],
  },
  {
    title: 'Facturation et paiement',
    paragraphs: [
      'Les factures sont émises à l’échéance convenue et payables à 30 jours, par virement bancaire. Tout retard entraîne de plein droit des pénalités au taux légal majoré et l’indemnité forfaitaire de recouvrement de 40 € (art. L441-10 du Code de commerce).',
    ],
  },
  {
    title: 'Mise à disposition et support',
    paragraphs: [
      'Le service est accessible en ligne 24 h/24, hors maintenance. Le support est assuré par e-mail à ' +
        LEGAL_EDITOR.email +
        ' les jours ouvrés. [niveaux de service / délais de réponse à compléter]',
    ],
  },
  {
    title: 'Données et réversibilité',
    paragraphs: [
      'À la fin du contrat, le client peut exporter ses données (clients, produits, factures) aux formats proposés par le service pendant 60 jours ; passé ce délai, les données sont supprimées, sous réserve des obligations légales de conservation.',
    ],
  },
  {
    title: 'Responsabilité',
    paragraphs: [
      'La responsabilité de l’éditeur, toutes causes confondues, est limitée au montant des sommes effectivement versées par le client au cours des douze derniers mois.',
    ],
  },
  {
    title: 'Droit applicable et litiges',
    paragraphs: [
      'Les présentes CGV sont régies par le droit français. Tout litige relève des tribunaux compétents du ressort du siège de l’éditeur, après tentative de résolution amiable.',
    ],
  },
];
