import type { CardTransactionItem } from '../../types';

const hex = (bytes: number[]) => bytes.map(byte => (byte & 0xff).toString(16).padStart(2, '0')).join('').toUpperCase();
const le32 = (bytes: number[], offset: number) => (bytes[offset] | (bytes[offset + 1] << 8) | (bytes[offset + 2] << 16) | (bytes[offset + 3] << 24)) >>> 0;

function dateParts(date: Date): Pick<CardTransactionItem, 'date' | 'time'> {
  const y = date.getUTCFullYear();
  const m = String(date.getUTCMonth() + 1).padStart(2, '0');
  const d = String(date.getUTCDate()).padStart(2, '0');
  const hh = String(date.getUTCHours()).padStart(2, '0');
  const mm = String(date.getUTCMinutes()).padStart(2, '0');
  return {
    date: `${y}-${m}-${d}`,
    time: `${hh}:${mm}`
  };
}

function decodeHexAscii(hexStr: string): string {
  let res = '';
  for (let i = 0; i < hexStr.length; i += 2) {
    const code = Number.parseInt(hexStr.slice(i, i + 2), 16);
    if (code >= 32 && code <= 126) res += String.fromCharCode(code);
  }
  return res.trim();
}

/**
 * Mandiri e-Money History Parser (eSaldo Info parity)
 * 24 bytes (48 hex chars) per record:
 * - Bytes 0..5 (hex 0..12): BCD ddMMyyHHmmss
 * - Bytes 6..9 (hex 12..20): Terminal ID
 * - Bytes 14..15 (hex 28..32): Type code (100 = Top up, else Payment / Old card 288 = Payment, 256/336 = Top up)
 * - Bytes 16..19 (hex 32..40): LE uint32 Amount
 * - Bytes 20..23 (hex 40..48): LE uint32 Balance
 */
export function parseEmoneyHistory(rawRecords: number[][]): CardTransactionItem[] {
  return rawRecords.flatMap(record => {
    if (record.length < 24) return [];
    const hexStr = hex(record);
    const day = hexStr.slice(0, 2);
    const month = hexStr.slice(2, 4);
    const yy = hexStr.slice(4, 6);
    const hh = hexStr.slice(6, 8);
    const mm = hexStr.slice(8, 10);
    const ss = hexStr.slice(10, 12);

    const typeCode = Number.parseInt(hexStr.slice(28, 32), 16);
    const typeOld = ((record[12] << 24) | (record[13] << 16) | (record[14] << 8) | record[15]) >>> 0;
    const amount = le32(record, 16);
    const balance = le32(record, 20);

    if (typeCode === 0 && amount === 0 && balance === 0) return [];

    let type: CardTransactionItem['type'] = 'Payment';
    if (typeCode === 100 || typeOld === 256 || typeOld === 336) {
      type = 'Top up';
    } else if (typeCode === 0 && typeOld === 0 && amount === 0) {
      type = 'Unknown';
    }

    const terminalId = hexStr.slice(12, 20);
    const date = `20${yy}-${month}-${day}`;
    const time = `${hh}:${mm}:${ss}`;

    return [{ type, amount, balance, terminalId, date, time }];
  });
}

/**
 * BNI TapCash History Parser (eSaldo Info parity)
 * 16 bytes per record:
 * - Byte 0: Type (1 = Payment, 3/4/6 = Top up)
 * - Bytes 1..3: 24-bit signed int BE amount
 * - Bytes 4..7: uint32 BE timestamp (+ 788893200 seconds since Unix epoch)
 */
export function parseTapcashHistory(rawRecords: number[][]): CardTransactionItem[] {
  return rawRecords.flatMap(record => {
    if (record.length < 16) return [];
    const typeCode = record[0];
    let rawAmt = ((record[1] << 16) | (record[2] << 8) | record[3]);
    if ((record[1] & 0x80) !== 0) {
      rawAmt = (rawAmt | 0xff000000);
    }
    const amount = Math.abs(rawAmt);

    const rawTs = (((record[4] & 0xff) << 24) | ((record[5] & 0xff) << 16) | ((record[6] & 0xff) << 8) | (record[7] & 0xff)) >>> 0;
    if (rawTs === 0 && amount === 0) return [];

    const timestamp = rawTs + 788893200;
    const date = new Date(timestamp * 1000);
    if (!Number.isFinite(date.getTime())) return [];

    let type: CardTransactionItem['type'] = 'Payment';
    if (typeCode === 3 || typeCode === 4 || typeCode === 6) {
      type = 'Top up';
    } else if (typeCode !== 1) {
      type = 'Unknown';
    }

    return [{ type, amount, ...dateParts(date) }];
  });
}

