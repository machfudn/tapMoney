import { IsoDepNfc } from '../iso-dep';
import { parseApduResponse } from './commands';
import { parseBrizziHistory, parseEmoneyHistory, parseFlazzHistory, parseTapcashHistory } from './parsers/history';
import type { CardTransactionItem } from '../types';

const send = async (data: number[]) => {
  try {
    const res = await IsoDepNfc.transceive({ data, timeout: 2500 });
    return parseApduResponse(res?.response || []);
  } catch {
    return parseApduResponse([]);
  }
};

const split = (data: number[], size: number) => Array.from({ length: Math.floor(data.length / size) }, (_, index) => data.slice(index * size, (index + 1) * size));

export async function readEmoneyHistory(isNewCard = true): Promise<CardTransactionItem[]> {
  try {
    const records: number[][] = [];
    if (isNewCard) {
      // New card (byte 18 == 0x83/131): 00 B2 <index> C4 00 (fallback 00 B2 <index> 04 00)
      for (let index = 1; index <= 255; index++) {
        let response = await send([0x00, 0xb2, index, 0xc4, 0x00]);
        if (!response.isSuccess && response.sw1 !== 0x90) {
          response = await send([0x00, 0xb2, index, 0x04, 0x00]);
        }
        if (!response.isSuccess || response.data.length < 24) break;
        records.push(response.data.slice(0, 24));
        if (records.length >= 30) break;
      }
    } else {
      // Old card: 00 B2 <index> 04 00 (fallback 00 B2 <index> 00 00)
      for (let index = 1; index <= 10; index++) {
        let response = await send([0x00, 0xb2, index, 0x04, 0x00]);
        if (!response.isSuccess) {
          response = await send([0x00, 0xb2, index, 0x00, 0x00]);
        }
        if (!response.isSuccess || response.data.length < 24) break;
        records.push(response.data.slice(0, 24));
      }
    }
    return parseEmoneyHistory(records);
  } catch {
    return [];
  }
}

export async function readTapcashHistory(totalRecords?: number): Promise<CardTransactionItem[]> {
  try {
    const count = typeof totalRecords === 'number' && totalRecords > 0 ? totalRecords : 15;
    const recordsData: number[] = [];
    const len1 = Math.min(count, 15) * 16;
    const resp1 = await send([0x90, 0x32, 0x03, 0x00, 0x01, 0x00, len1]);
    if (resp1.isSuccess && resp1.data.length > 0) {
      recordsData.push(...resp1.data);
    }
    if (count > 15) {
      const len2 = (count - 15) * 16;
      const resp2 = await send([0x90, 0x32, 0x03, 0x00, 0x01, 0x0f, len2]);
      if (resp2.isSuccess && resp2.data.length > 0) {
        recordsData.push(...resp2.data);
      }
    }
    return parseTapcashHistory(split(recordsData, 16));
  } catch {
    return [];
  }
}

export async function readBrizziHistory(): Promise<CardTransactionItem[]> {
  try {
    let response = await send([0x90, 0xbb, 0x00, 0x00, 0x07, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00]);
    if (!response.isSuccess && response.sw1 !== 0x91) {
      response = await send([0x90, 0xbd, 0x00, 0x00, 0x07, 0x00, 0x00, 0x00, 0x00, 0x17, 0x00, 0x00, 0x00]);
    }
    let raw = response.data.slice();
    while (response.sw1 === 0x91 && response.sw2 === 0xaf) {
      response = await send([0x90, 0xaf, 0x00, 0x00, 0x00]);
      raw = raw.concat(response.data);
    }
    return parseBrizziHistory(split(raw, 32).map(record => record.map(byte => (byte & 0xff).toString(16).padStart(2, '0')).join('')));
  } catch {
    return [];
  }
}

export async function readFlazzHistory(isGen2 = false): Promise<CardTransactionItem[]> {
  try {
    if (isGen2) {
      // Flazz Gen 2 history: 90 32 03 00 01 <index> 20
      const records: number[][] = [];
      for (let i = 0; i < 20; i++) {
        const response = await send([0x90, 0x32, 0x03, 0x00, 0x01, i, 0x20]);
        if (!response.isSuccess || response.data.length < 32) break;
        records.push(response.data);
      }
      return parseFlazzHistory(records);
    } else {
      // Flazz Gen 1 history: read binary chunks (15 bytes each, records are 60 bytes)
      const rawBytes: number[] = [];
      for (let offset = 0; offset < 16; offset++) {
        const response = await send([0x00, 0xb0, 0x00, offset * 15, 0x0f]);
        if (!response.isSuccess || response.data.length === 0) break;
        rawBytes.push(...response.data);
      }
      const records = split(rawBytes, 60);
      return parseFlazzHistory(records);
    }
  } catch {
    return [];
  }
}
