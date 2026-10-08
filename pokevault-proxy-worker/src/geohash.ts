/**
 * Celle geohash vicine, per TradeRadar.
 *
 * Il telefono manda solo la sua cella di 5 caratteri (~4,9 x 4,9 km): qui si
 * calcolano le 8 celle intorno, cosi' due utenti a 200 metri ma ai due lati di
 * un bordo di cella si trovano lo stesso. Il server non vede mai coordinate.
 */

const BASE32 = '0123456789bcdefghjkmnpqrstuvwxyz';

export const GEOHASH5_REGEX = /^[0123456789bcdefghjkmnpqrstuvwxyz]{5}$/;

/** Centro e mezze dimensioni della cella, in gradi. */
function decode(hash: string): { lat: number; lon: number; latErr: number; lonErr: number } {
  let evenBit = true;
  let latMin = -90, latMax = 90, lonMin = -180, lonMax = 180;
  for (const char of hash) {
    const idx = BASE32.indexOf(char);
    for (let bit = 4; bit >= 0; bit--) {
      const on = (idx >> bit) & 1;
      if (evenBit) {
        const mid = (lonMin + lonMax) / 2;
        if (on) lonMin = mid; else lonMax = mid;
      } else {
        const mid = (latMin + latMax) / 2;
        if (on) latMin = mid; else latMax = mid;
      }
      evenBit = !evenBit;
    }
  }
  return {
    lat: (latMin + latMax) / 2,
    lon: (lonMin + lonMax) / 2,
    latErr: (latMax - latMin) / 2,
    lonErr: (lonMax - lonMin) / 2,
  };
}

/** Il centro di una cella: per i luoghi d'incontro, mai per una persona. */
export function cellCenter(hash: string): { lat: number; lon: number } {
  const { lat, lon } = decode(hash);
  return { lat, lon };
}

export function encode(lat: number, lon: number, precision: number): string {
  let evenBit = true;
  let latMin = -90, latMax = 90, lonMin = -180, lonMax = 180;
  let hash = '';
  let idx = 0;
  let bit = 0;
  while (hash.length < precision) {
    if (evenBit) {
      const mid = (lonMin + lonMax) / 2;
      if (lon >= mid) { idx = idx * 2 + 1; lonMin = mid; } else { idx *= 2; lonMax = mid; }
    } else {
      const mid = (latMin + latMax) / 2;
      if (lat >= mid) { idx = idx * 2 + 1; latMin = mid; } else { idx *= 2; latMax = mid; }
    }
    evenBit = !evenBit;
    if (++bit === 5) {
      hash += BASE32[idx];
      bit = 0;
      idx = 0;
    }
  }
  return hash;
}

/** La cella e le 8 intorno (meno, vicino ai poli, dove si ripetono). */
export function cellAndNeighbors(hash: string): string[] {
  const { lat, lon, latErr, lonErr } = decode(hash);
  const cells = new Set<string>();
  for (const dLat of [-1, 0, 1]) {
    for (const dLon of [-1, 0, 1]) {
      const nLat = Math.max(-89.999, Math.min(89.999, lat + dLat * latErr * 2));
      let nLon = lon + dLon * lonErr * 2;
      if (nLon > 180) nLon -= 360;
      if (nLon < -180) nLon += 360;
      cells.add(encode(nLat, nLon, hash.length));
    }
  }
  return [...cells];
}
