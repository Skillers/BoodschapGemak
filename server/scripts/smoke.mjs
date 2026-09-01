/**
 * End-to-end check of the API against your real MySQL, plus the live socket.
 *
 * Run it once after setting up the database, before you go anywhere near
 * Android Studio: if this passes, every layer below the app is known good.
 *
 *   npm run smoke
 *
 * It writes real rows. Run it on a fresh database, or expect one stray closed
 * trip in your history afterwards.
 */
import 'dotenv/config';
import WebSocket from 'ws';

const BASE = process.env.SMOKE_URL || `http://127.0.0.1:${process.env.PORT || 4000}`;
const KEY = process.env.HOUSEHOLD_KEY;

if (!KEY) {
  console.error('HOUSEHOLD_KEY is not set. Run this from the server folder with .env in place.');
  process.exit(1);
}

let passed = 0;
const failures = [];

function check(label, condition, detail = '') {
  if (condition) {
    passed++;
    console.log(`  ok    ${label}`);
  } else {
    failures.push(label);
    console.log(`  FAIL  ${label}${detail ? ' -> ' + detail : ''}`);
  }
}

async function api(path, options = {}) {
  const res = await fetch(BASE + path, {
    ...options,
    headers: { 'content-type': 'application/json', 'x-household-key': KEY },
  });
  const text = await res.text();
  const body = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error(`${options.method || 'GET'} ${path} -> ${res.status} ${text}`);
  return body;
}

/** Collects push events so we can assert the live path really fired. */
function openSocket() {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`${BASE.replace(/^http/, 'ws')}/live?key=${encodeURIComponent(KEY)}`);
    const events = [];
    ws.on('message', (raw) => events.push(JSON.parse(raw.toString())));
    ws.on('open', () => resolve({ ws, events }));
    ws.on('error', reject);
    setTimeout(() => reject(new Error('socket did not open in 5s')), 5000);
  });
}

const settle = () => new Promise((r) => setTimeout(r, 250));

