import type { CardBalanceResult } from '../types';

export interface StoredTapRecord {
  id: string;
  cardNumber: string;
  cardType: string;
  issuer: string;
  balance: number;
  lastAmount?: number;
  merchantName?: string;
  timestamp: string;
}

const DB_NAME = 'tap_money_db';
const DB_VERSION = 1;
const STORE_CARDS = 'cards';
const STORE_HISTORY = 'history';

// Open or upgrade IndexedDB database
function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      return reject(new Error('IndexedDB not supported in current runtime'));
    }

    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = (event) => {
      const db = (event.target as IDBOpenDBRequest).result;

      if (!db.objectStoreNames.contains(STORE_CARDS)) {
        db.createObjectStore(STORE_CARDS, { keyPath: 'cardNumber' });
      }

      if (!db.objectStoreNames.contains(STORE_HISTORY)) {
        const historyStore = db.createObjectStore(STORE_HISTORY, { keyPath: 'id' });
        historyStore.createIndex('timestamp', 'timestamp', { unique: false });
        historyStore.createIndex('cardNumber', 'cardNumber', { unique: false });
      }
    };

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

// Save tap record and update last balance on card
export async function saveTapRecord(card: CardBalanceResult, merchantName?: string): Promise<StoredTapRecord> {
  const db = await openDatabase();
  const record: StoredTapRecord = {
    id: `tap_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
    cardNumber: card.cardNumber,
    cardType: card.cardType,
    issuer: card.issuer,
    balance: card.balance,
    lastAmount: card.lastAmount,
    merchantName: merchantName || 'Merchant Umum',
    timestamp: card.timestamp || new Date().toISOString()
  };

  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_CARDS, STORE_HISTORY], 'readwrite');
    const cardsStore = tx.objectStore(STORE_CARDS);
    const historyStore = tx.objectStore(STORE_HISTORY);

    cardsStore.put({
      cardNumber: card.cardNumber,
      cardType: card.cardType,
      issuer: card.issuer,
      lastBalance: card.balance,
      updatedAt: record.timestamp
    });

    historyStore.add(record);

    tx.oncomplete = () => resolve(record);
    tx.onerror = () => reject(tx.error);
  });
}

// Retrieve tap history descending by timestamp
export async function getStoredHistory(limit = 50): Promise<StoredTapRecord[]> {
  const db = await openDatabase();

  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_HISTORY, 'readonly');
    const store = tx.objectStore(STORE_HISTORY);
    const index = store.index('timestamp');
    const request = index.openCursor(null, 'prev');
    const results: StoredTapRecord[] = [];

    request.onsuccess = (event) => {
      const cursor = (event.target as IDBRequest<IDBCursorWithValue>).result;
      if (cursor && results.length < limit) {
        results.push(cursor.value);
        cursor.continue();
      } else {
        resolve(results);
      }
    };

    request.onerror = () => reject(request.error);
  });
}

// Clear all records from history store
export async function clearStoredHistory(): Promise<void> {
  const db = await openDatabase();

  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_HISTORY, 'readwrite');
    const store = tx.objectStore(STORE_HISTORY);
    const request = store.clear();

    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}
