import { Router } from 'express';
import { query } from '../db.js';
import { broadcast } from '../realtime.js';

export const tripsRouter = Router();

function dutchDateLabel(date = new Date()) {
  return `Boodschappen ${date.toLocaleDateString('nl-NL', {
    weekday: 'long', day: 'numeric', month: 'long',
  })}`;
}

async function findOpenTrip() {
  const rows = await query(
    `SELECT id, label, status, started_at AS startedAt
       FROM trip WHERE status = 'open' LIMIT 1`
  );
  return rows[0] ?? null;
}

/**
 * The open trip, its entries and its running total. Creates a trip on the
 * fly so the counter always has somewhere to put money; the unique index
 * on trip.open_marker makes the create safe when both phones ask at once.
 */
async function openTripWithEntries() {
  let trip = await findOpenTrip();
  if (!trip) {
    try {
      const result = await query('INSERT INTO trip (label) VALUES (?)', [dutchDateLabel()]);
      trip = { id: result.insertId, label: dutchDateLabel(), status: 'open', startedAt: new Date() };
    } catch (err) {
      if (err.code !== 'ER_DUP_ENTRY') throw err;
      trip = await findOpenTrip(); // the other phone created it first
    }
  }

  const entries = await query(
    `SELECT id, amount_cents AS amountCents, note, added_by AS addedBy, created_at AS createdAt
       FROM trip_entry WHERE trip_id = ? ORDER BY id DESC`,
    [trip.id]
  );
  const totalCents = entries.reduce((sum, e) => sum + e.amountCents, 0);
  return { ...trip, entries, totalCents };
}

tripsRouter.get('/current', async (_req, res) => {
  res.json(await openTripWithEntries());
});

/** Trip history, newest first, each with its total. */
tripsRouter.get('/', async (_req, res) => {
  const rows = await query(
    `SELECT t.id, t.label, t.status, t.started_at AS startedAt, t.closed_at AS closedAt,
            COALESCE(SUM(e.amount_cents), 0) AS totalCents,
            COUNT(e.id) AS entryCount
       FROM trip t
       LEFT JOIN trip_entry e ON e.trip_id = t.id
      GROUP BY t.id
      ORDER BY t.started_at DESC
      LIMIT 100`
  );
  res.json(rows.map((r) => ({ ...r, totalCents: Number(r.totalCents) })));
});

/** Adds an amount to the running total - the "+ EUR" tap. */
tripsRouter.post('/current/entries', async (req, res) => {
  const amountCents = Math.round(Number(req.body?.amountCents));
  if (!Number.isFinite(amountCents) || amountCents === 0) {
    return res.status(400).json({ error: 'amountCents must be a non-zero number' });
  }

  const trip = await openTripWithEntries();
  await query(
    'INSERT INTO trip_entry (trip_id, amount_cents, note, added_by) VALUES (?, ?, ?, ?)',
    [trip.id, amountCents, req.body?.note?.trim() || null, String(req.body?.addedBy ?? '').slice(0, 50)]
  );

  broadcast('trip.changed');
  res.status(201).json(await openTripWithEntries());
});

/** Undo a mistyped amount. */
tripsRouter.delete('/current/entries/:entryId', async (req, res) => {
  const trip = await findOpenTrip();
  if (!trip) return res.status(404).json({ error: 'no open trip' });

  const result = await query(
    'DELETE FROM trip_entry WHERE id = ? AND trip_id = ?',
    [Number(req.params.entryId), trip.id]
  );
  if (!result.affectedRows) return res.status(404).json({ error: 'entry not found' });

  broadcast('trip.changed');
  res.json(await openTripWithEntries());
});

/** Finishes the trip so the next one starts a fresh total. */
tripsRouter.post('/current/close', async (_req, res) => {
  const trip = await findOpenTrip();
  if (!trip) return res.status(404).json({ error: 'no open trip' });

  await query(
    "UPDATE trip SET status = 'closed', closed_at = CURRENT_TIMESTAMP WHERE id = ?",
    [trip.id]
  );

  // Finishing the shop also sweeps what is already in the cart, so the next
  // trip starts on a clean list. The app has no separate sweep button.
  const swept = await query('DELETE FROM shopping_item WHERE is_checked = 1');

  broadcast('trip.changed');
  broadcast('items.reload');
  res.json({ ok: true, closedTripId: trip.id, clearedItems: swept.affectedRows });
});

/** Renames the open trip, e.g. "Weekend AH" instead of the date. */
tripsRouter.patch('/current', async (req, res) => {
  const label = String(req.body?.label ?? '').trim();
  if (!label) return res.status(400).json({ error: 'label is required' });

  const trip = await findOpenTrip();
  if (!trip) return res.status(404).json({ error: 'no open trip' });

  await query('UPDATE trip SET label = ? WHERE id = ?', [label, trip.id]);
  broadcast('trip.changed');
  res.json({ ok: true });
});
