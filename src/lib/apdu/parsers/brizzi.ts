import type { CardBalanceResult } from '../../types';
import { generateCardNumber } from '../card-formatter';
import { parseBrizziHistory } from './history';

// Parse BRI Brizzi DESFire balance payload
export function parseBrizziPayload(rawBytes: number[], uidHex: string, cardNumOverride?: string): CardBalanceResult {
  let balance = 0;

  // DESFire Value file returns 4 bytes signed integer (little-endian)
  if (rawBytes.length >= 4) {
    const leBalance = (
      (rawBytes[0] & 0xff) |
      ((rawBytes[1] & 0xff) << 8) |
      ((rawBytes[2] & 0xff) << 16) |
      ((rawBytes[3] & 0xff) << 24)
    ) >>> 0;

    if (leBalance <= 20_000_000) {
      balance = leBalance;
    } else {
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

  // Use real card number from chip file 0 if available
  const rawNum = cardNumOverride?.replace(/[^0-9]/g, '') || '';
  const cardNumber = rawNum.length >= 16
    ? rawNum.slice(0, 16).replace(/(\d{4})(?=\d)/g, '$1 ')
    : generateCardNumber('5018', uidHex);

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
