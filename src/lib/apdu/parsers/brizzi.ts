import type { CardBalanceResult } from '../../types';

// Parse BRI Brizzi DESFire balance payload
export function parseBrizziPayload(rawBytes: number[], uidHex: string): CardBalanceResult {
  let balance = 0;

  // DESFire Value file returns 4 bytes signed integer
  if (rawBytes.length >= 4) {
    // DESFire typically uses little-endian byte order
    balance = (
      ((rawBytes[3] & 0xff) << 24) |
      ((rawBytes[2] & 0xff) << 16) |
      ((rawBytes[1] & 0xff) << 8) |
      (rawBytes[0] & 0xff)
    ) >>> 0;

    if (balance > 20_000_000) {
      const beBalance = (
        ((rawBytes[0] & 0xff) << 24) |
        ((rawBytes[1] & 0xff) << 16) |
        ((rawBytes[2] & 0xff) << 8) |
        (rawBytes[3] & 0xff)
      ) >>> 0;
      if (beBalance <= 20_000_000) {
        balance = beBalance;
      }
    }
  }

  const cleanUid = uidHex.replace(/[^0-9A-Fa-f]/g, '').padEnd(16, '0');
  const cardNumber = `5018 **** **** ${cleanUid.slice(-4)}`;

  return {
    cardType: 'BRI Brizzi',
    issuer: 'BRI',
    cardNumber,
    balance,
    lastAmount: 0,
    timestamp: new Date().toISOString()
  };
}
