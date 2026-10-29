import type { CardBalanceResult } from '../../types';
import { bytesToHex } from '../commands';

// Parse Mandiri e-Money balance and card details from raw chip bytes
export function parseEmoneyPayload(rawBytes: number[], uidHex: string): CardBalanceResult {
  // Purse file structure stores 4-byte balance
  let balance = 0;

  if (rawBytes.length >= 4) {
    // Read 32-bit unsigned integer (big-endian)
    balance = (
      ((rawBytes[0] & 0xff) << 24) |
      ((rawBytes[1] & 0xff) << 16) |
      ((rawBytes[2] & 0xff) << 8) |
      (rawBytes[3] & 0xff)
    ) >>> 0;

    // Fallback: if balance exceeds standard e-Money max cap (20 juta IDR), check little-endian
    if (balance > 20_000_000 && rawBytes.length >= 4) {
      const leBalance = (
        ((rawBytes[3] & 0xff) << 24) |
        ((rawBytes[2] & 0xff) << 16) |
        ((rawBytes[1] & 0xff) << 8) |
        (rawBytes[0] & 0xff)
      ) >>> 0;
      if (leBalance <= 20_000_000) {
        balance = leBalance;
      }
    }
  }

  // Format card number: prefix Mandiri CAN if present or mask from UID
  const cleanUid = uidHex.replace(/[^0-9A-Fa-f]/g, '').padEnd(16, '0');
  const cardNumber = `6032 **** **** ${cleanUid.slice(-4)}`;

  return {
    cardType: 'Mandiri e-Money',
    issuer: 'Mandiri',
    cardNumber,
    balance,
    lastAmount: 0,
    timestamp: new Date().toISOString()
  };
}
