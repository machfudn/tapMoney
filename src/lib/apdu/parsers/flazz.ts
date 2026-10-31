import type { CardBalanceResult } from '../../types';
import { generateCardNumber } from '../card-formatter';
import { parseFlazzHistory } from './history';

// Parse BCA Flazz Gen 1 and Gen 2 balance payload
export function parseFlazzPayload(rawBytes: number[], uidHex: string): CardBalanceResult {
  let balance = 0;

  if (rawBytes.length >= 4) {
    balance = (
      ((rawBytes[0] & 0xff) << 24) |
      ((rawBytes[1] & 0xff) << 16) |
      ((rawBytes[2] & 0xff) << 8) |
      (rawBytes[3] & 0xff)
    ) >>> 0;

    // Check little-endian if unusually high
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

  // Format 16-digit card number with BCA Flazz prefix
  const cardNumber = generateCardNumber('1809', uidHex);

  return {
    cardType: 'BCA Flazz',
    issuer: 'BCA',
    cardNumber,
    cardId: uidHex,
    balance,
    lastAmount: 0,
    history: parseFlazzHistory([]),
    timestamp: new Date().toISOString()
  };
}
