-- V3: Seed golden_dictionary with ≥ 50 validated FR→RW entries.
-- Validated by 'native-mvp'. Covers contexts: transport, marché, restaurant, santé, social.
-- Error categories mark where the LLM historically fails (INFIX_PRONOUN, INVENTION,
-- PLURAL_RESPECT, ANTI_POLITENESS_STACKING, SYNTACTIC_CALQUE).

INSERT INTO golden_dictionary
    (source_text, source_lang, target_text, target_lang, alternatives, context, error_category, validated_by, validated_at, usage_count)
VALUES

-- ============================================================
-- TRANSPORT (10 entries)
-- ============================================================
('Où est la gare ?',
 'fr', 'Indagara iri he ?', 'rw',
 ARRAY['Station iri he ?'],
 'transport', NULL, 'native-mvp', NOW(), 0),

('Je voudrais aller à l''aéroport.',
 'fr', 'Ndashaka kujya ku kiraro cy''indege.', 'rw',
 NULL,
 'transport', NULL, 'native-mvp', NOW(), 0),

('Combien coûte le billet ?',
 'fr', 'Ticket irivye amafaranga angahe ?', 'rw',
 ARRAY['Billet irivye amafaranga angahe ?'],
 'transport', NULL, 'native-mvp', NOW(), 0),

('Arrêtez-vous ici, s''il vous plaît.',
 'fr', 'Hagarara hano, murakoze.', 'rw',
 NULL,
 'transport', 'PLURAL_RESPECT', 'native-mvp', NOW(), 0),

('Est-ce que ce bus va au centre-ville ?',
 'fr', 'Iki modoka igiye mu mujyi ?', 'rw',
 NULL,
 'transport', NULL, 'native-mvp', NOW(), 0),

('À quelle heure part le prochain bus ?',
 'fr', 'Modoka ikurikira iva ku saa zingahe ?', 'rw',
 NULL,
 'transport', NULL, 'native-mvp', NOW(), 0),

('Je suis perdu.',
 'fr', 'Nabuze inzira.', 'rw',
 NULL,
 'transport', NULL, 'native-mvp', NOW(), 0),

('Pouvez-vous m''aider à trouver mon chemin ?',
 'fr', 'Mwambwira aho nkurikire ?', 'rw',
 NULL,
 'transport', 'PLURAL_RESPECT', 'native-mvp', NOW(), 0),

('Tournez à gauche au carrefour.',
 'fr', 'Hindukira ibumoso ku karoni.', 'rw',
 NULL,
 'transport', NULL, 'native-mvp', NOW(), 0),

('C''est loin d''ici ?',
 'fr', 'Ni kure kuva hano ?', 'rw',
 NULL,
 'transport', NULL, 'native-mvp', NOW(), 0),

-- ============================================================
-- MARCHÉ (10 entries)
-- ============================================================
('Où est le marché ?',
 'fr', 'Isoko iri he ?', 'rw',
 NULL,
 'marché', NULL, 'native-mvp', NOW(), 0),

('Combien ça coûte ?',
 'fr', 'Ni amafaranga angahe ?', 'rw',
 ARRAY['Ibi birivye amafaranga angahe ?'],
 'marché', NULL, 'native-mvp', NOW(), 0),

('C''est trop cher.',
 'fr', 'Ni birengeye.', 'rw',
 ARRAY['Ni birengeye cyane.'],
 'marché', NULL, 'native-mvp', NOW(), 0),

('Pouvez-vous baisser le prix ?',
 'fr', 'Mwampa igiciro cyiza ?', 'rw',
 NULL,
 'marché', 'PLURAL_RESPECT', 'native-mvp', NOW(), 0),

('Je voudrais acheter des tomates.',
 'fr', 'Ndashaka kugura inyanya.', 'rw',
 NULL,
 'marché', NULL, 'native-mvp', NOW(), 0),

('Donnez-moi un kilo de bananes.',
 'fr', 'Mumpe ikilo cy''ibitoki.', 'rw',
 NULL,
 'marché', 'PLURAL_RESPECT', 'native-mvp', NOW(), 0),

('Avez-vous des pommes de terre ?',
 'fr', 'Mufite ibirayi ?', 'rw',
 NULL,
 'marché', NULL, 'native-mvp', NOW(), 0),

('Je prends tout ça.',
 'fr', 'Nzatwara ibi byose.', 'rw',
 NULL,
 'marché', NULL, 'native-mvp', NOW(), 0),

('Avez-vous de la monnaie ?',
 'fr', 'Mufite amafaranga make ?', 'rw',
 NULL,
 'marché', NULL, 'native-mvp', NOW(), 0),

('Je reviendrai demain.',
 'fr', 'Nzagaruka ejo.', 'rw',
 NULL,
 'marché', NULL, 'native-mvp', NOW(), 0),

-- ============================================================
-- RESTAURANT (10 entries)
-- ============================================================
('Une table pour deux personnes, s''il vous plaît.',
 'fr', 'Nimutubwire aho abantu babiri bashobora kwicara.', 'rw',
 NULL,
 'restaurant', 'PLURAL_RESPECT', 'native-mvp', NOW(), 0),

('Qu''est-ce que vous recommandez ?',
 'fr', 'Ni iki mudusugeraho ?', 'rw',
 NULL,
 'restaurant', 'PLURAL_RESPECT', 'native-mvp', NOW(), 0),

('Je voudrais de l''eau, s''il vous plaît.',
 'fr', 'Nimumpe amazi, murakoze.', 'rw',
 NULL,
 'restaurant', 'PLURAL_RESPECT', 'native-mvp', NOW(), 0),

