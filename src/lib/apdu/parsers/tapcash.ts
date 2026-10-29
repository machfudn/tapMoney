import type { CardBalanceResult } from '../../types';

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

  const cleanUid = uidHex.replace(/[^0-9A-Fa-f]/g, '').padEnd(16, '0');
  const cardNumber = `7546 **** **** ${cleanUid.slice(-4)}`;

  return {
    cardType: 'BNI TapCash',
    issuer: 'BNI',
    cardNumber,
    balance,
    lastAmount: 0,
    timestamp: new Date().toISOString()
  };
}
