import { buildSelectAidApdu, buildReadBinaryApdu } from '../commands';
import type { CardBalanceResult } from '../../types';
import { generateCardNumber } from '../card-formatter';

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

  const cardNumber = generateCardNumber('6019', uidHex);

  return {
    cardType: 'Bank Mega MegaCash',
    issuer: 'Mega',
    cardNumber,
    balance,
    lastAmount: 0,
    timestamp: new Date().toISOString()
  };
}
