/**
 * Lance `ng serve` avec une version de Node compatible Angular 21,
 * même si le `node` du PATH est trop ancien (ex. 20.17 via Homebrew).
 */
import { spawn, spawnSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const MIN_20 = [20, 19, 0];
const MIN_22 = [22, 12, 0];
const __dirname = dirname(fileURLToPath(import.meta.url));
const frontendRoot = join(__dirname, '..');
const ngJs = join(frontendRoot, 'node_modules/@angular/cli/bin/ng.js');

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

function versionOk(ver) {
  const p = parse(ver);
  if (!p) return false;
  if (p[0] === 22) return gte(p, MIN_22);
  if (p[0] === 20) return gte(p, MIN_20);
  return p[0] > 22;
}

function nodeVersion(bin) {
  if (!existsSync(bin)) return null;
  const r = spawnSync(bin, ['-v'], { encoding: 'utf8' });
  if (r.status !== 0) return null;
  return (r.stdout || '').trim();
}

function nvmNodeBins() {
  const bins = [];
  try {
    const v = readFileSync(join(frontendRoot, '.nvmrc'), 'utf8').trim().replace(/^v/, '');
    bins.push(join(homedir(), `.nvm/versions/node/v${v}/bin/node`));
  } catch {
    /* pas de .nvmrc */
  }
  const nvmRoot = join(homedir(), '.nvm/versions/node');
  if (existsSync(nvmRoot)) {
    const dirs = readdirSync(nvmRoot).sort().reverse();
    for (const dir of dirs) {
      bins.push(join(nvmRoot, dir, 'bin/node'));
    }
  }
  return [...new Set(bins)];
}

function resolveNodeBin() {
  for (const bin of [process.execPath, ...nvmNodeBins()]) {
    const ver = nodeVersion(bin);
    if (ver && versionOk(ver)) {
      if (bin !== process.execPath) {
        console.log(`Node ${process.version} ignoré → utilisation de ${bin} (${ver})`);
      }
      return bin;
    }
  }
  return null;
}

if (!existsSync(ngJs)) {
  console.error('Angular CLI introuvable. Lance `npm install` dans frontend/.');
  process.exit(1);
}

const nodeBin = resolveNodeBin();
if (!nodeBin) {
  console.error(`
Node ${process.version} est trop ancien pour Angular 21 (minimum : 20.19 ou 22.12).

Installe une version via nvm :
  cd frontend && nvm install && nvm use

Ou mets à jour le Node par défaut de ton Mac (Homebrew, etc.).
`);
  process.exit(1);
}

const child = spawn(nodeBin, [ngJs, 'serve', ...process.argv.slice(2)], {
  cwd: frontendRoot,
  stdio: 'inherit',
  env: process.env,
});

child.on('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
  } else {
    process.exit(code ?? 1);
  }
});
