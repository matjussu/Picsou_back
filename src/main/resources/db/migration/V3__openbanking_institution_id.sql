-- Phase 5 (mock Open Banking) : rattacher une connexion bancaire à une banque du catalogue.
--
-- En Open Banking réel, `provider` (bridge/gocardless/tink) est l'AGRÉGATEUR, pas la banque :
-- il ne suffit donc pas à identifier l'établissement connecté ni à porter son identité de marque
-- (nom, logo) côté UI. On ajoute `institution_id` = slug de la banque dans le catalogue statique
-- exposé par GET /api/openbanking/institutions (ex. 'bnp-paribas', 'revolut').
--
-- Additif et nullable : migration non destructive, n'affecte aucune ligne existante.
ALTER TABLE bank_connections ADD COLUMN institution_id TEXT;
