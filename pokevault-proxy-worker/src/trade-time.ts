/**
 * L'ora italiana di TradeRadar: appuntamenti, promemoria e ore di silenzio
 * ragionano tutti in Europe/Rome, mentre il Worker vive in UTC.
 */

export interface Slot {
  day: string;
  /** "HH:mm", dalle 07:00 alle 23:00. Le prime prove (01/10) avevano solo la fascia. */
  time?: string;
  part: string;
}

/** Giorno ("2026-10-02") e ora ("10:30") in Italia, per gli appuntamenti. */
export function romeNow(at: number = Date.now()): { day: string; time: string } {
  const parts = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Europe/Rome', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).format(new Date(at));
  const [day, time] = parts.split(' ');
  return { day, time };
}

/** L'ora di un appuntamento: quella scelta, o la fine della fascia per le prime prove senza ora. */
export function slotTime(slot: Slot): string {
  if (slot.time) return slot.time;
  return slot.part === 'morning' ? '13:00' : slot.part === 'afternoon' ? '19:00' : '23:00';
}

/** Di quanto Roma e' avanti rispetto a UTC in quell'istante (1 o 2 ore, secondo l'ora legale). */
function romeOffsetMs(at: number): number {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Europe/Rome', hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
  }).formatToParts(new Date(at));
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value);
  return Date.UTC(get('year'), get('month') - 1, get('day'), get('hour'), get('minute'), get('second')) - Math.floor(at / 1000) * 1000;
}

/** L'istante (ms) di un giorno e un'ora italiani. */
export function romeToEpoch(day: string, time: string): number {
  const [y, m, d] = day.split('-').map(Number);
  const [hh, mm] = time.split(':').map(Number);
  const asUtc = Date.UTC(y, m - 1, d, hh, mm);
  // Due passaggi: vicino al cambio dell'ora legale il primo offset puo' essere quello sbagliato.
  const first = asUtc - romeOffsetMs(asUtc);
  return asUtc - romeOffsetMs(first);
}

/** "2026-10-02" + n giorni. */
export function addDays(day: string, n: number): string {
  const [y, m, d] = day.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10);
}
