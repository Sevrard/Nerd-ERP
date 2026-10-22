import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export const LOCAL_YML_PATH = path.join(__dirname, "..", "application-local.yml");

export function extractEtsyYamlBlock(text) {
  const lines = text.split("\n");
  const block = [];
  let inEtsy = false;
  for (const line of lines) {
    if (/^  etsy:\s*(\S.*)?$/.test(line)) {
      inEtsy = true;
      continue;
    }
    if (inEtsy) {
      if (/^  [a-zA-Z0-9_-]+:/.test(line)) break;
      if (/^[^\s#]/.test(line)) break;
      block.push(line);
    }
  }
  return block.join("\n");
}

function pickFromBlock(block, key) {
  const m = block.match(new RegExp(`^\\s*${key}:\\s*(.*)$`, "m"));
  if (!m) return undefined;
  const v = m[1].trim();
  if (!v || v.startsWith("#")) return undefined;
  return v.replace(/^["']|["']$/g, "");
}

export function readEtsyFromLocalYml() {
  if (!fs.existsSync(LOCAL_YML_PATH)) {
    return {};
  }
  const text = fs.readFileSync(LOCAL_YML_PATH, "utf8");
  const etsy = extractEtsyYamlBlock(text);
  return {
    keystring: pickFromBlock(etsy, "api-key"),
    sharedSecret: pickFromBlock(etsy, "shared-secret"),
    accessToken: pickFromBlock(etsy, "access-token"),
    refreshToken: pickFromBlock(etsy, "refresh-token"),
    shopId: pickFromBlock(etsy, "shop-id"),
  };
}

/** Met à jour access-token et refresh-token dans la section nerd.etsy. */
export function writeEtsyTokensToLocalYml(accessToken, refreshToken) {
  if (!fs.existsSync(LOCAL_YML_PATH)) {
    throw new Error(`Fichier introuvable : ${LOCAL_YML_PATH}`);
  }
  const lines = fs.readFileSync(LOCAL_YML_PATH, "utf8").split("\n");
  let inEtsy = false;
  let accessDone = false;
  let refreshDone = false;
  let etsyStart = -1;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (/^  etsy:\s*(\S.*)?$/.test(line)) {
      inEtsy = true;
      etsyStart = i;
      continue;
    }
    if (!inEtsy) continue;

    if (/^  [a-zA-Z0-9_-]+:/.test(line) && !/^    /.test(line)) {
      inEtsy = false;
      continue;
    }
    if (/^    access-token:/.test(line)) {
      lines[i] = `    access-token: ${accessToken}`;
      accessDone = true;
    }
    if (/^    refresh-token:/.test(line)) {
      lines[i] = `    refresh-token: ${refreshToken}`;
      refreshDone = true;
    }
  }

  if (!accessDone || !refreshDone) {
    let insertAt = lines.length;
    for (let i = etsyStart + 1; i < lines.length; i++) {
      if (/^  [a-zA-Z0-9_-]+:/.test(lines[i]) && !/^    /.test(lines[i])) {
        insertAt = i;
        break;
      }
    }
    const toInsert = [];
    if (!accessDone) toInsert.push(`    access-token: ${accessToken}`);
    if (!refreshDone) toInsert.push(`    refresh-token: ${refreshToken}`);
    lines.splice(insertAt, 0, ...toInsert);
  }

  fs.writeFileSync(LOCAL_YML_PATH, lines.join("\n"), "utf8");
}
