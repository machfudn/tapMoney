import { Capacitor } from '@capacitor/core';
import { CapacitorNfc, type NfcEvent } from '@capgo/capacitor-nfc';
import type { CardBalanceResult, CardIssuer } from './types';

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
    return { supported: false, enabled: false };
  }

  try {
    const supportedRes = await CapacitorNfc.isSupported();
    const statusRes = await CapacitorNfc.getStatus();
    return {
      supported: supportedRes.supported,
      enabled: statusRes.status === 'NFC_OK'
    };
  } catch {
    return { supported: false, enabled: false };
  }
}

export function identifyCardFromTag(event: NfcEvent): CardBalanceResult {
  const tag = event.tag;
  const techTypes = tag.techTypes || [];
  const uidHex = tag.id ? bytesToHex(tag.id) : 'UNKNOWN_UID';

  let issuer: CardIssuer = 'Unknown';
  let cardType = 'Kartu Uang Elektronik';
  let balance = 0;

  if (techTypes.some(t => t.includes('NfcF'))) {
    issuer = 'KCI';
    cardType = 'KMT Commuter Line';
    balance = 25000;
  } else if (techTypes.some(t => t.includes('IsoDep'))) {
    // Default ISO-DEP smart card
    issuer = 'Mandiri';
    cardType = 'Mandiri e-Money';
    balance = 50000;
  } else if (techTypes.some(t => t.includes('MifareClassic'))) {
    issuer = 'DKI';
    cardType = 'Bank DKI JakCard';
    balance = 35000;
  }

  return {
    cardType,
    issuer,
    cardNumber: maskCardNumber(uidHex.padEnd(16, '0').slice(0, 16)),
    balance,
    lastAmount: 3500,
    timestamp: new Date().toISOString()
  };
}
