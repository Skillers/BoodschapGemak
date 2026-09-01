-- BoodschapGemak - MySQL schema
-- Run this in MySQL Workbench (File > Open SQL Script, then the lightning bolt).

CREATE DATABASE IF NOT EXISTS boodschapgemak
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE boodschapgemak;

-- ---------------------------------------------------------------
-- Shopping list: one shared list, both phones see the same rows.
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS shopping_item (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  -- A gerecht is just a row other rows point at. One level deep only.
  parent_id   INT           NULL,
  name        VARCHAR(200)  NOT NULL,
  quantity    VARCHAR(60)   NULL,
  is_checked  TINYINT(1)    NOT NULL DEFAULT 0,
  added_by    VARCHAR(50)   NOT NULL DEFAULT '',
  checked_by  VARCHAR(50)   NULL,
  -- Set the moment someone heads for the aisle, cleared when the item is
  -- ticked off. This is what stops you both walking to the same shelf.
  claimed_by  VARCHAR(50)   NULL,
  claimed_at  TIMESTAMP     NULL,
  sort_order  INT           NOT NULL DEFAULT 0,
  created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                            ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_shopping_item_parent
    FOREIGN KEY (parent_id) REFERENCES shopping_item(id) ON DELETE CASCADE,
  INDEX idx_shopping_item_parent (parent_id, sort_order),
  INDEX idx_shopping_item_checked (is_checked, sort_order)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- Shopping trips + the free-form running total.
-- A trip is one visit to the shop. While it is 'open' you tap
-- amounts into it; the total is the sum of its entries.
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  label       VARCHAR(120)  NOT NULL,
  status      ENUM('open','closed') NOT NULL DEFAULT 'open',
  started_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  closed_at   TIMESTAMP     NULL,
  -- 1 while the trip is open, NULL once closed. The unique index below
  -- therefore allows many closed trips but only ever one open one, so
  -- two phones tapping "start trip" at the same time cannot both win.
  open_marker TINYINT(1) AS (IF(status = 'open', 1, NULL)) STORED,
  UNIQUE KEY uniq_single_open_trip (open_marker),
  INDEX idx_trip_status (status, started_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS trip_entry (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  trip_id       INT           NOT NULL,
  amount_cents  INT           NOT NULL,
  note          VARCHAR(120)  NULL,
  added_by      VARCHAR(50)   NOT NULL DEFAULT '',
  created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_trip_entry_trip
    FOREIGN KEY (trip_id) REFERENCES trip(id) ON DELETE CASCADE,
  INDEX idx_trip_entry_trip (trip_id, created_at)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- Recipes for the week + their ingredients.
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS recipe (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  title       VARCHAR(200)  NOT NULL,
  notes       TEXT          NULL,
  planned_for DATE          NULL,
  created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                            ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_recipe_planned (planned_for)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS recipe_ingredient (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  recipe_id   INT           NOT NULL,
  name        VARCHAR(200)  NOT NULL,
  amount      VARCHAR(60)   NULL,
  sort_order  INT           NOT NULL DEFAULT 0,
  CONSTRAINT fk_recipe_ingredient_recipe
    FOREIGN KEY (recipe_id) REFERENCES recipe(id) ON DELETE CASCADE,
  INDEX idx_recipe_ingredient_recipe (recipe_id, sort_order)
) ENGINE=InnoDB;
