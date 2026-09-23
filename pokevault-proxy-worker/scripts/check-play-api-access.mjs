/**
 * Dice se il service account puo' gia' interrogare la Play Developer API.
 *
 * I permessi concessi in Play Console impiegano fino a 24 ore a propagarsi, e
 * nel frattempo /v1/billing/verify risponde 502 senza spiegare perche'. Senza
 * questo controllo l'unico modo di sapere se e' pronto sarebbe riprovare
 * dall'app a caso.
 *
 * Come funziona: si autentica come il service account e chiede lo stato di un
 * purchase token palesemente finto. La risposta distingue i casi:
 *
 *   404  -> TUTTO OK. Autenticazione e permessi funzionano; il token non esiste,
 *           ed e' esattamente quello che ci aspettiamo da uno inventato.
 *   401  -> le credenziali non sono valide.
 *   403  -> credenziali valide ma permesso mancante o non ancora propagato.
 *           E' il caso in cui bisogna solo aspettare.
 *
 * Uso:
 *   node scripts/check-play-api-access.mjs <percorso-del-json> [package]
 *
 * Non stampa mai il contenuto della chiave.
 */

import { readFileSync } from 'node:fs';
import { createSign } from 'node:crypto';

const TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token';
const SCOPE = 'https://www.googleapis.com/auth/androidpublisher';
const ANDROID_PUBLISHER = 'https://androidpublisher.googleapis.com/androidpublisher/v3';

const keyPath = process.argv[2];
const packageName = process.argv[3] ?? 'com.emabuia.pokevault';

if (!keyPath) {
  console.error('Uso: node scripts/check-play-api-access.mjs <percorso-del-json> [package]');
  process.exit(2);
}

function base64Url(input) {
  return Buffer.from(input).toString('base64')
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function accessToken(account) {
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const payload = base64Url(JSON.stringify({
    iss: account.client_email,
    scope: SCOPE,
    aud: TOKEN_ENDPOINT,
    iat: now,
    exp: now + 3600,
  }));

  const signer = createSign('RSA-SHA256');
  signer.update(`${header}.${payload}`);
  const signature = signer.sign(account.private_key.replace(/\\n/g, '\n'), 'base64')
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

  const response = await fetch(TOKEN_ENDPOINT, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: `${header}.${payload}.${signature}`,
    }),
  });

  const body = await response.json();
  if (!response.ok || !body.access_token) {
    throw new Error(`token non ottenuto (HTTP ${response.status}): ${body.error_description ?? body.error ?? ''}`);
  }
  return body.access_token;
}

const account = JSON.parse(readFileSync(keyPath, 'utf8'));
console.log(`Account di servizio: ${account.client_email}`);
console.log(`Package:             ${packageName}\n`);

let token;
try {
  token = await accessToken(account);
  console.log('[1/2] Autenticazione presso Google........ OK');
} catch (error) {
  console.log('[1/2] Autenticazione presso Google........ FALLITA');
  console.log(`      ${error.message}`);
  console.log('\nLa chiave non e\' valida, o la Google Play Android Developer API');
  console.log('non e\' abilitata sul progetto Cloud.');
  process.exit(1);
}

// Un token volutamente inesistente: ci interessa il CODICE della risposta, non il dato.
const url = `${ANDROID_PUBLISHER}/applications/${encodeURIComponent(packageName)}` +
  `/purchases/subscriptionsv2/tokens/${encodeURIComponent('token-di-prova-inesistente')}`;

const probe = await fetch(url, { headers: { authorization: `Bearer ${token}` } });

if (probe.status === 404) {
  console.log('[2/2] Permessi in Play Console............ OK\n');
  console.log('Pronto: il Worker puo\' verificare gli abbonamenti.');
  console.log('Il 404 e\' il risultato atteso, il purchase token era finto.');
  process.exit(0);
}

if (probe.status === 401 || probe.status === 403) {
  const detail = await probe.text();

  // Due 403 molto diversi finiscono sullo stesso codice, e confonderli costa
  // un giorno di attesa per un problema che si risolve in due minuti.
  const apiDisabled = /has not been used in project|API has not been used|is disabled/i.test(detail);

  if (apiDisabled) {
    const project = /project (\d+)/.exec(detail)?.[1];
    console.log('[2/2] API abilitata sul progetto Cloud... NO\n');
    console.log('Non e\' un problema di permessi e non serve aspettare: la Google Play');
    console.log('Android Developer API non e\' attiva sul progetto Cloud. Abilitala qui:\n');
    console.log(
      `  https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com` +
      (project ? `?project=${project}` : '')
    );
    console.log('\nPoi aspetta un paio di minuti e rilancia questo script.');
    process.exit(1);
  }

  console.log('[2/2] Permessi in Play Console............ NON ANCORA\n');
  console.log(`HTTP ${probe.status}. Di solito significa che il permesso "Visualizza dati`);
  console.log('finanziari" non si e\' ancora propagato: puo\' richiedere fino a 24 ore.');
  console.log('Se fra un giorno risponde ancora cosi\', controlla che l\'account di servizio');
  console.log('sia invitato in Play Console e abbia quel permesso.\n');
  console.log(detail.slice(0, 400));
  process.exit(1);
}

console.log(`[2/2] Risposta inattesa: HTTP ${probe.status}\n`);
console.log((await probe.text()).slice(0, 400));
process.exit(1);
