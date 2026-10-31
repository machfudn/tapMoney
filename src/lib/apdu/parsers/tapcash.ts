import type { CardBalanceResult } from '../../types';
import { generateCardNumber } from '../card-formatter';
import { parseTapcashHistory } from './history';

// Parse BNI TapCash balance payload
export function parseTapcashPayload(rawBytes: number[], uidHex: string): CardBalanceResult {
  let balance = 0;

  if (rawBytes.length >= 4) {
    balance = (
      ((rawBytes[0] & 0xff) << 24) |
      ((rawBytes[1] & 0xff) << 16) |
      ((rawBytes[2] & 0xff) << 8) |
      (rawBytes[3] & 0xff)
    ) >>> 0;

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

  // Format 16-digit card number with BNI TapCash prefix
  const cardNumber = generateCardNumber('7546', uidHex);

  return {
    cardType: 'BNI TapCash',
    issuer: 'BNI',
    cardNumber,
    cardId: uidHex,
    balance,
    lastAmount: 0,
    history: parseTapcashHistory([]),
    timestamp: new Date().toISOString()
  };
}
