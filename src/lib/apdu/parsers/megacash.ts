import { buildSelectAidApdu, buildReadBinaryApdu } from '../commands';
import type { CardBalanceResult } from '../../types';

export const AID_MEGACASH = 'A0000000030002';

export function buildSelectMegaCashApdu(): number[] {
  return buildSelectAidApdu(AID_MEGACASH);
}

export function buildReadMegaCashBalanceApdu(): number[] {
  return buildReadBinaryApdu(0, 4);
}

export function parseMegaCashPayload(data: number[], uidHex: string): CardBalanceResult {
  let balance = 0;
  if (data.length >= 4) {
    balance = ((data[0] & 0xff) << 24) |
              ((data[1] & 0xff) << 16) |
              ((data[2] & 0xff) << 8) |
              (data[3] & 0xff);
    if (balance < 0) balance = 0;
  }

  const cleanUid = uidHex.replace(/[^0-9A-Fa-f]/g, '').toUpperCase();
  const suffix = cleanUid.length >= 8 ? cleanUid.slice(-8) : cleanUid.padEnd(8, '0');
  const cardNumber = `6019 8800 ${suffix.slice(0, 4)} ${suffix.slice(4, 8)}`;

  return {
    cardType: 'Bank Mega MegaCash',
    issuer: 'Mega',
    cardNumber,
    balance,
    lastAmount: 0,
    timestamp: new Date().toISOString()
  };
}
