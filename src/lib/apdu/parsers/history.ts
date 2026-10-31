import type { CardTransactionItem } from '../../types';

const hex = (bytes: number[]) => bytes.map(byte => (byte & 0xff).toString(16).padStart(2, '0')).join('').toUpperCase();
const le32 = (bytes: number[], offset: number) => (bytes[offset] | (bytes[offset + 1] << 8) | (bytes[offset + 2] << 16) | (bytes[offset + 3] << 24)) >>> 0;
const be24 = (bytes: number[], offset: number) => ((bytes[offset] << 16) | (bytes[offset + 1] << 8) | bytes[offset + 2]) >>> 0;

function dateParts(date: Date): Pick<CardTransactionItem, 'date' | 'time'> {
  return {
    date: date.toISOString().slice(0, 10),
    time: date.toISOString().slice(11, 19)
  };
}

export function parseEmoneyHistory(rawRecords: number[][]): CardTransactionItem[] {
  return rawRecords.flatMap(record => {
    if (record.length < 24) return [];
    const date = new Date(Date.UTC(2000 + record[0], record[1] - 1, record[2], record[3], record[4], record[5]));
    const code = ((record[10] << 24) | (record[11] << 16) | (record[12] << 8) | record[13]) >>> 0;
    const amount = le32(record, 14);
    const balance = le32(record, 18);
    if (!Number.isFinite(date.getTime()) || (code === 0 && amount === 0 && balance === 0)) return [];
    return [{ type: (code === 256 || code === 336) ? 'Top up' : 'Payment', amount, balance, ...dateParts(date) }];
  });
}

export function parseTapcashHistory(rawRecords: number[][]): CardTransactionItem[] {
  return rawRecords.flatMap(record => {
    if (record.length < 16) return [];
    const typeCode = record[0];
    const amount = (((record[1] & 0x7f) << 16) | ((record[2] & 0xff) << 8) | (record[3] & 0xff)) >>> 0;
    const rawTs = (((record[4] & 0xff) << 24) | ((record[5] & 0xff) << 16) | ((record[6] & 0xff) << 8) | (record[7] & 0xff)) >>> 0;
    const timestamp = rawTs + 788893200;
    const date = new Date(timestamp * 1000);
    if (!Number.isFinite(date.getTime()) || amount === 0) return [];
    const type = typeCode === 2 ? 'Top up' : 'Payment';
    return [{ type, amount, ...dateParts(date) }];
  });
}

export function parseBrizziHistory(hexRecords: string[]): CardTransactionItem[] {
  return hexRecords.flatMap(record => {
    if (record.length < 52) return [];
    const yy = Number.parseInt(record.slice(32, 34), 16);
    const month = Number.parseInt(record.slice(34, 36), 16);
    const day = Number.parseInt(record.slice(36, 38), 16);
    const hour = Number.parseInt(record.slice(38, 40), 16);
    const minute = Number.parseInt(record.slice(40, 42), 16);
    const typeCode = record.slice(44, 46);
    const rawAmtHex = record.slice(46, 52);
    const revAmtHex = rawAmtHex.slice(4, 6) + rawAmtHex.slice(2, 4) + rawAmtHex.slice(0, 2);
    const amount = Number.parseInt(revAmtHex, 16) || Number.parseInt(rawAmtHex, 16);
    const date = new Date(Date.UTC(2000 + yy, month - 1, day, hour, minute));
    if (!Number.isFinite(date.getTime()) || amount === 0) return [];
    const type = (typeCode === '02' || typeCode === '20') ? 'Top up' : 'Payment';
    return [{ type, amount, ...dateParts(date) }];
  });
}

export function parseFlazzHistory(rawRecords: number[][]): CardTransactionItem[] {
  return rawRecords.flatMap(record => {
    if (record.length < 8) return [];
    const date = new Date(Date.UTC(2000 + record[0], record[1] - 1, record[2], record[3], record[4], record[5]));
    const amount = le32(record, 4);
    if (!Number.isFinite(date.getTime()) || amount === 0) return [];
    return [{ type: 'Payment', amount, terminalId: hex(record.slice(0, 4)), ...dateParts(date) }];
  });
}
