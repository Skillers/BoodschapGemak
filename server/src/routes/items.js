import { Router } from 'express';
import { pool, query } from '../db.js';
import { broadcast } from '../realtime.js';

export const itemsRouter = Router();

const SELECT_COLUMNS = `id, parent_id AS parentId, name, quantity, is_checked AS isChecked,
                        added_by AS addedBy, checked_by AS checkedBy, claimed_by AS claimedBy,
                        sort_order AS sortOrder`;

// MySQL hands TINYINT(1) back as 0/1; the app wants a real boolean.
const toItem = (row) => ({ ...row, isChecked: Boolean(row.isChecked) });

/**
 * Flat list, ordered within each sibling group. The app builds the tree - a
 * gerecht is simply a row that other rows point at with parent_id.
 */
const listItems = async () => {
  const rows = await query(
    `SELECT ${SELECT_COLUMNS} FROM shopping_item ORDER BY sort_order ASC, id ASC`
  );
  return rows.map(toItem);
};

const findItem = async (id) => {
  const rows = await query(`SELECT ${SELECT_COLUMNS} FROM shopping_item WHERE id = ?`, [id]);
  return rows[0] ? toItem(rows[0]) : null;
};

const childCount = async (id) => {
  const [{ n }] = await query('SELECT COUNT(*) AS n FROM shopping_item WHERE parent_id = ?', [id]);
  return n;
};

/**
 * Pushes the row itself rather than a "go refetch" ping. That saves the other
 * phone a full round trip, which is most of the delay you actually feel when
 * you are both stood in different aisles. Only safe when exactly one row
 * changed - anything touching a parent and its children says reload instead.
 */
const pushItem = (item) => broadcast('item.upserted', { item });

itemsRouter.get('/', async (_req, res) => {
  res.json(await listItems());
});

itemsRouter.post('/', async (req, res) => {
  const name = String(req.body?.name ?? '').trim();
  if (!name) return res.status(400).json({ error: 'name is required' });

  const quantity = String(req.body?.quantity ?? '').trim().slice(0, 60) || null;
  const addedBy = String(req.body?.addedBy ?? '').slice(0, 50);

  // A sub-item of a gerecht. Verified so a bad id cannot orphan a row.
  const rawParent = req.body?.parentId;
  let parentId = null;
  if (rawParent !== undefined && rawParent !== null && rawParent !== '') {
    parentId = Number(rawParent);
    if (!Number.isInteger(parentId)) return res.status(400).json({ error: 'parentId must be an id' });
    const parent = await findItem(parentId);
    if (!parent) return res.status(400).json({ error: 'parentId does not exist' });
    // One level only: a sub-item cannot itself hold sub-items.
    if (parent.parentId) return res.status(400).json({ error: 'cannot nest below a sub-item' });
  }

  // Position within its own sibling group, so children order inside a gerecht.
  const [{ next }] = parentId === null
    ? await query('SELECT COALESCE(MAX(sort_order), 0) + 1 AS next FROM shopping_item WHERE parent_id IS NULL')
    : await query('SELECT COALESCE(MAX(sort_order), 0) + 1 AS next FROM shopping_item WHERE parent_id = ?', [parentId]);

  const result = await query(
    'INSERT INTO shopping_item (parent_id, name, quantity, added_by, sort_order) VALUES (?, ?, ?, ?, ?)',
    [parentId, name, quantity, addedBy, next]
  );

  const item = await findItem(result.insertId);
  pushItem(item);
  res.status(201).json(item);
});

