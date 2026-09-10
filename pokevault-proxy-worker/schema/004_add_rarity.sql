-- Adds cards.rarity, backfilled once from TCGdex (MIT-licensed, see
-- scripts/backfill-rarity-tcgdex.mjs) and owned by us in D1 from then on --
-- no runtime dependency on TCGdex or PokeWallet to display it.
-- See MIGRATION_PLAN.md M4.6 for the full source/coverage analysis.

ALTER TABLE cards ADD COLUMN rarity TEXT;
