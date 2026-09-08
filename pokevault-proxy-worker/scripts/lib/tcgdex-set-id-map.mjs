// Our expansion id -> TCGdex set id, only where they differ. Found by
// searching TCGdex's full set list (`GET /v2/en/sets`) by name and verified
// live on 2026-09-08 (see MIGRATION_PLAN.md M4.6). Every expansion id not
// listed here already matches its TCGdex id directly (verified: 200 on
// `GET /v2/en/sets/{id}` for all of them).
//
// Shared by scripts/backfill-rarity-tcgdex.mjs and
// scripts/backfill-release-date-tcgdex.mjs -- keep in one place so the two
// backfills can never drift onto different set mappings.
export const TCGDEX_ID_OVERRIDES = {
  sv3pt5: 'sv03.5', // Pokémon 151
  sv4pt5: 'sv04.5', // Paldean Fates
  sv6pt5: 'sv06.5', // Shrouded Fable
  sv8pt5: 'sv08.5', // Prismatic Evolutions
  zsv10pt5: 'sv10.5w', // White Flare
  rsv10pt5: 'sv10.5b', // Black Bolt
  swsh45sv: 'swsh4.5sv', // Shining Fates Shiny Vault
  pgo: 'swsh10.5', // Pokémon GO
  cel25c: 'cel25cc', // Celebrations Classic Collection
  det: 'det1', // Detective Pikachu
  sm35: 'sm3.5', // Shining Legends
  sm75: 'sm7.5', // Dragon Majesty
  swsh12pt5: 'swsh12.5', // Crown Zenith
  swsh12pt5gg: 'swsh12.5gg', // Crown Zenith Galarian Gallery
  me2pt5: 'me02.5', // Ascended Heroes
  swsh35: 'swsh3.5', // Champion's Path
  swsh45: 'swsh4.5', // Shining Fates
};
