# BoodschapGemak

A shared shopping list for two people, with a running cost total and a place to
keep the week's recipes. Both phones see the same list and updates arrive live.

```
Android app  <-- REST + WebSocket -->  Node API server  <-- SQL -->  MySQL
```

The middle layer is not optional: an Android app cannot speak MySQL's wire
protocol safely (it would need the database password on the phone), and it is
also what pushes the live updates. Your database stays an ordinary MySQL schema
you can open in MySQL Workbench.

| Folder    | What it is |
|-----------|------------|
| `db/`     | `schema.sql` and optional `seed.sql`, to run in MySQL Workbench |
| `server/` | Node + Express + `ws` API that owns all database access |
| `android/`| Kotlin + Jetpack Compose app |

## What it does

**Shopping list** — one shared list. Add a product with an optional amount, tap
a row to tick it off, long-press to edit or delete. Ticked items sink to the
bottom and show who put them in the cart; one button sweeps them off the list.

Each unticked row also has a **claim chip**. Tap "Pak ik" when you set off for
the shelf and the row lights up on the other phone with your name on it, before
you have the thing in your hand. Ticking the item off clears the claim. This is
the part that stops you both walking to the same aisle — the network was never
the slow bit, the walk was.

**Running total** — a free-form counter, deliberately not tied to list items.
Type what something cost, tap `+`, and the total climbs. Each amount stays
listed so a mistyped one can be undone. "Boodschappen afsluiten" banks the
total and starts a fresh one; past trips are kept with their totals.

**Recipes** — write down what you want to eat this week with its ingredients.
Cards open and close so ingredient lists stay out of the way until wanted.

## Setting it up, in order

Work upwards. Each step proves one layer, so when something breaks you know
which one it was. Android Studio comes last on purpose — it is the biggest
install and the least-proven code.

### 1. The database

You have MySQL Workbench but not MySQL Server itself — nothing is listening on
3306. Install **MySQL Community Server 8.x** (the MySQL Installer bundles both),
then open and run `db/schema.sql` in Workbench. You should end up with five
tables under `boodschapgemak`. Run `db/seed.sql` too if you want a few rows to
look at.

### 2. The server

Copy `server/.env.example` to `server/.env` and fill in your MySQL password and
a `HOUSEHOLD_KEY` — any long random string, the same one on both phones.

```bash
cd server && npm install && npm start
```

It refuses to start if MySQL is unreachable, so a clean startup line means the
database connection works. If it exits, the message says why.

### 3. The whole API, automatically

In a second terminal:

```bash
cd server && npm run smoke
```

This drives every endpoint against your real database and asserts on the
answers — that a claim is recorded and then cleared by ticking off, that
undoing an amount puts the total back exactly, that closing a trip banks it and
opens a fresh one, that the live socket fires on a write. If it prints
`Everything below the Android app works`, the backend is done. It writes real
rows, so run it before you start using the list for real.

### 4. The live behaviour, from a browser

Open `http://<your-pc-ip>:4000/` — the server serves a small test client. Open
it in **two windows side by side**, or on both phones, and use it: add
something in one, watch it appear in the other; tap "Pak ik" and watch the row
light up on the other screen with your name.

This is the fastest way to see the thing actually work, and it needs no Android
tooling at all. It also shows the API round trip in milliseconds in the header,
so you can measure your real latency over 5G before committing to anything. If
you like, use it on your phones as-is for a shop or two while the app gets
built — it is deliberately usable, not just a debug page.

### 5. The Android app

Only now install **Android Studio** — it brings its own JDK and the Android SDK,
neither of which is on your machine yet. `File > Open` the `android/` folder and
let it sync; it downloads Gradle 8.11.1 as pinned in
`gradle/wrapper/gradle-wrapper.properties` and fills in the wrapper jar itself.
Then run the app onto your phone over USB or wireless debugging.

The Kotlin has never been compiled — there is no JDK on this machine — so treat
the first build as a debugging session rather than a formality. Every
capitalised identifier across the twelve source files has been checked to
resolve to an import or a local declaration, which rules out the usual cause,
but version-level API drift can only be found by a real compiler.

On first launch it asks for three things:

