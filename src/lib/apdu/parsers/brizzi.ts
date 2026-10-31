import type { CardBalanceResult } from '../../types';
import { generateCardNumber } from '../card-formatter';
import { parseBrizziHistory } from './history';

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

  // Format 16-digit card number with BRI Brizzi prefix
  const cardNumber = generateCardNumber('5018', uidHex);

  return {
    cardType: 'BRI Brizzi',
    issuer: 'BRI',
    cardNumber,
    cardId: uidHex,
    balance,
    lastAmount: 0,
    history: parseBrizziHistory([]),
    timestamp: new Date().toISOString()
  };
}
