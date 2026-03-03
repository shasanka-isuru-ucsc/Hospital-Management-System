'use strict';

require('dotenv').config();

const http = require('http');
const { Server } = require('socket.io');
const { connect, StringCodec, consumerOpts, createInbox } = require('nats');

const PORT = process.env.PORT || 3010;

// ── HTTP server (Socket.IO needs one) ─────────────────────────────────────────
const httpServer = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: true, data: { status: 'ok', service: 'hms-ws-bridge' } }));
  } else {
    res.writeHead(404);
    res.end();
  }
});

// ── Socket.IO ──────────────────────────────────────────────────────────────────
const io = new Server(httpServer, {
  cors: { origin: '*', methods: ['GET', 'POST'] },
});

// Clients join a room based on query param: ?room=queue or ?room=pharmacy
io.on('connection', (socket) => {
  const room = socket.handshake.query.room;
  if (room === 'queue' || room === 'pharmacy') {
    socket.join(room);
    console.log(`[Socket.IO] Client ${socket.id} joined room: ${room}`);
  }
  socket.on('disconnect', () => {
    console.log(`[Socket.IO] Client ${socket.id} disconnected`);
  });
});

// ── NATS subscriptions ────────────────────────────────────────────────────────
async function subscribeToSubject(js, sc, { stream, subject, durable, room, event }) {
  try {
    const opts = consumerOpts();
    opts.durable(durable);
    opts.ackExplicit();
    opts.deliverAll();
    opts.filterSubject(subject);
    opts.deliverTo(createInbox()); // required for push consumers

    const sub = await js.subscribe(subject, opts);

    (async () => {
      for await (const msg of sub) {
        try {
          const payload = JSON.parse(sc.decode(msg.data));
          io.to(room).emit(event, payload);
          msg.ack();
        } catch (parseErr) {
          console.error(`[WS] Failed to parse ${subject} message:`, parseErr.message);
          msg.ack(); // ack anyway to avoid redelivery loop
        }
      }
    })();

    console.log(`[WS] Subscribed to NATS subject ${subject} → Socket.IO room '${room}'`);
  } catch (err) {
    console.error(`[WS] Failed to subscribe to ${subject}:`, err.message);
  }
}

async function startNats() {
  const sc = StringCodec();
  const nc = await connect({ servers: process.env.NATS_URL || 'nats://localhost:4222' });
  const js = nc.jetstream();
  console.log(`[NATS] Connected to ${process.env.NATS_URL || 'nats://localhost:4222'}`);

  // queue.updated — published by reception-service, QUEUE stream
  await subscribeToSubject(js, sc, {
    stream:  'QUEUE',
    subject: 'queue.updated',
    durable: 'ws-bridge-queue',
    room:    'queue',
    event:   'queue:updated',
  });

  // pharmacy.new_rx — published by clinical-service, PHARMACY stream
  await subscribeToSubject(js, sc, {
    stream:  'PHARMACY',
    subject: 'pharmacy.new_rx',
    durable: 'ws-bridge-pharmacy',
    room:    'pharmacy',
    event:   'pharmacy:new_rx',
  });
}

// ── Start ──────────────────────────────────────────────────────────────────────
httpServer.listen(PORT, async () => {
  console.log(`[WS Bridge] Socket.IO server running on port ${PORT}`);

  try {
    await startNats();
    console.log('[WS Bridge] NATS subscriptions ready');
  } catch (err) {
    console.error('[WS Bridge] NATS setup failed — WebSocket events will not be delivered:', err.message);
    // Server stays up — Socket.IO still works, just no NATS events
  }
});

// ── Graceful shutdown ──────────────────────────────────────────────────────────
process.on('SIGTERM', () => {
  console.log('[WS Bridge] SIGTERM received — shutting down');
  httpServer.close(() => process.exit(0));
});