async function main() {
  console.log(`Testing ${BASE}\n`);

  console.log('Reachability and auth');
  const health = await (await fetch(BASE + '/api/health')).json();
  check('health responds', health.ok === true);

  const unauth = await fetch(BASE + '/api/items', { headers: { 'x-household-key': 'wrong' } });
  check('wrong household key is rejected', unauth.status === 401, `got ${unauth.status}`);

  const { ws, events } = await openSocket();
  check('live socket accepts the right key', true);

  console.log('\nShopping list');
  const created = await api('/api/items', {
    method: 'POST',
    body: JSON.stringify({ name: 'Smoketest melk', quantity: '2 pakken', addedBy: 'smoke' }),
  });
  check('item is created', created.id > 0, JSON.stringify(created));
  check('item comes back with its fields', created.name === 'Smoketest melk' && created.quantity === '2 pakken');
  check('isChecked is a real boolean, not 0/1', created.isChecked === false, typeof created.isChecked);

  await settle();
  check('creating pushed item.upserted over the socket',
    events.some((e) => e.type === 'item.upserted' && e.item?.id === created.id));
  check('the push carried the row itself, so no refetch is needed',
    events.find((e) => e.type === 'item.upserted')?.item?.name === 'Smoketest melk');

  const claimed = await api(`/api/items/${created.id}`, {
    method: 'PATCH',
    body: JSON.stringify({ claimedBy: 'Rick' }),
  });
  check('claiming records who is walking to the shelf', claimed.claimedBy === 'Rick', JSON.stringify(claimed));

  const released = await api(`/api/items/${created.id}`, {
    method: 'PATCH',
    body: JSON.stringify({ claimedBy: '' }),
  });
  check('an empty claim releases it', released.claimedBy === null, JSON.stringify(released));

  await api(`/api/items/${created.id}`, {
    method: 'PATCH',
    body: JSON.stringify({ claimedBy: 'Rick' }),
  });
  const ticked = await api(`/api/items/${created.id}`, {
    method: 'PATCH',
    body: JSON.stringify({ isChecked: true, by: 'Rick' }),
  });
  check('ticking it off records who did it', ticked.checkedBy === 'Rick');
  check('ticking it off clears the claim', ticked.claimedBy === null, JSON.stringify(ticked));

  const swept = await api('/api/items/clear-checked', { method: 'POST' });
  check('clear-checked removes ticked items', swept.deleted >= 1, JSON.stringify(swept));
  const afterSweep = await api('/api/items');
  check('the swept item is gone', !afterSweep.some((i) => i.id === created.id));

  console.log('\nRunning total');
  const trip = await api('/api/trips/current');
  check('an open trip exists or is created on demand', trip.id > 0 && trip.status === 'open');

  const startingTotal = trip.totalCents;
  const afterAdd = await api('/api/trips/current/entries', {
    method: 'POST',
    body: JSON.stringify({ amountCents: 1234, note: 'smoketest', addedBy: 'smoke' }),
  });
  check('adding an amount moves the total by exactly that much',
    afterAdd.totalCents === startingTotal + 1234, `${startingTotal} -> ${afterAdd.totalCents}`);

  const entry = afterAdd.entries.find((e) => e.note === 'smoketest');
  const afterUndo = await api(`/api/trips/current/entries/${entry.id}`, { method: 'DELETE' });
  check('undoing an amount puts the total back', afterUndo.totalCents === startingTotal);

  await api('/api/trips/current/entries', {
    method: 'POST',
    body: JSON.stringify({ amountCents: 500, addedBy: 'smoke' }),
  });
  const closed = await api('/api/trips/current/close', { method: 'POST' });
  check('closing banks the trip', closed.ok === true);

  const fresh = await api('/api/trips/current');
  check('a fresh trip starts at zero', fresh.totalCents === 0 && fresh.id !== trip.id);

  const history = await api('/api/trips');
  const banked = history.find((t) => t.id === trip.id);
  check('the closed trip is kept in history with its total',
    banked?.status === 'closed' && banked.totalCents === startingTotal + 500,
    JSON.stringify(banked));

  console.log('\nRecipes');
  const recipe = await api('/api/recipes', {
    method: 'POST',
    body: JSON.stringify({
      title: 'Smoketest pasta',
      notes: 'weggooien na de test',
      ingredients: [{ name: 'Penne', amount: '500 g' }, { name: 'Pesto', amount: '1 pot' }],
    }),
  });
  const recipes = await api('/api/recipes');
  const stored = recipes.find((r) => r.id === recipe.id);
  check('recipe is stored with its ingredients', stored?.ingredients?.length === 2, JSON.stringify(stored));
  check('ingredients keep the order they were sent in',
    stored?.ingredients?.[0]?.name === 'Penne' && stored?.ingredients?.[1]?.name === 'Pesto');

  await api(`/api/recipes/${recipe.id}`, {
    method: 'PATCH',
    body: JSON.stringify({ title: 'Smoketest pasta 2', ingredients: [{ name: 'Spaghetti' }] }),
  });
  const updated = (await api('/api/recipes')).find((r) => r.id === recipe.id);
  check('updating replaces the ingredient list wholesale',
    updated.title === 'Smoketest pasta 2' && updated.ingredients.length === 1,
    JSON.stringify(updated));

  await api(`/api/recipes/${recipe.id}`, { method: 'DELETE' });
  check('recipe deletes', !(await api('/api/recipes')).some((r) => r.id === recipe.id));

  ws.close();

  console.log(`\n${passed} passed, ${failures.length} failed`);
  if (failures.length) {
    console.log('Failed: ' + failures.join(', '));
    process.exit(1);
  }
  console.log('Everything below the Android app works.');
  process.exit(0);
}

main().catch((err) => {
  console.error('\nSmoke test could not finish:', err.message);
  console.error('Is the server running? Start it with: npm start');
  process.exit(1);
});
