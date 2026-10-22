#!/usr/bin/env node
/**
 * OAuth 2.0 Authorization Code + PKCE pour Etsy Open API v3.
 * Usage : voir backend/README.md (section Etsy OAuth).
 */
import crypto from "node:crypto";
import http from "node:http";
import { readEtsyFromLocalYml } from "./etsy-yaml.mjs";
const DEFAULT_PORT = 3003;
const DEFAULT_REDIRECT_PATH = "/oauth/redirect";
const DEFAULT_SCOPES = "transactions_r listings_r shops_r email_r";

function base64UrlEncode(buffer) {
  return buffer
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=/g, "");
}

function createPkcePair() {
  const verifier = base64UrlEncode(crypto.randomBytes(32));
  const challenge = base64UrlEncode(crypto.createHash("sha256").update(verifier).digest());
  return { verifier, challenge };
}

function parseArgs(argv) {
  const opts = {
    port: Number(process.env.ETSY_OAUTH_PORT || DEFAULT_PORT),
    scopes: process.env.ETSY_OAUTH_SCOPES || DEFAULT_SCOPES,
    keystring: process.env.ETSY_KEYSTRING || process.env.ETSY_API_KEY,
    sharedSecret: process.env.ETSY_SHARED_SECRET,
  };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--port" && argv[i + 1]) opts.port = Number(argv[++i]);
    else if (a === "--scopes" && argv[i + 1]) opts.scopes = argv[++i];
    else if (a === "--keystring" && argv[i + 1]) opts.keystring = argv[++i];
    else if (a === "--shared-secret" && argv[i + 1]) opts.sharedSecret = argv[++i];
    else if (a === "--help" || a === "-h") {
      console.log(`Usage: node scripts/etsy-oauth.mjs [options]

Options:
  --keystring <s>       Keystring Etsy (sinon ETSY_KEYSTRING / api-key dans application-local.yml)
  --shared-secret <s>   Shared secret (sinon ETSY_SHARED_SECRET / application-local.yml)
  --port <n>            Port local (défaut ${DEFAULT_PORT}, ou ETSY_OAUTH_PORT)
  --scopes <s>          Scopes séparés par espaces (défaut: ${DEFAULT_SCOPES})

Prérequis Etsy Developers : redirect URI exacte
  http://localhost:<port>${DEFAULT_REDIRECT_PATH}
`);
      process.exit(0);
    }
  }
  const fromYml = readEtsyFromLocalYml();
  opts.keystring = opts.keystring || fromYml.keystring;
  opts.sharedSecret = opts.sharedSecret || fromYml.sharedSecret;
  opts.redirectUri = `http://localhost:${opts.port}${DEFAULT_REDIRECT_PATH}`;
  return opts;
}