- **Server-adres** — `http://<your-pc-ip>:4000`. On home wifi that is the LAN
  address from `ipconfig`; once Tailscale is set up, use the Tailscale address
  instead and it works everywhere. Never `localhost` — to the phone, that means
  the phone.
- **Huissleutel** — the `HOUSEHOLD_KEY` from `server/.env`.
- **Jouw naam** — so the other person sees who ticked and claimed what.

The dot beside the title is the live connection: green connected, amber
reconnecting, grey offline.

If steps 1–4 passed, anything that goes wrong from here is in the app, not
underneath it.

## Reaching your PC from the supermarket

The server runs on your PC, but the phones need it from mobile data. Use
**Tailscale** — free for personal use, and the best option here for three
reasons: it punches through your router without port forwarding, it exposes
nothing to the public internet, and it usually negotiates a *direct* connection
between phone and PC rather than relaying through someone else's servers, which
keeps the latency down.

1. Install Tailscale on the PC and on both phones, all signed into one account.
2. Read the PC's Tailscale address (`100.x.y.z`) from its admin console.
3. Use `http://100.x.y.z:4000` as the server address in the app.

That address works identically at home and in the shop, so there is nothing to
switch, and there is nothing to configure on the router either. Your PC does
have to be awake — check its sleep settings.

Worth checking once, since latency is the point here:

```bash
tailscale ping <your-pc-name>
```

It reports whether the connection is **direct** or going **via a DERP relay**.
Direct is what you want; a relay adds a detour and will cost you tens of
milliseconds. If it relays, enabling UPnP or NAT-PMP on your router usually
lets it find the direct path.

A Cloudflare Tunnel also works and gives you real https, but every message
detours through Cloudflare's network, which adds latency for no benefit when
the only two clients are your own phones.

## How fast it actually is

Ticking an item off shows on the other phone in roughly 150–400ms over mobile
data. Two design choices get it there:

- **Your own taps apply instantly.** The app updates its own screen first and
  sends the request in the background, so you never wait on the network. If the
  request fails it re-syncs and the tick reverts.
- **The push carries the row.** The server sends the changed item itself, not a
  "something changed, go look" ping, so the other phone renders after one hop
  instead of a round trip.

What is left is physics: a mobile round trip is 30–80ms on good signal and worse
on a busy supermarket cell. Sub-50ms would need both phones on the same local
network. This is also why polling was the wrong answer — checking every few
seconds puts a multi-second gap exactly where you cannot afford one.

Neither approach notifies you when the app is closed; Android suspends the
socket. A lock-screen ping would need Firebase Cloud Messaging.

## API

All `/api` routes require an `x-household-key` header. The WebSocket lives at
`/live?key=<HOUSEHOLD_KEY>`. Shopping-list events carry their payload
(`{"type":"item.upserted","item":{...}}`, `{"type":"item.deleted","id":7}`) so
the app can render without asking for anything back. Bulk and lower-traffic
changes still just say reload: `items.reload`, `trip.changed`, `recipes.changed`.

| Method | Path | |
|---|---|---|
| GET/POST | `/api/items` | list, add |
| PATCH/DELETE | `/api/items/:id` | rename, tick, claim (`claimedBy`, `""` releases), remove |
| POST | `/api/items/clear-checked` | sweep ticked items |
| GET | `/api/trips/current` | open trip with entries and total |
| GET | `/api/trips` | trip history with totals |
| POST/DELETE | `/api/trips/current/entries[/:id]` | add amount, undo |
| PATCH | `/api/trips/current` | rename the trip |
| POST | `/api/trips/current/close` | bank the total, start fresh |
| GET/POST | `/api/recipes` | list with ingredients, add |
| PATCH/DELETE | `/api/recipes/:id` | update (ingredients replaced wholesale), remove |
| GET | `/api/health` | no key needed |

## Notes on the design

Money is stored as integer cents (`amount_cents`), never floats.

`trip.open_marker` is a generated column that is `1` while a trip is open and
`NULL` once closed, with a unique index on it. That makes "at most one open
trip" a database rule, so two phones opening the app at the same moment cannot
both create one.

The shared key is one secret for the household rather than per-person accounts.
It is enough for two people on a private server; it is not enough if you ever
put this on the public internet without https, because the key travels in a
plain header.