('L''addition, s''il vous plaît.',
 'fr', 'Nimunteze fagitire, murakoze.', 'rw',
 ARRAY['Nimunteze konti, murakoze.'],
 'restaurant', 'PLURAL_RESPECT', 'native-mvp', NOW(), 0),

('C''est délicieux.',
 'fr', 'Ni byiza cyane.', 'rw',
 ARRAY['Biryoshe cyane.'],
 'restaurant', NULL, 'native-mvp', NOW(), 0),

('Je suis végétarien.',
 'fr', 'Sinrya inyama.', 'rw',
 NULL,
 'restaurant', NULL, 'native-mvp', NOW(), 0),

('Avez-vous un menu en français ?',
 'fr', 'Mufite menu mu rurimi rw''igifaransa ?', 'rw',
 NULL,
 'restaurant', NULL, 'native-mvp', NOW(), 0),

('Je voudrais commander.',
 'fr', 'Ndashaka gutuma.', 'rw',
 NULL,
 'restaurant', NULL, 'native-mvp', NOW(), 0),

('Pouvez-vous apporter encore du pain ?',
 'fr', 'Mwongere umutsima, murakoze.', 'rw',
 NULL,
 'restaurant', 'PLURAL_RESPECT', 'native-mvp', NOW(), 0),

('Merci, c''était très bon.',
 'fr', 'Murakoze, byari biryoshe cyane.', 'rw',
 NULL,
 'restaurant', NULL, 'native-mvp', NOW(), 0),

-- ============================================================
-- SANTÉ (10 entries)
-- ============================================================
('J''ai besoin d''un médecin.',
 'fr', 'Nkeneye muganga.', 'rw',
 NULL,
 'santé', NULL, 'native-mvp', NOW(), 0),

('Où est l''hôpital ?',
 'fr', 'Ibitaro biri he ?', 'rw',
 NULL,
 'santé', NULL, 'native-mvp', NOW(), 0),

('J''ai mal à la tête.',
 'fr', 'Umutwe urarambura.', 'rw',
 ARRAY['Umutwe urabona.'],
 'santé', NULL, 'native-mvp', NOW(), 0),

('J''ai de la fièvre.',
 'fr', 'Mfite umuriro.', 'rw',
 NULL,
 'santé', NULL, 'native-mvp', NOW(), 0),

('Appelez une ambulance, s''il vous plaît.',
 'fr', 'Nimwite ambulansi, murakoze.', 'rw',
 NULL,
 'santé', 'PLURAL_RESPECT', 'native-mvp', NOW(), 0),

('Je suis allergique à la pénicilline.',
 'fr', 'Penisiline irandwara.', 'rw',
 NULL,
 'santé', NULL, 'native-mvp', NOW(), 0),

('Où est la pharmacie ?',
 'fr', 'Farmasi iri he ?', 'rw',
 NULL,
 'santé', NULL, 'native-mvp', NOW(), 0),

('J''ai besoin d''un médicament contre la douleur.',
 'fr', 'Nkeneye umuti wo gukumira ububabare.', 'rw',
 NULL,
 'santé', NULL, 'native-mvp', NOW(), 0),

('Pouvez-vous répéter lentement ?',
 'fr', 'Mwongerere, ariko buhoro buhoro.', 'rw',
 NULL,
 'santé', 'PLURAL_RESPECT', 'native-mvp', NOW(), 0),

('Je dois me reposer.',
 'fr', 'Ngombwa kuruhuka.', 'rw',
 NULL,
 'santé', NULL, 'native-mvp', NOW(), 0),

-- ============================================================
-- SOCIAL / POLITESSE (12 entries)
-- ============================================================
('Bonjour.',
 'fr', 'Muraho.', 'rw',
 ARRAY['Bonjour.'],
 'social', NULL, 'native-mvp', NOW(), 0),

('Bonsoir.',
 'fr', 'Mwiriwe.', 'rw',
 NULL,
 'social', NULL, 'native-mvp', NOW(), 0),

('Bonne nuit.',
 'fr', 'Ijoro ryiza.', 'rw',
 NULL,
 'social', NULL, 'native-mvp', NOW(), 0),

('Comment allez-vous ?',
 'fr', 'Amakuru ?', 'rw',
 ARRAY['Meze mute ?'],
 'social', NULL, 'native-mvp', NOW(), 0),

('Je vais bien, merci.',
 'fr', 'Ni meza, murakoze.', 'rw',
 ARRAY['Meze neza, urakoze.'],
 'social', NULL, 'native-mvp', NOW(), 0),

('Merci beaucoup.',
 'fr', 'Murakoze cyane.', 'rw',
 ARRAY['Urakoze cyane.'],
 'social', NULL, 'native-mvp', NOW(), 0),

('S''il vous plaît.',
 'fr', 'Murakoze.', 'rw',
 ARRAY['Ndakwinginze.'],
 'social', 'PLURAL_RESPECT', 'native-mvp', NOW(), 0),

('Excusez-moi.',
 'fr', 'Mbabarira.', 'rw',
 NULL,
 'social', NULL, 'native-mvp', NOW(), 0),

('Je ne comprends pas.',
 'fr', 'Ntabwo nibuka.', 'rw',
 ARRAY['Sibisobanukirwa.'],
 'social', NULL, 'native-mvp', NOW(), 0),

('Parlez-vous français ?',
 'fr', 'Muravuga igifaransa ?', 'rw',
 NULL,
 'social', 'PLURAL_RESPECT', 'native-mvp', NOW(), 0),

('Au revoir.',
 'fr', 'Murabeho.', 'rw',
 ARRAY['Raho.'],
 'social', NULL, 'native-mvp', NOW(), 0),

('Bienvenue.',
 'fr', 'Murakaza neza.', 'rw',
 NULL,
 'social', NULL, 'native-mvp', NOW(), 0);
