// Normalizza il campo `stage` di TCGdex alla sua forma canonica inglese.
//
// TCGdex localizza lo stadio: /v2/en dice "Stage1", /v2/it dice "Livello 1"
// per la stessa carta (verificato il 14/09/2026 su sv08-130). L'ingest di un
// set nuovo legge il payload italiano e i backfill leggono quello inglese:
// senza questa funzione la colonna cards.stage finirebbe con due grafie per lo
// stesso stadio, e ogni consumatore dovrebbe conoscerle entrambe.
//
// Uno stadio non riconosciuto viene restituito com'e' invece che scartato:
// meglio un valore da mappare dopo che un buco nei dati.

const CANONICAL_BY_NORMALIZED = {
  basic: 'Basic',
  base: 'Basic',
  stage1: 'Stage1',
  livello1: 'Stage1',
  fase1: 'Stage1',
  stage2: 'Stage2',
  livello2: 'Stage2',
  fase2: 'Stage2',
  vmax: 'VMAX',
  vstar: 'VSTAR',
  mega: 'MEGA',
  break: 'BREAK',
  restored: 'RESTORED',
  risorto: 'RESTORED',
  levelup: 'LEVEL-UP',
};

export function canonicalStage(raw) {
  const trimmed = String(raw ?? '').trim();
  if (!trimmed) return null;
  const normalized = trimmed.toLowerCase().replace(/[^a-z0-9]/g, '');
  return CANONICAL_BY_NORMALIZED[normalized] ?? trimmed;
}
