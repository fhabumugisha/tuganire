-- V6: correct the Kinyarwanda translations in the golden dictionary (human/GPT-reviewed).
-- Matched on the French source_text. Dollar-quoting ($$) avoids escaping the apostrophes
-- present in both the French and Kinyarwanda text. Idempotent: re-running sets the same values.

-- 🚌 Transport / orientation
UPDATE golden_dictionary SET target_text = $$Gare iri he ?$$, last_modified_at = now() WHERE source_text = $$Où est la gare ?$$;
UPDATE golden_dictionary SET target_text = $$Ndashaka kujya ku kibuga cy'indege.$$, last_modified_at = now() WHERE source_text = $$Je voudrais aller à l'aéroport.$$;
UPDATE golden_dictionary SET target_text = $$Itike igura angahe ?$$, last_modified_at = now() WHERE source_text = $$Combien coûte le billet ?$$;
UPDATE golden_dictionary SET target_text = $$Hagarara hano, nyamuneka.$$, last_modified_at = now() WHERE source_text = $$Arrêtez-vous ici, s'il vous plaît.$$;
UPDATE golden_dictionary SET target_text = $$Iyi bisi ijya mu mujyi rwagati ?$$, last_modified_at = now() WHERE source_text = $$Est-ce que ce bus va au centre-ville ?$$;
UPDATE golden_dictionary SET target_text = $$Bisi ikurikira ihaguruka saa ngahe ?$$, last_modified_at = now() WHERE source_text = $$À quelle heure part le prochain bus ?$$;
UPDATE golden_dictionary SET target_text = $$Nayobye.$$, last_modified_at = now() WHERE source_text = $$Je suis perdu.$$;
UPDATE golden_dictionary SET target_text = $$Mwamfasha kubona inzira ?$$, last_modified_at = now() WHERE source_text = $$Pouvez-vous m'aider à trouver mon chemin ?$$;
UPDATE golden_dictionary SET target_text = $$Ku masangano, mukate ibumoso.$$, last_modified_at = now() WHERE source_text = $$Tournez à gauche au carrefour.$$;
UPDATE golden_dictionary SET target_text = $$Ni kure kuva hano ?$$, last_modified_at = now() WHERE source_text = $$C'est loin d'ici ?$$;

-- 🛒 Marché / achats
UPDATE golden_dictionary SET target_text = $$Isoko riri he ?$$, last_modified_at = now() WHERE source_text = $$Où est le marché ?$$;
UPDATE golden_dictionary SET target_text = $$Ibi bigura angahe ?$$, last_modified_at = now() WHERE source_text = $$Combien ça coûte ?$$;
UPDATE golden_dictionary SET target_text = $$Birahenze cyane.$$, last_modified_at = now() WHERE source_text = $$C'est trop cher.$$;
UPDATE golden_dictionary SET target_text = $$Mwamanura igiciro ?$$, last_modified_at = now() WHERE source_text = $$Pouvez-vous baisser le prix ?$$;
UPDATE golden_dictionary SET target_text = $$Ndashaka kugura inyanya.$$, last_modified_at = now() WHERE source_text = $$Je voudrais acheter des tomates.$$;
UPDATE golden_dictionary SET target_text = $$Mumpere ikilo kimwe cy'imineke.$$, last_modified_at = now() WHERE source_text = $$Donnez-moi un kilo de bananes.$$;
UPDATE golden_dictionary SET target_text = $$Mufite ibirayi ?$$, last_modified_at = now() WHERE source_text = $$Avez-vous des pommes de terre ?$$;
UPDATE golden_dictionary SET target_text = $$Ndatwara ibi byose.$$, last_modified_at = now() WHERE source_text = $$Je prends tout ça.$$;
UPDATE golden_dictionary SET target_text = $$Mufite amafaranga yo kugarura ?$$, last_modified_at = now() WHERE source_text = $$Avez-vous de la monnaie ?$$;
UPDATE golden_dictionary SET target_text = $$Nzagaruka ejo.$$, last_modified_at = now() WHERE source_text = $$Je reviendrai demain.$$;

