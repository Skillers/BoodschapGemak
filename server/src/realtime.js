import { WebSocketServer } from 'ws';

let wss = null;

/**
 * Attaches a WebSocket server to the HTTP server. Clients connect to
 * ws://host:port/live?key=<HOUSEHOLD_KEY> and then just listen; every
 * change made through the REST API is pushed to all of them.
 */
export function attachRealtime(httpServer, householdKey) {
  wss = new WebSocketServer({ noServer: true });

  httpServer.on('upgrade', (req, socket, head) => {
    const url = new URL(req.url, `http://${req.headers.host}`);
    if (url.pathname !== '/live') {
      socket.destroy();
      return;
    }
    if (url.searchParams.get('key') !== householdKey) {
      socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
      socket.destroy();
      return;
    }
    wss.handleUpgrade(req, socket, head, (ws) => wss.emit('connection', ws, req));
  });

  wss.on('connection', (ws) => {
    ws.isAlive = true;
    ws.on('pong', () => { ws.isAlive = true; });
    ws.send(JSON.stringify({ type: 'hello' }));
  });

  // Phones drop off wifi constantly; prune sockets that stopped answering.
  const heartbeat = setInterval(() => {
    for (const ws of wss.clients) {
      if (!ws.isAlive) { ws.terminate(); continue; }
      ws.isAlive = false;
      ws.ping();
    }
  }, 30000);
  wss.on('close', () => clearInterval(heartbeat));
}

/** Tells every connected phone that something changed. */
export function broadcast(type, payload = {}) {
  if (!wss) return;
  const message = JSON.stringify({ type, ...payload });
  for (const ws of wss.clients) {
    if (ws.readyState === ws.OPEN) ws.send(message);
  }
}
