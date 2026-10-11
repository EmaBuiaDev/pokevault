// Il codice con cui l'app chiede le immagini di un'espansione
// (/sets/{SETCODE}/image, /sets/{SETCODE}/symbol): questa piccola mappa vince
// su expansions.base_set_code di D1. Rispecchia
// preferredBaseSetCodeForItalianExpansion in CatalogRepository.kt -- se
// cambia quella, va cambiata anche questa.
//
// Condivisa dai backfill di loghi e simboli: tutti e due scrivono su R2 sotto
// it/{SETCODE}/ e non devono poter finire in due cartelle diverse.
export const CLIENT_SET_CODE_OVERRIDES = {
  me01: 'MEG',
  me02: 'PFL',
  me03: 'ME03',
  me04: 'CRI',
  me2pt5: 'ASC',
  mep: 'MEP',
  sv01: 'SVI',
  sv02: 'PAL',
  sv03: 'OBF',
  sv04: 'PAR',
  sv05: 'TEF',
  sv06: 'TWM',
  sv07: 'SCR',
  sv08: 'SSP',
  sv09: 'JTG',
  sv10: 'DRI',
  zsv10pt5: 'BLK',
  rsv10pt5: 'WHT',
  sv3pt5: 'MEW',
  sv4pt5: 'PAF',
  sv6pt5: 'SFA',
  sv8pt5: 'PRE',
};