async function exchangeCode({ keystring, sharedSecret, redirectUri, code, codeVerifier }) {
  const body = new URLSearchParams({
    grant_type: "authorization_code",
    client_id: keystring,
    redirect_uri: redirectUri,
    code,
    code_verifier: codeVerifier,
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
    throw new Error(
      `Échange token échoué (${res.status}): ${JSON.stringify(json)}`
    );
  }
  return json;
}

/** Scopes Etsy : liste séparée par espaces, sans doublons (encodage URL via URLSearchParams). */
function normalizeScopes(scopes) {
  return [...new Set(String(scopes).trim().split(/\s+/).filter(Boolean))].join(" ");
}

function buildAuthorizeUrl({ keystring, redirectUri, scopes, state, codeChallenge }) {
  const scope = normalizeScopes(scopes);
  const q = new URLSearchParams({
    response_type: "code",
    client_id: keystring,
    redirect_uri: redirectUri,
    scope,
    state,
    code_challenge: codeChallenge,
    code_challenge_method: "S256",
  });
  return `https://www.etsy.com/oauth/connect?${q.toString()}`;
}

function main() {
  const opts = parseArgs(process.argv);
  if (!opts.keystring || !opts.sharedSecret) {
    console.error(
      "Keystring et shared secret requis (application-local.yml ou ETSY_KEYSTRING + ETSY_SHARED_SECRET)."
    );
    process.exit(1);
  }

  const { verifier, challenge } = createPkcePair();
  const state = base64UrlEncode(crypto.randomBytes(16));
  const authUrl = buildAuthorizeUrl({
    keystring: opts.keystring,
    redirectUri: opts.redirectUri,
    scopes: opts.scopes,
    state,
    codeChallenge: challenge,
  });

  const server = http.createServer(async (req, res) => {
    const url = new URL(req.url, `http://localhost:${opts.port}`);
    if (url.pathname !== DEFAULT_REDIRECT_PATH) {
      res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      res.end("Not found");
      return;
    }

    const error = url.searchParams.get("error");
    if (error) {
      res.writeHead(400, { "Content-Type": "text/plain; charset=utf-8" });
      res.end(`Etsy a refusé : ${error}\n${url.searchParams.get("error_description") || ""}`);
      server.close();
      process.exit(1);
    }

    const returnedState = url.searchParams.get("state");
    if (returnedState !== state) {
      res.writeHead(400, { "Content-Type": "text/plain; charset=utf-8" });
      res.end("State OAuth invalide (CSRF). Relancez le script.");
      server.close();
      process.exit(1);
    }

    const code = url.searchParams.get("code");
    if (!code) {
      res.writeHead(400, { "Content-Type": "text/plain; charset=utf-8" });
      res.end("Paramètre code manquant.");
      server.close();
      process.exit(1);
    }

    try {
      const token = await exchangeCode({
        keystring: opts.keystring,
        sharedSecret: opts.sharedSecret,
        redirectUri: opts.redirectUri,
        code,
        codeVerifier: verifier,
      });
      res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
      res.end(
        "<!DOCTYPE html><html><body><h1>Token reçu</h1><p>Retournez au terminal pour copier access_token dans application-local.yml.</p></body></html>"
      );

      console.log("\n=== Succès OAuth Etsy ===\n");
      console.log("Collez dans backend/application-local.yml (2 lignes distinctes) :\n");
      console.log(`  access-token: ${token.access_token}`);
      console.log("  ^^^ Bearer API (~1 h) — celui-ci dans access-token, PAS le refresh\n");
      if (token.refresh_token) {
        console.log(`  refresh-token: ${token.refresh_token}`);
        console.log("  ^^^ Renouvellement (~90 j) — ne pas mettre dans access-token\n");
      }
      console.log(`\nScopes accordés : ${token.scope || "(non renvoyé)"}`);
      console.log(`Expire dans : ${token.expires_in ?? "?"} s\n`);
      server.close();
      process.exit(0);
    } catch (e) {
      res.writeHead(500, { "Content-Type": "text/plain; charset=utf-8" });
      res.end(String(e.message));
      console.error(e);
      server.close();
      process.exit(1);
    }
  });

  server.listen(opts.port, "127.0.0.1", () => {
    console.log("=== Etsy OAuth (dev) ===\n");
    console.log(`Keystring Etsy (client_id) : ${opts.keystring}`);
    if (opts.keystring.startsWith("AQ.")) {
      console.warn(
        "⚠ Ce client_id ressemble à une clé Gemini, pas au keystring Etsy (nerd.etsy.api-key).\n"
      );
    }
    console.log("");
    console.log("1. Etsy Developers → app nerd-erp → Redirect URI (doit correspondre exactement) :");
    console.log(`   ${opts.redirectUri}\n`);
    console.log("2. Ouvrez ce lien dans un navigateur (compte propriétaire de la boutique) :\n");
    console.log(authUrl);
    console.log("\n3. Acceptez les autorisations → redirection automatique → token affiché ici.\n");
    console.log(`Serveur en écoute sur 127.0.0.1:${opts.port} …\n`);
  });

  server.on("error", (err) => {
    if (err.code === "EADDRINUSE") {
      console.error(`Port ${opts.port} occupé. Utilisez --port 3004 ou libérez le port.`);
    } else {
      console.error(err);
    }
    process.exit(1);
  });
}

main();
