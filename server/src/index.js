import 'dotenv/config';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import express from 'express';
import cors from 'cors';

import { pool } from './db.js';
import { attachRealtime } from './realtime.js';
import { itemsRouter } from './routes/items.js';
import { tripsRouter } from './routes/trips.js';
import { recipesRouter } from './routes/recipes.js';

const PORT = Number(process.env.PORT || 4000);
const HOUSEHOLD_KEY = process.env.HOUSEHOLD_KEY;

if (!HOUSEHOLD_KEY) {
  console.error('HOUSEHOLD_KEY is not set. Copy .env.example to .env and fill it in.');
  process.exit(1);
}

const app = express();
app.use(cors());
app.use(express.json({ limit: '256kb' }));

// Unauthenticated, so the app can show "server reachable" before logging in.
app.get('/api/health', (_req, res) => res.json({ ok: true }));

// Browser test client at http://<server>:4000/ - lets you exercise the list
// and the live push from two tabs, before any of the Android tooling exists.
const publicDir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'public');
app.use(express.static(publicDir));

app.use('/api', (req, res, next) => {
  if (req.get('x-household-key') !== HOUSEHOLD_KEY) {
    return res.status(401).json({ error: 'bad or missing x-household-key header' });
  }
  next();
});

app.use('/api/items', itemsRouter);
app.use('/api/trips', tripsRouter);
app.use('/api/recipes', recipesRouter);

// Express 5 forwards rejected promises from async handlers to here.
app.use((err, _req, res, _next) => {
  console.error(err);
  res.status(500).json({ error: 'server error' });
});

const server = http.createServer(app);
attachRealtime(server, HOUSEHOLD_KEY);

async function start() {
  await pool.query('SELECT 1'); // fail loudly now rather than on the first tap
  server.listen(PORT, '0.0.0.0', () => {
    console.log(`BoodschapGemak API listening on http://0.0.0.0:${PORT}`);
    console.log(`Realtime socket on ws://0.0.0.0:${PORT}/live?key=...`);
  });
}

start().catch((err) => {
  console.error('Could not reach MySQL:', err.message);
  process.exit(1);
});
