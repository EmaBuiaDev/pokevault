-- Adds expansions.official_count: quante carte ha la parte BASE di una
-- espansione -- cioe' il numero che sta stampato sulle carte dopo la barra.
--
-- card_count, l'unico conteggio che avevamo, e' il totale: base piu' segrete e
-- fuori serie. I due numeri non coincidono quasi mai (84 espansioni su 105) e
-- quello stampato e' sempre il minore. Ascesa Eroica: 217 sulle carte, 295 in
-- card_count. Le carte oltre il 217 -- le segrete -- si numerano "219/217",
-- superando il proprio totale.
--
-- Perche' questa colonna esiste: la ricerca per ID nell'app confronta il totale
-- che l'utente digita ("066/217") coi totali noti dell'espansione. Senza il
-- conteggio ufficiale l'unico confronto possibile era contro card_count, che
-- quel numero non lo contiene: cercare "066/217" non trovava Ascesa Eroica e
-- ripiegava sulla carta 66 di un'espansione qualsiasi. Solo "066/295" -- un
-- numero che su nessuna carta e' mai stampato -- la trovava.
--
-- Fonte: TCGdex `cardCount.official`, la stessa da cui arrivano rarita',
-- release date, serie e nome del set (schema/002, 004, 005, 006). Verificato
-- il 2026-09-13 su tutte e 106 le espansioni in catalogo: 105 rispondono, e
-- l'unica senza conteggio ufficiale e' `mep` (i promo Mega Evolution), che un
-- totale stampato non ce l'ha per definizione -- resta NULL, e l'app tratta il
-- NULL come "non lo so", mai come "non e' questa".
--
-- I nuovi set lo prendono da scripts/backfill-official-count-tcgdex.mjs, e
-- scripts/ingest-tcgdex-set.mjs lo compila al momento dell'ingest.

ALTER TABLE expansions ADD COLUMN official_count INTEGER;

