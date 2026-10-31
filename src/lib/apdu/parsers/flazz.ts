import type { CardBalanceResult } from '../../types';
import { generateCardNumber } from '../card-formatter';
import { parseFlazzHistory } from './history';

// Parse BCA Flazz Gen 1 and Gen 2 balance payload
export function parseFlazzPayload(rawBytes: number[], uidHex: string, cardNumOverride?: string, balanceOverride?: number): CardBalanceResult {
  let balance = typeof balanceOverride === 'number' ? balanceOverride : 0;

  if (typeof balanceOverride !== 'number') {
    if (rawBytes.length >= 4) {
      balance = (
        ((rawBytes[0] & 0xff) << 24) |
        ((rawBytes[1] & 0xff) << 16) |
        ((rawBytes[2] & 0xff) << 8) |
        (rawBytes[3] & 0xff)
      ) >>> 0;

      // Check little-endian if unusually high
      if (balance > 20_000_000) {
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
    } else if (rawBytes.length >= 3) {
      balance = (
        ((rawBytes[0] & 0xff) << 16) |
        ((rawBytes[1] & 0xff) << 8) |
        (rawBytes[2] & 0xff)
      ) >>> 0;
    }
  }

  // Use real card number extracted from chip if available
  const rawNum = cardNumOverride?.replace(/[^0-9]/g, '') || '';
  const cardNumber = rawNum.length >= 16
    ? rawNum.slice(0, 16).replace(/(\d{4})(?=\d)/g, '$1 ')
    : generateCardNumber('1809', uidHex);

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
