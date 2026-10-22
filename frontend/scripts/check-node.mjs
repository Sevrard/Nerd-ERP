/** Angular 21 CLI exige Node >= 20.19 ou >= 22.12 */
const MIN_20 = [20, 19, 0];
const MIN_22 = [22, 12, 0];

function parse(v) {
  const m = /^v?(\d+)\.(\d+)\.(\d+)/.exec(String(v).trim());
  if (!m) return null;
  return [Number(m[1]), Number(m[2]), Number(m[3])];
}

function gte(a, b) {
  for (let i = 0; i < 3; i++) {
    if (a[i] > b[i]) return true;
    if (a[i] < b[i]) return false;
  }
  return true;
}

function ok(ver) {
  const p = parse(ver);
  if (!p) return false;
  if (p[0] === 22) return gte(p, MIN_22);
  if (p[0] === 20) return gte(p, MIN_20);
  return p[0] > 22;
}

const current = process.version;
if (ok(current)) {
  process.exit(0);
}

console.error(`
Node ${current} est trop ancien pour Angular 21 (minimum : 20.19 ou 22.12).

Avec nvm (déjà installé chez toi) :

  cd frontend
  nvm install    # lit .nvmrc → 20.19.4
  nvm use
  npm start

Pour fixer la version par défaut : nvm alias default 20.19.4
`);
process.exit(1);
