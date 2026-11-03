#!/usr/bin/env node
/**
 * Importe un catalogue JSON (prestations) dans un workspace via l'API REST.
 *
 * Usage:
 *   node scripts/import-catalog-products.mjs [--workspace-slug nerd-group] [--file scripts/data/nerd-group-prestations.json]
 *
 * Variables d'environnement optionnelles:
 *   NERD_API_BASE, NERD_EMAIL, NERD_PASSWORD, NERD_TENANT_SLUG
 */
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

const API_BASE = process.env.NERD_API_BASE ?? 'http://127.0.0.1:8080/api/v1';
const EMAIL = process.env.NERD_EMAIL ?? 'admin@nerd.local';
const PASSWORD = process.env.NERD_PASSWORD ?? 'Admin123!';
const TENANT_SLUG = process.env.NERD_TENANT_SLUG ?? 'nerd-group';

function parseArgs() {
  const args = process.argv.slice(2);
  let workspaceSlug = 'nerd-group';
  let file = join(__dirname, 'data/nerd-group-prestations.json');
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--workspace-slug' && args[i + 1]) workspaceSlug = args[++i];
    else if (args[i] === '--file' && args[i + 1]) file = args[++i];
  }
  return { workspaceSlug, file };
}

function buildDescription(category, item) {
  const lines = [
    `Catégorie : ${category.label} (${category.code})`,
    `Facturation : ${item.billing_type}${item.billing_cycle ? ` / ${item.billing_cycle}` : ''}`,
    `Quantité indicative : ${item.default_quantity} ${item.unit_label}`,
    item.is_optional_default ? 'Optionnel par défaut dans les devis.' : '',
    '',
    item.description ?? '',
  ];
  return lines.filter(Boolean).join('\n');
}

async function api(method, path, token, body) {
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  if (!res.ok) {
    throw new Error(`${method} ${path} → ${res.status}: ${json?.message ?? text}`);
  }
  return json;
}

async function main() {
  const { workspaceSlug, file } = parseArgs();
  const catalog = JSON.parse(readFileSync(file, 'utf8'));
  const vatRate = catalog.vat_rate_default ?? 20;

  console.log('Connexion…');
  const login = await api('POST', '/auth/login', null, {
    email: EMAIL,
    password: PASSWORD,
    tenantSlug: TENANT_SLUG,
  });
  const token = login.accessToken;

  const workspaces = await api('GET', '/workspaces', token);
  const ws = workspaces.find((w) => w.slug === workspaceSlug);
  if (!ws) {
    throw new Error(`Workspace slug "${workspaceSlug}" introuvable. Disponibles: ${workspaces.map((w) => w.slug).join(', ')}`);
  }
  console.log(`Workspace : ${ws.name} (${ws.id})`);

  const existingCategories = await api('GET', `/product-categories?workspaceId=${ws.id}`, token);
  const categoryByCode = new Map(
    (existingCategories ?? []).map((c) => [c.code.toUpperCase(), c]),
  );

  for (const category of catalog.categories ?? []) {
    const catPayload = {
      workspaceId: ws.id,
      code: category.code,
      label: category.label,
      sortOrder: (catalog.categories ?? []).indexOf(category) + 1,
    };
    let cat = categoryByCode.get(category.code.toUpperCase());
    if (cat) {
      cat = await api('PUT', `/product-categories/${cat.id}`, token, catPayload);
    } else {
      cat = await api('POST', '/product-categories', token, catPayload);
      categoryByCode.set(category.code.toUpperCase(), cat);
    }
    console.log(`📁 ${category.code}`);
  }

  const existing = await api('GET', `/products?workspaceId=${ws.id}&activeOnly=false&size=500`, token);
  const byRef = new Map((existing.content ?? []).map((p) => [p.referenceCode.toUpperCase(), p]));

  let created = 0;
  let updated = 0;
  let skipped = 0;

  for (const category of catalog.categories ?? []) {
    const cat = categoryByCode.get(category.code.toUpperCase());
    for (const item of category.items ?? []) {
      const payload = {
        workspaceId: ws.id,
        referenceCode: item.sku,
        name: item.label,
        description: buildDescription(category, item),
        unitPriceHt: item.unit_price_ht,
        vatRate,
        active: true,
        categoryId: cat?.id ?? null,
      };
      const prev = byRef.get(item.sku.toUpperCase());
      if (prev) {
        await api('PUT', `/products/${prev.id}`, token, payload);
        updated++;
        console.log(`↻ ${item.sku}`);
      } else {
        await api('POST', '/products', token, payload);
        created++;
        console.log(`+ ${item.sku}`);
      }
    }
  }

  console.log(`\nTerminé : ${created} créé(s), ${updated} mis à jour, ${skipped} ignoré(s).`);
}

main().catch((e) => {
  console.error(e.message ?? e);
  process.exit(1);
});
