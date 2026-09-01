import { Router } from 'express';
import { query, transaction } from '../db.js';
import { broadcast } from '../realtime.js';

export const recipesRouter = Router();

/** Normalises the ingredient list coming from the app. */
function cleanIngredients(raw) {
  if (!Array.isArray(raw)) return [];
  return raw
    .map((i) => ({ name: String(i?.name ?? '').trim(), amount: i?.amount?.trim() || null }))
    .filter((i) => i.name);
}

async function insertIngredients(conn, recipeId, ingredients) {
  if (!ingredients.length) return;
  await conn.query(
    'INSERT INTO recipe_ingredient (recipe_id, name, amount, sort_order) VALUES ?',
    [ingredients.map((ing, idx) => [recipeId, ing.name, ing.amount, idx + 1])]
  );
}

/** All recipes with their ingredients nested, so the app can expand a card offline. */
recipesRouter.get('/', async (_req, res) => {
  const recipes = await query(
    `SELECT id, title, notes, planned_for AS plannedFor, created_at AS createdAt
       FROM recipe ORDER BY planned_for IS NULL, planned_for ASC, title ASC`
  );
  if (!recipes.length) return res.json([]);

  const ingredients = await query(
    `SELECT id, recipe_id AS recipeId, name, amount
       FROM recipe_ingredient ORDER BY recipe_id, sort_order, id`
  );

  const byRecipe = new Map(recipes.map((r) => [r.id, []]));
  for (const ing of ingredients) byRecipe.get(ing.recipeId)?.push(ing);

  res.json(recipes.map((r) => ({ ...r, ingredients: byRecipe.get(r.id) })));
});

recipesRouter.post('/', async (req, res) => {
  const title = String(req.body?.title ?? '').trim();
  if (!title) return res.status(400).json({ error: 'title is required' });

  const ingredients = cleanIngredients(req.body?.ingredients);
  const id = await transaction(async (conn) => {
    const [result] = await conn.execute(
      'INSERT INTO recipe (title, notes, planned_for) VALUES (?, ?, ?)',
      [title, req.body?.notes?.trim() || null, req.body?.plannedFor || null]
    );
    await insertIngredients(conn, result.insertId, ingredients);
    return result.insertId;
  });

  broadcast('recipes.changed');
  res.status(201).json({ id });
});

/**
 * Replaces the recipe. Ingredients are swapped wholesale when supplied -
 * the app always sends the full list it has on screen, so there is no
 * per-row diffing to get wrong.
 */
recipesRouter.patch('/:id', async (req, res) => {
  const id = Number(req.params.id);

  const fields = [];
  const values = [];
  if (req.body?.title !== undefined) {
    const title = String(req.body.title).trim();
    if (!title) return res.status(400).json({ error: 'title cannot be empty' });
    fields.push('title = ?');
    values.push(title);
  }
  if (req.body?.notes !== undefined) {
    fields.push('notes = ?');
    values.push(req.body.notes?.trim() || null);
  }
  if (req.body?.plannedFor !== undefined) {
    fields.push('planned_for = ?');
    values.push(req.body.plannedFor || null);
  }

  const found = await transaction(async (conn) => {
    const [rows] = await conn.execute('SELECT id FROM recipe WHERE id = ?', [id]);
    if (!rows.length) return false;

    if (fields.length) {
      await conn.execute(`UPDATE recipe SET ${fields.join(', ')} WHERE id = ?`, [...values, id]);
    }
    if (req.body?.ingredients !== undefined) {
      await conn.execute('DELETE FROM recipe_ingredient WHERE recipe_id = ?', [id]);
      await insertIngredients(conn, id, cleanIngredients(req.body.ingredients));
    }
    return true;
  });
  if (!found) return res.status(404).json({ error: 'recipe not found' });

  broadcast('recipes.changed');
  res.json({ ok: true });
});

recipesRouter.delete('/:id', async (req, res) => {
  const result = await query('DELETE FROM recipe WHERE id = ?', [Number(req.params.id)]);
  if (!result.affectedRows) return res.status(404).json({ error: 'recipe not found' });

  broadcast('recipes.changed');
  res.json({ ok: true });
});
