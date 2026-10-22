#!/usr/bin/env node
/**
 * Renouvelle nerd.etsy.access-token via refresh_token (durée access ~1 h).
 */
import {
  LOCAL_YML_PATH,
  readEtsyFromLocalYml,
  writeEtsyTokensToLocalYml,
} from "./etsy-yaml.mjs";

async function refreshAccessToken({ keystring, sharedSecret, refreshToken }) {
  const body = new URLSearchParams({
    grant_type: "refresh_token",
    client_id: keystring,
    refresh_token: refreshToken,
  });
  const res = await fetch("https://api.etsy.com/v3/public/oauth/token", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      "x-api-key": `${keystring}:${sharedSecret}`,
    },
    body,
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(`Refresh échoué (${res.status}): ${JSON.stringify(json)}`);
  }
  return json;
}

function parseArgs(argv) {
  const opts = {
    keystring: process.env.ETSY_KEYSTRING || process.env.ETSY_API_KEY,
    sharedSecret: process.env.ETSY_SHARED_SECRET,
    refreshToken: process.env.ETSY_REFRESH_TOKEN,
    write: true,
  };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--no-write") opts.write = false;
    else if (a === "--refresh-token" && argv[i + 1]) opts.refreshToken = argv[++i];
    else if (a === "--keystring" && argv[i + 1]) opts.keystring = argv[++i];
    else if (a === "--shared-secret" && argv[i + 1]) opts.sharedSecret = argv[++i];
    else if (a === "--help" || a === "-h") {
      console.log(`Usage: node scripts/etsy-refresh-token.mjs [options]

Renouvelle l'access token Etsy (~1 h) à partir de refresh-token dans application-local.yml.

Options:
  --no-write            Affiche les tokens sans modifier application-local.yml
  --refresh-token <s>   Sinon nerd.etsy.refresh-token / ETSY_REFRESH_TOKEN
  --keystring <s>       Sinon nerd.etsy.api-key
  --shared-secret <s>   Sinon nerd.etsy.shared-secret

Après mise à jour du YAML, redémarrez le backend (mvn spring-boot:run).
`);
      process.exit(0);
    }
  }
  const fromYml = readEtsyFromLocalYml();
  opts.keystring = opts.keystring || fromYml.keystring;
  opts.sharedSecret = opts.sharedSecret || fromYml.sharedSecret;
  opts.refreshToken = opts.refreshToken || fromYml.refreshToken;
  return opts;
}

async function main() {
  const opts = parseArgs(process.argv);
  if (!opts.keystring || !opts.sharedSecret) {
    console.error("Keystring et shared secret requis (section nerd.etsy).");
    process.exit(1);
  }
  if (!opts.refreshToken) {
    console.error(
      "refresh-token manquant. Lancez d'abord node scripts/etsy-oauth.mjs ou ajoutez nerd.etsy.refresh-token."
    );
    process.exit(1);
  }

  const token = await refreshAccessToken({
    keystring: opts.keystring,
    sharedSecret: opts.sharedSecret,
    refreshToken: opts.refreshToken,
  });

  const access = token.access_token;
  const refresh = token.refresh_token || opts.refreshToken;

  console.log("\n=== Access token renouvelé ===\n");
  console.log(`  access-token: ${access}`);
  console.log(`  expire dans : ${token.expires_in ?? "?"} s`);
  if (token.refresh_token) {
    console.log(`  refresh-token: ${refresh} (nouveau jeton enregistré)`);
  }

  if (opts.write) {
    writeEtsyTokensToLocalYml(access, refresh);
    console.log(`\nFichier mis à jour : ${LOCAL_YML_PATH}`);
    console.log("Redémarrez le backend pour prendre en compte le nouvel access-token.\n");
  } else {
    console.log("\n(--no-write : application-local.yml inchangé)\n");
  }
}

main().catch((e) => {
  console.error(e.message || e);
  process.exit(1);
});
