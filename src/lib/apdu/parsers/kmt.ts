import type { CardBalanceResult } from '../../types';
import { generateCardNumber } from '../card-formatter';

// Parse KMT Commuter Line Sony FeliCa block data
export function parseKmtPayload(blockBytes: number[], uidHex: string): CardBalanceResult {
  let balance = 0;

  // 16-byte FeliCa block: bytes 11..12 or 10..13 usually contain balance in little-endian
  if (blockBytes.length >= 16) {
    balance = (
      ((blockBytes[13] & 0xff) << 24) |
      ((blockBytes[12] & 0xff) << 16) |
      ((blockBytes[11] & 0xff) << 8) |
      (blockBytes[10] & 0xff)
    ) >>> 0;

    if (balance > 2_000_000) {
      // 16-bit short balance check (common in Japanese transit cards & KMT)
      balance = ((blockBytes[11] & 0xff) << 8) | (blockBytes[10] & 0xff);
    }
  }

  // Format 16-digit card number with KMT prefix
  const cardNumber = generateCardNumber('1002', uidHex);

  return {
    cardType: 'KMT Commuter Line',
    issuer: 'KCI',
    cardNumber,
    balance,
    lastAmount: 0,
    timestamp: new Date().toISOString()
  };
}