itemsRouter.patch('/:id', async (req, res) => {
  const id = Number(req.params.id);
  const existing = await findItem(id);
  if (!existing) return res.status(404).json({ error: 'item not found' });

  const fields = [];
  const values = [];
  let touchesFamily = false;

  if (req.body?.name !== undefined) {
    const name = String(req.body.name).trim();
    if (!name) return res.status(400).json({ error: 'name cannot be empty' });
    fields.push('name = ?');
    values.push(name);
  }
  if (req.body?.quantity !== undefined) {
    fields.push('quantity = ?');
    values.push(String(req.body.quantity ?? '').trim() || null);
  }
  if (req.body?.isChecked !== undefined) {
    const checked = Boolean(req.body.isChecked);
    const by = checked ? String(req.body?.by ?? '').slice(0, 50) : null;
    // Once it is in the cart the claim has served its purpose, so it goes.
    fields.push('is_checked = ?', 'checked_by = ?', 'claimed_by = ?', 'claimed_at = ?');
    values.push(checked ? 1 : 0, by, null, null);

    if (existing.parentId === null) {
      // Ticking a gerecht takes everything under it along.
      if (await childCount(id)) {
        touchesFamily = true;
        await query(
          `UPDATE shopping_item
              SET is_checked = ?, checked_by = ?, claimed_by = NULL, claimed_at = NULL
            WHERE parent_id = ?`,
          [checked ? 1 : 0, by, id]
        );
      }
    } else {
      touchesFamily = true;
    }
  } else if (req.body?.claimedBy !== undefined) {
    // "" releases the claim, a name takes it.
    const claimedBy = String(req.body.claimedBy).trim().slice(0, 50) || null;
    fields.push('claimed_by = ?', 'claimed_at = ?');
    values.push(claimedBy, claimedBy ? new Date() : null);
  }
  if (!fields.length) return res.status(400).json({ error: 'nothing to update' });

  values.push(id);
  await query(`UPDATE shopping_item SET ${fields.join(', ')} WHERE id = ?`, values);

  // A gerecht is ticked exactly when everything under it is. Recomputed here
  // rather than in the app so both phones cannot disagree about it.
  if (existing.parentId !== null && req.body?.isChecked !== undefined) {
    const [{ open }] = await query(
      'SELECT COUNT(*) AS open FROM shopping_item WHERE parent_id = ? AND is_checked = 0',
      [existing.parentId]
    );
    await query(
      'UPDATE shopping_item SET is_checked = ?, checked_by = ? WHERE id = ?',
      [open ? 0 : 1, open ? null : String(req.body?.by ?? '').slice(0, 50), existing.parentId]
    );
  }

  if (touchesFamily) {
    broadcast('items.reload');
    return res.json(await findItem(id));
  }

  const item = await findItem(id);
  pushItem(item);
  res.json(item);
});

itemsRouter.delete('/:id', async (req, res) => {
  const id = Number(req.params.id);
  const hadChildren = await childCount(id);

  const result = await query('DELETE FROM shopping_item WHERE id = ?', [id]);
  if (!result.affectedRows) return res.status(404).json({ error: 'item not found' });

  // The foreign key cascades, so a gerecht takes its sub-items with it and the
  // other phone has to reload rather than remove one row.
  if (hadChildren) broadcast('items.reload');
  else broadcast('item.deleted', { id });

  res.json({ ok: true });
});

/**
 * Writes a new order for one sibling group. The app sends the ids in the order
 * they now appear on screen after a drag; position is simply the index.
 */
itemsRouter.post('/reorder', async (req, res) => {
  const ids = (Array.isArray(req.body?.ids) ? req.body.ids : []).map(Number).filter(Number.isInteger);
  if (!ids.length) return res.status(400).json({ error: 'ids is required' });

  await pool.query(
    `UPDATE shopping_item
        SET sort_order = FIELD(id, ${ids.map(() => '?').join(',')})
      WHERE id IN (${ids.map(() => '?').join(',')})`,
    [...ids, ...ids]
  );

  broadcast('items.reload');
  res.json({ ok: true, ordered: ids.length });
});

/** Sweeps everything that is already in the cart off the list. */
itemsRouter.post('/clear-checked', async (_req, res) => {
  const result = await query('DELETE FROM shopping_item WHERE is_checked = 1');
  broadcast('items.reload');
  res.json({ deleted: result.affectedRows });
});
