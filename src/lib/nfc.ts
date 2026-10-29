import { Capacitor } from '@capacitor/core';
import { CapacitorNfc, type NfcEvent } from '@capgo/capacitor-nfc';
import type { CardBalanceResult, CardIssuer } from './types';
import { generateCardNumber } from './apdu/card-formatter';

export function bytesToHex(bytes: number[]): string {
  return bytes.map(b => (b & 0xff).toString(16).padStart(2, '0')).join('').toUpperCase();
}

export function hexToBytes(hex: string): number[] {
  const clean = hex.replace(/[^0-9A-Fa-f]/g, '');
  const bytes: number[] = [];
  for (let i = 0; i < clean.length; i += 2) {
    bytes.push(parseInt(clean.substring(i, i + 2), 16));
  }
  return bytes;
}

export function formatRupiah(amount: number): string {
  return new Intl.NumberFormat('id-ID', {
    style: 'currency',
    currency: 'IDR',
    maximumFractionDigits: 0
  }).format(amount);
}

export function maskCardNumber(num: string): string {
  if (num.length < 8) return num;
  return `${num.slice(0, 4)} **** **** ${num.slice(-4)}`;
}

export async function checkNfcStatus(): Promise<{ supported: boolean; enabled: boolean }> {
  if (!Capacitor.isNativePlatform()) {
    return { supported: true, enabled: true };
  }

  try {
    const supportedRes = await CapacitorNfc.isSupported();
    const statusRes = await CapacitorNfc.getStatus();
    return {
      supported: Boolean(supportedRes?.supported),
      enabled: statusRes?.status === 'NFC_OK'
    };
  } catch {
    return { supported: false, enabled: false };
  }
}

export async function openNfcSettings(): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;
  try {
    await CapacitorNfc.showSettings();
  } catch {
    // fallback or ignore
  }
}

export { readCardViaApdu } from './apdu/engine';

export function identifyCardFromTag(event: NfcEvent): CardBalanceResult {
  const tag = event.tag || (event as any);
  const techTypes: string[] = tag.techTypes || [];
  const uidHex = tag.id && Array.isArray(tag.id) ? bytesToHex(tag.id) : 'UNKNOWN_UID';

  let issuer: CardIssuer = 'Unknown';
  let cardType = 'Kartu Elektronik (NFC)';
  let prefix = '8888';

  if (techTypes.some((t: string) => t.includes('NfcF'))) {
    issuer = 'KCI';
    cardType = 'KMT Commuter Line';
    prefix = '1002';
  } else if (techTypes.some((t: string) => t.includes('MifareClassic'))) {
    issuer = 'DKI';
    cardType = 'Bank DKI JakCard';
    prefix = '3101';
  } else if (techTypes.some((t: string) => t.includes('IsoDep'))) {
    issuer = 'Unknown';
    cardType = 'Kartu Elektronik (ISO-DEP)';
    prefix = '8888';
  }

  return {
    cardType,
    issuer,
    cardNumber: generateCardNumber(prefix, uidHex),
    balance: 0,
    lastAmount: 0,
    timestamp: new Date().toISOString()
  };
}
