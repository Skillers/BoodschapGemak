import { Router } from 'express';
import { pool, query } from '../db.js';
import { broadcast } from '../realtime.js';

export const itemsRouter = Router();

const SELECT_COLUMNS = `id, name, quantity, dish, is_checked AS isChecked, added_by AS addedBy,
                        checked_by AS checkedBy, claimed_by AS claimedBy, sort_order AS sortOrder`;

// MySQL hands TINYINT(1) back as 0/1; the app wants a real boolean.
const toItem = (row) => ({ ...row, isChecked: Boolean(row.isChecked) });

const listItems = async () => {
  const rows = await query(
    `SELECT ${SELECT_COLUMNS}
       FROM shopping_item
      ORDER BY is_checked ASC, sort_order ASC, id ASC`
  );
  return rows.map(toItem);
};

const findItem = async (id) => {
  const rows = await query(`SELECT ${SELECT_COLUMNS} FROM shopping_item WHERE id = ?`, [id]);
  return rows[0] ? toItem(rows[0]) : null;
};

/**
 * Pushes the row itself rather than a "go refetch" ping. That saves the other
 * phone a full round trip, which is most of the delay you actually feel when
 * you are both stood in different aisles.
 */
const pushItem = (item) => broadcast('item.upserted', { item });

itemsRouter.get('/', async (_req, res) => {
  res.json(await listItems());
});

itemsRouter.post('/', async (req, res) => {
  const name = String(req.body?.name ?? '').trim();
  if (!name) return res.status(400).json({ error: 'name is required' });

  const quantity = req.body?.quantity?.trim() || null;
  const dish = String(req.body?.dish ?? '').trim().slice(0, 120) || null;
  const addedBy = String(req.body?.addedBy ?? '').slice(0, 50);

  const [{ next }] = await query(
    'SELECT COALESCE(MAX(sort_order), 0) + 1 AS next FROM shopping_item'
  );
  const result = await query(
    'INSERT INTO shopping_item (name, quantity, dish, added_by, sort_order) VALUES (?, ?, ?, ?, ?)',
    [name, quantity, dish, addedBy, next]
  );

  const item = await findItem(result.insertId);
  pushItem(item);
  res.status(201).json(item);
});

/**
 * Adds a whole dish at once: every ingredient becomes an ordinary list row,
 * tagged with the dish so the list says why it is there. One request rather
 * than one per ingredient, so the other phone sees the lot appear together.
 */
itemsRouter.post('/dish', async (req, res) => {
  const dish = String(req.body?.dish ?? '').trim().slice(0, 120);
  if (!dish) return res.status(400).json({ error: 'dish is required' });

  const ingredients = (Array.isArray(req.body?.ingredients) ? req.body.ingredients : [])
    .map((i) => ({
      name: String(i?.name ?? '').trim().slice(0, 200),
      quantity: String(i?.quantity ?? '').trim().slice(0, 60) || null,
    }))
    .filter((i) => i.name);
  if (!ingredients.length) return res.status(400).json({ error: 'at least one ingredient is required' });

  const addedBy = String(req.body?.addedBy ?? '').slice(0, 50);
  const [{ next }] = await query(
    'SELECT COALESCE(MAX(sort_order), 0) + 1 AS next FROM shopping_item'
  );

  // pool.query, not the prepared-statement helper: bulk "VALUES ?" is a
  // driver-side expansion that execute() does not do.
  await pool.query(
    'INSERT INTO shopping_item (name, quantity, dish, added_by, sort_order) VALUES ?',
    [ingredients.map((ing, idx) => [ing.name, ing.quantity, dish, addedBy, next + idx])]
  );

  broadcast('items.reload');
  res.status(201).json({ dish, added: ingredients.length });
});

itemsRouter.patch('/:id', async (req, res) => {
  const id = Number(req.params.id);
  const fields = [];
  const values = [];

  if (req.body?.name !== undefined) {
    const name = String(req.body.name).trim();
    if (!name) return res.status(400).json({ error: 'name cannot be empty' });
    fields.push('name = ?');
    values.push(name);
  }
  if (req.body?.quantity !== undefined) {
    fields.push('quantity = ?');
    values.push(req.body.quantity?.trim() || null);
  }
  if (req.body?.isChecked !== undefined) {
    const checked = Boolean(req.body.isChecked);
    // Once it is in the cart the claim has served its purpose, so it goes.
    fields.push('is_checked = ?', 'checked_by = ?', 'claimed_by = ?', 'claimed_at = ?');
    values.push(checked ? 1 : 0, checked ? String(req.body?.by ?? '').slice(0, 50) : null, null, null);
  } else if (req.body?.claimedBy !== undefined) {
    // "" releases the claim, a name takes it.
    const claimedBy = String(req.body.claimedBy).trim().slice(0, 50) || null;
    fields.push('claimed_by = ?', 'claimed_at = ?');
    values.push(claimedBy, claimedBy ? new Date() : null);
  }
  if (!fields.length) return res.status(400).json({ error: 'nothing to update' });

  values.push(id);
  const result = await query(
    `UPDATE shopping_item SET ${fields.join(', ')} WHERE id = ?`, values
  );
  if (!result.affectedRows) return res.status(404).json({ error: 'item not found' });

  const item = await findItem(id);
  pushItem(item);
  res.json(item);
});

itemsRouter.delete('/:id', async (req, res) => {
  const id = Number(req.params.id);
  const result = await query('DELETE FROM shopping_item WHERE id = ?', [id]);
  if (!result.affectedRows) return res.status(404).json({ error: 'item not found' });

  broadcast('item.deleted', { id });
  res.json({ ok: true });
});

/** Sweeps everything that is already in the cart off the list. */
itemsRouter.post('/clear-checked', async (_req, res) => {
  const result = await query('DELETE FROM shopping_item WHERE is_checked = 1');
  broadcast('items.reload');
  res.json({ deleted: result.affectedRows });
});
