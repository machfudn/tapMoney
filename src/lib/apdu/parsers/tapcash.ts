import type { CardBalanceResult } from '../../types';
import { generateCardNumber } from '../card-formatter';
import { parseTapcashHistory } from './history';

// Parse BNI TapCash balance payload
export function parseTapcashPayload(rawBytes: number[], uidHex: string, cardNumOverride?: string, balanceOverride?: number): CardBalanceResult {
  let balance = typeof balanceOverride === 'number' ? balanceOverride : 0;

  if (typeof balanceOverride !== 'number' && rawBytes.length >= 4) {
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

  // Use real card number from chip bytes if available
  const rawNum = cardNumOverride?.replace(/[^0-9]/g, '') || '';
  const cardNumber = rawNum.length >= 16
    ? rawNum.slice(0, 16).replace(/(\d{4})(?=\d)/g, '$1 ')
    : generateCardNumber('7546', uidHex);

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