/**
 * BRI Brizzi History Parser (eSaldo Info parity)
 * 32 bytes (64 hex chars) per record:
 * - Hex 32..42: BCD ddMMyyHHmm
 * - Hex 44..46: Type (EB/EF = Payment, EC = Top up)
 * - Hex 46..52: LE 24-bit uint amount
 */
export function parseBrizziHistory(hexRecords: string[]): CardTransactionItem[] {
  return hexRecords.flatMap(record => {
    if (record.length < 52) return [];
    const day = record.slice(32, 34);
    const month = record.slice(34, 36);
    const yy = record.slice(36, 38);
    const hour = record.slice(38, 40);
    const minute = record.slice(40, 42);
    const typeCode = record.slice(44, 46).toUpperCase();

    const b0 = Number.parseInt(record.slice(46, 48), 16);
    const b1 = Number.parseInt(record.slice(48, 50), 16);
    const b2 = Number.parseInt(record.slice(50, 52), 16);
    const amount = (b0 | (b1 << 8) | (b2 << 16)) >>> 0;

    if (!yy || (amount === 0 && yy === '00' && day === '00')) return [];

    const date = `20${yy}-${month}-${day}`;
    const time = `${hour}:${minute}`;
    const type: CardTransactionItem['type'] = (typeCode === 'EB' || typeCode === 'EF') ? 'Payment' : 'Top up';

    return [{ type, amount, date, time }];
  });
}

/**
 * BCA Flazz History Parser (eSaldo Info parity)
 * Gen 1 (60 bytes / 120 hex chars):
 * - Hex 0..4: Type (1024 = Payment, else Top up)
 * - Hex 12..18: 3 bytes BE uint24 amount
 * - Hex 60..76: Merchant / Terminal ID ASCII
 * - Hex 76..84: 4 bytes BE uint32 seconds since 1980-01-01
 *
 * Gen 2 (32 bytes / 64 hex chars):
 * - Hex 0..2: Type (04 = Payment, else Top up)
 * - Hex 2..8: 3 bytes BE uint24 amount
 * - Hex 8..16: 4 bytes BE uint32 seconds since 1980-01-01
 * - Hex 28..44: Merchant / Terminal ID ASCII
 * - Hex 44..50: 3 bytes BE uint24 balance
 */
export function parseFlazzHistory(rawRecords: (number[] | string)[]): CardTransactionItem[] {
  return rawRecords.flatMap(item => {
    const h = typeof item === 'string' ? item.toUpperCase() : hex(item);
    if (h.length >= 120) {
      // Gen 1
      const sec = Number.parseInt(h.slice(76, 84), 16);
      if (!sec || Number.isNaN(sec)) return [];
      const dateObj = new Date(Date.UTC(1980, 0, 1) + sec * 1000);
      const typeNum = Number.parseInt(h.slice(0, 4), 16);
      const type: CardTransactionItem['type'] = typeNum === 1024 ? 'Payment' : 'Top up';
      const amount = Number.parseInt(h.slice(12, 18), 16);
      const terminalId = decodeHexAscii(h.slice(60, 76)) || h.slice(60, 76);

      return [{ type, amount, terminalId, ...dateParts(dateObj) }];
    } else if (h.length >= 64) {
      // Gen 2
      const sec = Number.parseInt(h.slice(8, 16), 16);
      if (!sec || Number.isNaN(sec)) return [];
      const dateObj = new Date(Date.UTC(1980, 0, 1) + sec * 1000);
      const typeNum = Number.parseInt(h.slice(0, 2), 16);
      const isPayment = typeNum === 4;
      const type: CardTransactionItem['type'] = isPayment ? 'Payment' : 'Top up';
      const rawAmt = Number.parseInt(h.slice(2, 8), 16);
      const amount = isPayment && rawAmt > 0x800000 ? 0x1000000 - rawAmt : rawAmt;
      const balance = Number.parseInt(h.slice(44, 50), 16);
      const terminalId = decodeHexAscii(h.slice(28, 44)) || h.slice(28, 44);

      return [{ type, amount: Math.abs(amount), balance, terminalId, ...dateParts(dateObj) }];
    }
    return [];
  });
}
