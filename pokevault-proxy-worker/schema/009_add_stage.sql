-- Adds cards.stage (lo stadio evolutivo: Basic / Stage1 / Stage2 / VMAX ...),
-- backfilled una volta sola da TCGdex (MIT, vedi scripts/backfill-stage-tcgdex.mjs)
-- e da li' in poi nostro in D1 come il resto del catalogo -- nessuna dipendenza
-- a runtime da TCGdex o PokeWallet.
--
-- Perche' serve: senza stadio l'app non puo' sapere se un Pokemon si puo'
-- calare in campo dalla mano. L'Hand-Simulator contava come "Basic" ogni carta
-- il cui sottotipo non nominava un'evoluzione, cioe' TUTTE quelle importate dal
-- catalogo italiano (toItalianTcgCard riempiva subtypes solo dalla carta
-- inglese di appoggio, quasi sempre assente): una Fase 1 in mano risultava
-- giocabile, e mulligan e starter rate ne uscivano ottimisti.
--
-- Valori: la stringa canonica inglese di TCGdex (Basic, Stage1, Stage2, VMAX,
-- VSTAR, MEGA, BREAK, RESTORED, LEVEL-UP), normalizzata in scrittura da
-- scripts/lib/tcgdex-stage.mjs -- l'endpoint italiano dice "Livello 1" dove
-- quello inglese dice "Stage1", e due grafie per lo stesso stadio nella stessa
-- colonna sono un modo sicuro per sbagliare la query dopo. NULL per Trainer,
-- Energie e per le carte che TCGdex non copre.

ALTER TABLE cards ADD COLUMN stage TEXT;
