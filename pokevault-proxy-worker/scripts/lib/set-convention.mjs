// Come e' scritto un set che c'e' gia': padding del numero, estensione del
// card_id, layout della chiave R2. Serve a chi aggiunge carte a un set
// esistente (topup-set-from-tcgdex.mjs, topup-set-from-official.mjs), perche'
// i set ereditati dal catalogo storico e quelli ingeriti da TCGdex scrivono le
// stesse cose in due grafie diverse e un set non deve mai finire scritto in
// entrambe.

import { mkdir, readFile, rm } from 'node:fs/promises';
import path from 'node:path';

// "048" e "48" sono la stessa carta: e' la chiave con cui si decide chi manca.
// Solo per il confronto, pero': il numero che si SCRIVE non passa di qui se non
// e' numerico, o le varianti degli XY ("24a") finirebbero in catalogo come
// "24A" -- e quel numero l'utente se lo vede stampato sotto la carta.
export function normalizeNumber(value) {
  const raw = String(value).trim();
  return /^\d+$/.test(raw) ? String(parseInt(raw, 10)) : raw.toUpperCase();
}

export function detectConvention(existingRows, setCodeUpper) {
  const numeric = existingRows.filter((r) => /^\d+$/.test(String(r.card_number ?? '').trim()));
  const padded = numeric.filter((r) => {
    const n = String(r.card_number).trim();
    return n.length > 1 && n.startsWith('0');
  });
  const padWidth = padded.length > numeric.length / 2
    ? Math.max(...padded.map((r) => String(r.card_number).trim().length))
    : 0;
  const tally = new Map();
  for (const row of existingRows) {
    const ext = (/\.([a-z0-9]+)$/i.exec(String(row.card_id ?? '')) ?? [])[1]?.toLowerCase();
    if (ext) tally.set(ext, (tally.get(ext) ?? 0) + 1);
  }
  const cardIdExt = [...tally.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] ?? 'webp';
  // I promo hanno numeri non numerici ("SWSH026", "SM01") e gli XY le varianti
  // ("24a"): li' si tiene la grafia della sorgente tale e quale.
  const formatNumber = (localId) => {
    const raw = String(localId).trim();
    if (!/^\d+$/.test(raw)) return raw;
    return padWidth > 0 ? normalizeNumber(raw).padStart(padWidth, '0') : normalizeNumber(raw);
  };
  return {
    padWidth,
    cardIdExt,
    formatNumber,
    formatCardId: (localId) => `${setCodeUpper}_IT_${formatNumber(localId)}.${cardIdExt}`,
  };
}

// Il Worker prova molte chiavi (maiuscolo/minuscolo, con e senza prefisso), ma
// caricare dove il set ha gia' le sue immagini evita di spargere lo stesso set
// su due layout diversi. Si scopre chiedendo a R2 dov'e' una carta che c'e'.
export async function detectR2Layout({ setId, sampleNumber, bucket, tmpDir, runWrangler }) {
  const upper = setId.toUpperCase();
  const lower = setId.toLowerCase();
  const probeFile = path.join(tmpDir, 'layout-probe.bin');
  const layouts = [
    { folder: lower, shape: 'plain' },
    { folder: upper, shape: 'plain' },
    { folder: upper, shape: 'prefixed' },
    { folder: lower, shape: 'prefixed' },
  ];
  await mkdir(tmpDir, { recursive: true });
  for (const layout of layouts) {
    for (const ext of ['webp', 'png']) {
      const name = layout.shape === 'prefixed'
        ? `${layout.folder.toUpperCase()}_IT_${sampleNumber}.${ext}`
        : `${sampleNumber}.${ext}`;
      const key = `it/${layout.folder}/${name}`;
      await rm(probeFile, { force: true });
      try {
        await runWrangler(['r2', 'object', 'get', `${bucket}/${key}`, '--remote', '--file', probeFile]);
        const buf = await readFile(probeFile).catch(() => Buffer.alloc(0));
        if (buf.length > 0) {
          await rm(probeFile, { force: true });
          return { ...layout, sampleKey: key };
        }
      } catch { /* chiave assente: si prova la prossima */ }
    }
  }
  await rm(probeFile, { force: true });
  return null;
}

// Dove va caricata la carta `number` di questo set, dato il layout dedotto.
// Senza layout si ripiega su it/<SET>/<SET>_IT_<n>.webp, la forma piu' diffusa.
export function r2KeyFor(layout, setCodeUpper, number) {
  const folder = layout?.folder ?? setCodeUpper;
  const name = (layout?.shape ?? 'prefixed') === 'prefixed'
    ? `${folder.toUpperCase()}_IT_${number}.webp`
    : `${number}.webp`;
  return `it/${folder}/${name}`;
}
