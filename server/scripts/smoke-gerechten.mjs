import 'dotenv/config';

const BASE = 'http://127.0.0.1:4000';
const KEY = process.env.HOUSEHOLD_KEY;
let pass = 0; const fails = [];

const api = async (path, options = {}) => {
  const res = await fetch(BASE + path, {
    ...options,
    headers: { 'content-type': 'application/json', 'x-household-key': KEY },
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${options.method || 'GET'} ${path} -> ${res.status} ${text}`);
  return text ? JSON.parse(text) : null;
};
const check = (label, cond, detail = '') => {
  if (cond) { pass++; console.log('  ok    ' + label); }
  else { fails.push(label); console.log('  FAIL  ' + label + (detail ? ' -> ' + detail : '')); }
};
const post = (p, b) => api(p, { method: 'POST', body: JSON.stringify(b) });
const patch = (p, b) => api(p, { method: 'PATCH', body: JSON.stringify(b) });
const byId = (list, id) => list.find((i) => i.id === id);

// Start from a clean list so counts are unambiguous.
for (const i of await api('/api/items')) await api('/api/items/' + i.id, { method: 'DELETE' }).catch(() => {});

console.log('Gerecht with sub-items');
const dish = await post('/api/items', { name: 'Pasta pesto', addedBy: 'test' });
check('gerecht is a normal top-level row', dish.id > 0 && dish.parentId === null);

const penne = await post('/api/items', { name: 'Penne', quantity: '500 g', parentId: dish.id });
const pesto = await post('/api/items', { name: 'Pesto', quantity: '1 pot', parentId: dish.id });
check('sub-items point at the gerecht', penne.parentId === dish.id && pesto.parentId === dish.id);
check('sub-items order within the gerecht', penne.sortOrder === 1 && pesto.sortOrder === 2,
  `${penne.sortOrder}, ${pesto.sortOrder}`);

const deep = await fetch(BASE + '/api/items', {
  method: 'POST', headers: { 'content-type': 'application/json', 'x-household-key': KEY },
  body: JSON.stringify({ name: 'Nope', parentId: penne.id }),
});
check('nesting below a sub-item is refused', deep.status === 400, 'got ' + deep.status);

console.log('\nTicking');
await patch('/api/items/' + dish.id, { isChecked: true, by: 'Rick' });
let all = await api('/api/items');
check('ticking the gerecht ticks every sub-item',
  byId(all, penne.id).isChecked === true && byId(all, pesto.id).isChecked === true);

await patch('/api/items/' + penne.id, { isChecked: false, by: 'Rick' });
all = await api('/api/items');
check('un-ticking one sub-item un-ticks the gerecht', byId(all, dish.id).isChecked === false,
  JSON.stringify(byId(all, dish.id)));
check('the other sub-item is left alone', byId(all, pesto.id).isChecked === true);

await patch('/api/items/' + penne.id, { isChecked: true, by: 'Rick' });
all = await api('/api/items');
check('ticking the last sub-item ticks the gerecht again', byId(all, dish.id).isChecked === true);

console.log('\nReordering');
const a = await post('/api/items', { name: 'Aaa' });
const b = await post('/api/items', { name: 'Bbb' });
await post('/api/items/reorder', { ids: [b.id, a.id] });
all = await api('/api/items');
check('reorder writes the given order', byId(all, b.id).sortOrder < byId(all, a.id).sortOrder,
  `b=${byId(all, b.id).sortOrder} a=${byId(all, a.id).sortOrder}`);

console.log('\nDeleting');
await api('/api/items/' + dish.id, { method: 'DELETE' });
all = await api('/api/items');
check('deleting a gerecht cascades to its sub-items',
  !byId(all, penne.id) && !byId(all, pesto.id) && !byId(all, dish.id));
check('unrelated rows survive', !!byId(all, a.id) && !!byId(all, b.id));

for (const i of await api('/api/items')) await api('/api/items/' + i.id, { method: 'DELETE' }).catch(() => {});

console.log(`\n${pass} passed, ${fails.length} failed`);
if (fails.length) { console.log('Failed: ' + fails.join(', ')); process.exit(1); }
