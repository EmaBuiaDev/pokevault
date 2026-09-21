-- Le carte che TCGdex non copre nel set di appartenenza, chiuse una per una.
-- Ogni UPDATE resta condizionato a rarity vuota: non deve poter sovrascrivere
-- niente di gia' valorizzato, nemmeno rilanciandolo due volte.

-- det: le nostre SM190-SM200 sono promo del film, numerate come SM Black Star
-- Promos. TCGdex le tiene in `smp`, non in `det1`: smp-SM190/194/195/200
-- verificate una per una, tutte "Promo".
UPDATE cards SET rarity = 'Promo'
 WHERE expansion_id = 'det' AND (rarity IS NULL OR TRIM(rarity) = '');

-- mep: set interamente promozionale (le altre 88 carte sono gia' "Promo").
-- Le 94-110 sono le promo oltre l'89 che TCGdex cataloga -- vedi il topup da
-- archivio ufficiale.
UPDATE cards SET rarity = 'Promo'
 WHERE expansion_id = 'mep' AND (rarity IS NULL OR TRIM(rarity) = '');

-- cel25c: i nostri numeri ("2_A") non parlano con i localId di TCGdex
-- ("CC001"), ma non serve appaiarli: tutte e 25 le carte di
-- Celebrations Classic Collection hanno la stessa rarita'.
UPDATE cards SET rarity = 'Classic Collection'
 WHERE expansion_id = 'cel25c' AND (rarity IS NULL OR TRIM(rarity) = '');

-- ecard2: quattro localId che mancano nel dataset TCGdex di Aquapolis.
-- Presi dalla scheda del wiki italiano, che li stampa in vocabolario inglese.
-- Si punta a espansione + numero, non al card_id: i set arrivati dal wiki
-- hanno l'immagine in .webp (ECARD2_IT_50.webp) mentre quelli storici sono in
-- .png, e indovinare l'estensione fa fallire l'UPDATE in silenzio.
UPDATE cards SET rarity = CASE card_number WHEN '50' THEN 'Uncommon' ELSE 'Common' END
 WHERE expansion_id = 'ecard2' AND card_number IN ('50', '74', '95', '103')
   AND (rarity IS NULL OR TRIM(rarity) = '');

-- hgss2 96: la Litografia d'Alfa. TCGdex la numera "TWO", non 96, quindi il
-- confronto per numero non la trova (hgss2-TWO -> "Ultra Rare").
UPDATE cards SET rarity = 'Ultra Rare'
 WHERE expansion_id = 'hgss2' AND card_number = '96'
   AND (rarity IS NULL OR TRIM(rarity) = '');
