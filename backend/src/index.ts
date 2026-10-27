import { serve } from '@hono/node-server';
import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { logger } from 'hono/logger';

interface CardDefinition {
  id: string;
  name: string;
  issuer: string;
  tech: 'IsoDep' | 'NfcF' | 'NfcA';
  aidHex?: string;
  description: string;
}

interface TapHistoryEntry {
  id: string;
  cardType: string;
  cardNumberMasked: string;
  balance: number;
  lastTransactionAmount?: number;
  merchantCode?: string;
  merchantName?: string;
  timestamp: string;
}

const SUPPORTED_CARDS: CardDefinition[] = [
  {
    id: 'emoney',
    name: 'Mandiri e-Money',
    issuer: 'Bank Mandiri',
    tech: 'IsoDep',
    aidHex: 'A000000003000000',
    description: 'ISO-DEP smart card using proprietary balance and history file structures'
  },
  {
    id: 'flazz',
    name: 'BCA Flazz',
    issuer: 'Bank Central Asia',
    tech: 'IsoDep',
    aidHex: 'A000000003000000',
    description: 'Supports Gen 1 and Gen 2 Flazz transaction log structures'
  },
  {
    id: 'brizzi',
    name: 'BRI Brizzi',
    issuer: 'Bank Rakyat Indonesia',
    tech: 'IsoDep',
    aidHex: '0000030080000000',
    description: 'Mifare DESFire EV1 based electronic money card'
  },
  {
    id: 'tapcash',
    name: 'BNI TapCash',
    issuer: 'Bank Negara Indonesia',
    tech: 'IsoDep',
    aidHex: 'A000000003000000',
    description: 'ISO 7816 compatible prepaid smart card'
  },
  {
    id: 'jakcard',
    name: 'Bank DKI JakCard',
    issuer: 'Bank DKI',
    tech: 'IsoDep',
    description: 'Jakarta provincial smart card for transit and tourism'
  },
  {
    id: 'kmt',
    name: 'KMT Commuter Line',
    issuer: 'PT Kereta Commuter Indonesia',
    tech: 'NfcF',
    description: 'JIS 6319-4 Sony FeliCa transit payment card'
  }
];

// In-memory merchant map for resolving location / terminal identifiers
// ponytail: in-memory store; replace with SQLite / PostgreSQL when multi-instance persistence is needed
const MERCHANT_REGISTRY: Record<string, string> = {
  'GT_CILILITAN_1': 'Gerbang Tol Cililitan 1',
  'GT_CILILITAN_2': 'Gerbang Tol Cililitan 2',
  'GT_HALIM_1': 'Gerbang Tol Halim 1',
  'GT_CENGKARENG': 'Gerbang Tol Cengkareng',
  'TJ_HARMONI': 'Halte Transjakarta Harmoni',
  'TJ_MONAS': 'Halte Transjakarta Monas',
  'TJ_KUNINGAN': 'Halte Transjakarta Kuningan Barat',
  'KCI_MANGGARAI': 'Stasiun KCI Manggarai',
  'KCI_TANAH_ABANG': 'Stasiun KCI Tanah Abang',
  'MRT_BUNDARAN_HI': 'Stasiun MRT Bundaran HI',
  'MRT_LEBAK_BULUS': 'Stasiun MRT Lebak Bulus Grab'
};

const historyStore: TapHistoryEntry[] = [];

const app = new Hono();

app.use('*', logger());
app.use('*', cors({
  origin: '*',
  allowMethods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowHeaders: ['Content-Type', 'Authorization']
}));

app.get('/health', (c) => {
  return c.json({
    status: 'ok',
    service: 'tap-money-backend',
    uptimeSeconds: Math.floor(process.uptime()),
    timestamp: new Date().toISOString()
  });
});

app.get('/api/cards/definitions', (c) => {
  return c.json({
    success: true,
    data: SUPPORTED_CARDS
  });
});

app.post('/api/cards/resolve-merchant', async (c) => {
  const body = await c.req.json<{ code?: string }>().catch(() => ({ code: '' }));
  const code = (body.code || '').trim().toUpperCase();

  const merchantName = MERCHANT_REGISTRY[code] || 'Merchant Umum';
  return c.json({
    success: true,
    code,
    name: merchantName
  });
});

app.get('/api/cards/history', (c) => {
  return c.json({
    success: true,
    total: historyStore.length,
    data: historyStore.slice(-50).reverse()
  });
});

app.post('/api/cards/history', async (c) => {
  const body: Partial<TapHistoryEntry> = await c.req.json<Partial<TapHistoryEntry>>().catch(() => ({}));

  if (!body.cardType || typeof body.balance !== 'number') {
    return c.json({ success: false, error: 'cardType and balance are required' }, 400);
  }

  const entry: TapHistoryEntry = {
    id: `tap_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
    cardType: body.cardType,
    cardNumberMasked: body.cardNumberMasked || '**** **** **** ****',
    balance: body.balance,
    lastTransactionAmount: body.lastTransactionAmount,
    merchantCode: body.merchantCode,
    merchantName: body.merchantCode ? MERCHANT_REGISTRY[body.merchantCode] : undefined,
    timestamp: new Date().toISOString()
  };

  historyStore.push(entry);

  return c.json({
    success: true,
    data: entry
  }, 201);
});

const port = Number(process.env.PORT) || 3000;
console.log(`tap-money-backend listening on http://localhost:${port}`);

serve({
  fetch: app.fetch,
  port
});