UPDATE expansions SET official_count = 114 WHERE id = 'bw1';
UPDATE expansions SET official_count = 98 WHERE id = 'bw2';
UPDATE expansions SET official_count = 101 WHERE id = 'bw3';
UPDATE expansions SET official_count = 99 WHERE id = 'bw4';
UPDATE expansions SET official_count = 108 WHERE id = 'bw5';
UPDATE expansions SET official_count = 124 WHERE id = 'bw6';
UPDATE expansions SET official_count = 149 WHERE id = 'bw7';
UPDATE expansions SET official_count = 135 WHERE id = 'bw8';
UPDATE expansions SET official_count = 116 WHERE id = 'bw9';
UPDATE expansions SET official_count = 101 WHERE id = 'bwp';
UPDATE expansions SET official_count = 25 WHERE id = 'cel25';
UPDATE expansions SET official_count = 25 WHERE id = 'cel25c';
UPDATE expansions SET official_count = 95 WHERE id = 'col1';
UPDATE expansions SET official_count = 18 WHERE id = 'det';
UPDATE expansions SET official_count = 130 WHERE id = 'dp1';
UPDATE expansions SET official_count = 123 WHERE id = 'dp2';
UPDATE expansions SET official_count = 132 WHERE id = 'dp3';
UPDATE expansions SET official_count = 100 WHERE id = 'dp5';
UPDATE expansions SET official_count = 146 WHERE id = 'dp6';
UPDATE expansions SET official_count = 100 WHERE id = 'dp7';
UPDATE expansions SET official_count = 83 WHERE id = 'g1';
UPDATE expansions SET official_count = 123 WHERE id = 'hgss1';
UPDATE expansions SET official_count = 95 WHERE id = 'hgss2';
UPDATE expansions SET official_count = 90 WHERE id = 'hgss3';
UPDATE expansions SET official_count = 102 WHERE id = 'hgss4';
UPDATE expansions SET official_count = 132 WHERE id = 'me01';
UPDATE expansions SET official_count = 94 WHERE id = 'me02';
UPDATE expansions SET official_count = 88 WHERE id = 'me03';
UPDATE expansions SET official_count = 86 WHERE id = 'me04';
UPDATE expansions SET official_count = 217 WHERE id = 'me2pt5';
UPDATE expansions SET official_count = 78 WHERE id = 'pgo';
UPDATE expansions SET official_count = 127 WHERE id = 'pl1';
UPDATE expansions SET official_count = 111 WHERE id = 'pl2';
UPDATE expansions SET official_count = 99 WHERE id = 'pl4';
UPDATE expansions SET official_count = 86 WHERE id = 'rsv10pt5';
UPDATE expansions SET official_count = 149 WHERE id = 'sm1';
UPDATE expansions SET official_count = 214 WHERE id = 'sm10';
UPDATE expansions SET official_count = 236 WHERE id = 'sm11';
UPDATE expansions SET official_count = 68 WHERE id = 'sm115';
UPDATE expansions SET official_count = 236 WHERE id = 'sm12';
UPDATE expansions SET official_count = 145 WHERE id = 'sm2';
UPDATE expansions SET official_count = 147 WHERE id = 'sm3';
UPDATE expansions SET official_count = 73 WHERE id = 'sm35';
UPDATE expansions SET official_count = 111 WHERE id = 'sm4';
UPDATE expansions SET official_count = 156 WHERE id = 'sm5';
UPDATE expansions SET official_count = 131 WHERE id = 'sm6';
UPDATE expansions SET official_count = 168 WHERE id = 'sm7';
UPDATE expansions SET official_count = 70 WHERE id = 'sm75';
UPDATE expansions SET official_count = 214 WHERE id = 'sm8';
UPDATE expansions SET official_count = 181 WHERE id = 'sm9';
UPDATE expansions SET official_count = 94 WHERE id = 'sma';
UPDATE expansions SET official_count = 248 WHERE id = 'smp';
UPDATE expansions SET official_count = 198 WHERE id = 'sv01';
UPDATE expansions SET official_count = 193 WHERE id = 'sv02';
UPDATE expansions SET official_count = 197 WHERE id = 'sv03';
UPDATE expansions SET official_count = 182 WHERE id = 'sv04';
UPDATE expansions SET official_count = 162 WHERE id = 'sv05';
UPDATE expansions SET official_count = 167 WHERE id = 'sv06';
UPDATE expansions SET official_count = 142 WHERE id = 'sv07';
UPDATE expansions SET official_count = 191 WHERE id = 'sv08';
UPDATE expansions SET official_count = 159 WHERE id = 'sv09';
UPDATE expansions SET official_count = 182 WHERE id = 'sv10';
UPDATE expansions SET official_count = 165 WHERE id = 'sv3pt5';
UPDATE expansions SET official_count = 91 WHERE id = 'sv4pt5';
UPDATE expansions SET official_count = 64 WHERE id = 'sv6pt5';
UPDATE expansions SET official_count = 131 WHERE id = 'sv8pt5';
UPDATE expansions SET official_count = 225 WHERE id = 'svp';
UPDATE expansions SET official_count = 202 WHERE id = 'swsh1';
UPDATE expansions SET official_count = 189 WHERE id = 'swsh10';
UPDATE expansions SET official_count = 30 WHERE id = 'swsh10tg';
UPDATE expansions SET official_count = 196 WHERE id = 'swsh11';
UPDATE expansions SET official_count = 30 WHERE id = 'swsh11tg';
UPDATE expansions SET official_count = 195 WHERE id = 'swsh12';
UPDATE expansions SET official_count = 159 WHERE id = 'swsh12pt5';
UPDATE expansions SET official_count = 70 WHERE id = 'swsh12pt5gg';
UPDATE expansions SET official_count = 30 WHERE id = 'swsh12tg';
UPDATE expansions SET official_count = 192 WHERE id = 'swsh2';
UPDATE expansions SET official_count = 189 WHERE id = 'swsh3';
UPDATE expansions SET official_count = 73 WHERE id = 'swsh35';
UPDATE expansions SET official_count = 185 WHERE id = 'swsh4';
UPDATE expansions SET official_count = 72 WHERE id = 'swsh45';
UPDATE expansions SET official_count = 122 WHERE id = 'swsh45sv';
UPDATE expansions SET official_count = 163 WHERE id = 'swsh5';
UPDATE expansions SET official_count = 198 WHERE id = 'swsh6';
UPDATE expansions SET official_count = 203 WHERE id = 'swsh7';
UPDATE expansions SET official_count = 264 WHERE id = 'swsh8';
UPDATE expansions SET official_count = 172 WHERE id = 'swsh9';
UPDATE expansions SET official_count = 30 WHERE id = 'swsh9tg';
UPDATE expansions SET official_count = 307 WHERE id = 'swshp';
UPDATE expansions SET official_count = 39 WHERE id = 'xy0';
UPDATE expansions SET official_count = 146 WHERE id = 'xy1';
UPDATE expansions SET official_count = 124 WHERE id = 'xy10';
UPDATE expansions SET official_count = 114 WHERE id = 'xy11';
UPDATE expansions SET official_count = 108 WHERE id = 'xy12';
UPDATE expansions SET official_count = 106 WHERE id = 'xy2';
UPDATE expansions SET official_count = 111 WHERE id = 'xy3';
UPDATE expansions SET official_count = 119 WHERE id = 'xy4';
UPDATE expansions SET official_count = 160 WHERE id = 'xy5';
UPDATE expansions SET official_count = 108 WHERE id = 'xy6';
UPDATE expansions SET official_count = 98 WHERE id = 'xy7';
UPDATE expansions SET official_count = 162 WHERE id = 'xy8';
UPDATE expansions SET official_count = 122 WHERE id = 'xy9';
UPDATE expansions SET official_count = 6 WHERE id = 'xya';
UPDATE expansions SET official_count = 211 WHERE id = 'xyp';
UPDATE expansions SET official_count = 86 WHERE id = 'zsv10pt5';

-- Pitch Black (me05), ingerita dopo lo snapshot del catalogo su cui sono
-- stati calcolati i seed qui sopra.
UPDATE expansions SET official_count = 84 WHERE id = 'me05';
