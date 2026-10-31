import type { CardBalanceResult } from '../../types';
import { generateCardNumber } from '../card-formatter';
import { parseEmoneyHistory } from './history';

// Parse Mandiri e-Money balance and card details from raw chip bytes
export function parseEmoneyPayload(rawBytes: number[], uidHex: string, cardNumOverride?: string): CardBalanceResult {
  let balance = 0;

  if (rawBytes.length >= 4) {
    // Mandiri e-Money natively stores balance as 32-bit unsigned integer (little-endian)
    const leBalance = (
      (rawBytes[0] & 0xff) |
      ((rawBytes[1] & 0xff) << 8) |
      ((rawBytes[2] & 0xff) << 16) |
      ((rawBytes[3] & 0xff) << 24)
    ) >>> 0;

    if (leBalance <= 20_000_000) {
      balance = leBalance;
    } else {
      // Fallback: check big-endian
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

  // Use real card number from chip if available, else derive from CAN prefix
  const rawNum = cardNumOverride?.replace(/[^0-9]/g, '') || '';
  const cardNumber = rawNum.length >= 16
    ? rawNum.slice(0, 16).replace(/(\d{4})(?=\d)/g, '$1 ')
    : generateCardNumber('6032', uidHex);

  return {
    cardType: 'Mandiri e-Money',
    issuer: 'Mandiri',
    cardNumber,
    cardId: uidHex,
    balance,
    lastAmount: 0,
    history: parseEmoneyHistory([]),
    timestamp: new Date().toISOString()
  };
}
