import type { BackendCardDefinition, CardBalanceResult } from './types';

// API base URL configured via VITE_API_URL, fallback to localhost for development
const API_BASE_URL = (import.meta.env.VITE_API_URL || 'http://localhost:3000').replace(/\/+$/, '');

export async function fetchCardDefinitions(): Promise<BackendCardDefinition[]> {
  try {
    const res = await fetch(`${API_BASE_URL}/api/cards/definitions`);
    if (!res.ok) return [];
    const json = await res.json();
    return json.data || [];
  } catch {
    return [];
  }
}

export async function resolveMerchantName(code: string): Promise<string> {
  try {
    const res = await fetch(`${API_BASE_URL}/api/cards/resolve-merchant`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code })
    });
    if (!res.ok) return 'Merchant Umum';
    const json = await res.json();
    return json.name || 'Merchant Umum';
  } catch {
    return 'Merchant Umum';
  }
}

export async function syncTapHistory(card: CardBalanceResult, merchantCode?: string): Promise<boolean> {
  try {
    const res = await fetch(`${API_BASE_URL}/api/cards/history`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        cardType: card.cardType,
        cardNumberMasked: card.cardNumber,
        balance: card.balance,
        lastTransactionAmount: card.lastAmount,
        merchantCode
      })
    });
    return res.ok;
  } catch {
    return false;
  }
}
