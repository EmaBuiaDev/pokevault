-- Adds expansions.base_set_code: the dominant raw (English) set code among an
-- expansion's cards, e.g. "DP1", "CRI", "SVI". Precomputes server-side what
-- the Android client currently derives at runtime by scanning every card of
-- an expansion (PokeTcgRepository.mergeItalianSets's `dominantRawSetCode`) --
-- used to link an Italian expansion to its English base set for logo/series/
-- release date. Populating it here lets /v1/expansions carry it directly, so
-- building the Pokedex list no longer requires fetching the full card catalog.
--
-- Verified before writing this: every card_id in production contains "_IT_"
-- (15526/15526), so the LIKE-based extraction below is safe with no NULLs
-- left over from a non-matching format.

ALTER TABLE expansions ADD COLUMN base_set_code TEXT;

WITH set_code_extract AS (
  SELECT
    expansion_id,
    UPPER(SUBSTR(card_id, 1, INSTR(card_id, '_IT_') - 1)) AS raw_set_code
  FROM cards
  WHERE INSTR(card_id, '_IT_') > 0
),
counted AS (
  SELECT expansion_id, raw_set_code, COUNT(*) AS cnt
  FROM set_code_extract
  GROUP BY expansion_id, raw_set_code
),
ranked AS (
  SELECT
    expansion_id,
    raw_set_code,
    ROW_NUMBER() OVER (PARTITION BY expansion_id ORDER BY cnt DESC, raw_set_code ASC) AS rn
  FROM counted
)
UPDATE expansions
SET base_set_code = (
  SELECT raw_set_code FROM ranked WHERE ranked.expansion_id = expansions.id AND rn = 1
)
WHERE id IN (SELECT DISTINCT expansion_id FROM ranked);
