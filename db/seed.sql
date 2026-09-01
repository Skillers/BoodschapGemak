-- Optional demo data so the app has something to show on first run.
USE boodschapgemak;

INSERT INTO shopping_item (name, quantity, added_by, sort_order) VALUES
  ('Melk',        '2 pakken', 'Rick', 1),
  ('Volkorenbrood', NULL,     'Rick', 2),
  ('Appels',      '6 stuks',  'Rick', 3);

INSERT INTO trip (label, status) VALUES ('Boodschappen zaterdag', 'open');

INSERT INTO recipe (title, notes, planned_for) VALUES
  ('Pasta pesto met kip', 'Kip eerst marineren.', NULL);

-- Pinned to a variable: LAST_INSERT_ID() would otherwise move on to the
-- ingredient rows partway through the multi-row insert below.
SET @recipe_id = LAST_INSERT_ID();

INSERT INTO recipe_ingredient (recipe_id, name, amount, sort_order) VALUES
  (@recipe_id, 'Penne',            '500 g', 1),
  (@recipe_id, 'Kipfilet',         '400 g', 2),
  (@recipe_id, 'Pesto',            '1 pot', 3),
  (@recipe_id, 'Parmezaanse kaas', '100 g', 4);