-- 🍽️ Restaurant
UPDATE golden_dictionary SET target_text = $$Imeza y'abantu babiri, nyamuneka.$$, last_modified_at = now() WHERE source_text = $$Une table pour deux personnes, s'il vous plaît.$$;
UPDATE golden_dictionary SET target_text = $$Mutugira inama yo gufata iki ?$$, last_modified_at = now() WHERE source_text = $$Qu'est-ce que vous recommandez ?$$;
UPDATE golden_dictionary SET target_text = $$Ndashaka amazi, nyamuneka.$$, last_modified_at = now() WHERE source_text = $$Je voudrais de l'eau, s'il vous plaît.$$;
UPDATE golden_dictionary SET target_text = $$Konti, nyamuneka.$$, last_modified_at = now() WHERE source_text = $$L'addition, s'il vous plaît.$$;
UPDATE golden_dictionary SET target_text = $$Biraryoshye.$$, last_modified_at = now() WHERE source_text = $$C'est délicieux.$$;
UPDATE golden_dictionary SET target_text = $$Sindya inyama.$$, last_modified_at = now() WHERE source_text = $$Je suis végétarien.$$;
UPDATE golden_dictionary SET target_text = $$Mufite menu mu Gifaransa ?$$, last_modified_at = now() WHERE source_text = $$Avez-vous un menu en français ?$$;
UPDATE golden_dictionary SET target_text = $$Ndashaka gutumiza.$$, last_modified_at = now() WHERE source_text = $$Je voudrais commander.$$;
UPDATE golden_dictionary SET target_text = $$Mwazana undi mugati ?$$, last_modified_at = now() WHERE source_text = $$Pouvez-vous apporter encore du pain ?$$;
UPDATE golden_dictionary SET target_text = $$Murakoze, byari biryoshye cyane.$$, last_modified_at = now() WHERE source_text = $$Merci, c'était très bon.$$;

-- 🏥 Santé
UPDATE golden_dictionary SET target_text = $$Nkeneye muganga.$$, last_modified_at = now() WHERE source_text = $$J'ai besoin d'un médecin.$$;
UPDATE golden_dictionary SET target_text = $$Ibitaro biri he ?$$, last_modified_at = now() WHERE source_text = $$Où est l'hôpital ?$$;
UPDATE golden_dictionary SET target_text = $$Umutwe urandya.$$, last_modified_at = now() WHERE source_text = $$J'ai mal à la tête.$$;
UPDATE golden_dictionary SET target_text = $$Mfite umuriro.$$, last_modified_at = now() WHERE source_text = $$J'ai de la fièvre.$$;
UPDATE golden_dictionary SET target_text = $$Muhamagare imbangukiragutabara, nyamuneka.$$, last_modified_at = now() WHERE source_text = $$Appelez une ambulance, s'il vous plaît.$$;
UPDATE golden_dictionary SET target_text = $$Ngira allergie kuri penisiline.$$, last_modified_at = now() WHERE source_text = $$Je suis allergique à la pénicilline.$$;
UPDATE golden_dictionary SET target_text = $$Farumasi iri he ?$$, last_modified_at = now() WHERE source_text = $$Où est la pharmacie ?$$;
UPDATE golden_dictionary SET target_text = $$Nkeneye umuti ugabanya ububabare.$$, last_modified_at = now() WHERE source_text = $$J'ai besoin d'un médicament contre la douleur.$$;
UPDATE golden_dictionary SET target_text = $$Mwabisubiramo buhoro ?$$, last_modified_at = now() WHERE source_text = $$Pouvez-vous répéter lentement ?$$;
UPDATE golden_dictionary SET target_text = $$Ngomba kuruhuka.$$, last_modified_at = now() WHERE source_text = $$Je dois me reposer.$$;

-- 👋 Salutations / politesse
UPDATE golden_dictionary SET target_text = $$Muraho.$$, last_modified_at = now() WHERE source_text = $$Bonjour.$$;
UPDATE golden_dictionary SET target_text = $$Mwiriwe.$$, last_modified_at = now() WHERE source_text = $$Bonsoir.$$;
UPDATE golden_dictionary SET target_text = $$Ijoro ryiza.$$, last_modified_at = now() WHERE source_text = $$Bonne nuit.$$;
UPDATE golden_dictionary SET target_text = $$Mumeze mute ?$$, last_modified_at = now() WHERE source_text = $$Comment allez-vous ?$$;
UPDATE golden_dictionary SET target_text = $$Meze neza, murakoze.$$, last_modified_at = now() WHERE source_text = $$Je vais bien, merci.$$;
UPDATE golden_dictionary SET target_text = $$Murakoze cyane.$$, last_modified_at = now() WHERE source_text = $$Merci beaucoup.$$;
UPDATE golden_dictionary SET target_text = $$Nyamuneka.$$, last_modified_at = now() WHERE source_text = $$S'il vous plaît.$$;
UPDATE golden_dictionary SET target_text = $$Mumbabarire.$$, last_modified_at = now() WHERE source_text = $$Excusez-moi.$$;
UPDATE golden_dictionary SET target_text = $$Sinsobanukiwe.$$, last_modified_at = now() WHERE source_text = $$Je ne comprends pas.$$;
UPDATE golden_dictionary SET target_text = $$Muvuga Igifaransa ?$$, last_modified_at = now() WHERE source_text = $$Parlez-vous français ?$$;
UPDATE golden_dictionary SET target_text = $$Murabeho.$$, last_modified_at = now() WHERE source_text = $$Au revoir.$$;
UPDATE golden_dictionary SET target_text = $$Murakaza neza.$$, last_modified_at = now() WHERE source_text = $$Bienvenue.$$;
