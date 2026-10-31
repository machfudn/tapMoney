import { IsoDepNfc } from '../iso-dep';
import { parseApduResponse } from './commands';
import { parseBrizziHistory, parseEmoneyHistory, parseFlazzHistory, parseTapcashHistory } from './parsers/history';
import type { CardTransactionItem } from '../types';

const send = async (data: number[]) => parseApduResponse((await IsoDepNfc.transceive({ data, timeout: 1800 })).response);
const split = (data: number[], size: number) => Array.from({ length: Math.floor(data.length / size) }, (_, index) => data.slice(index * size, (index + 1) * size));

export async function readEmoneyHistory(): Promise<CardTransactionItem[]> {
  const records: number[][] = [];
  for (let index = 1; index <= 10; index++) {
    const response = await send([0x00, 0xb2, index, 0x04, 0x3f]);
    if (!response.isSuccess || response.data.length < 24) break;
    records.push(response.data.slice(0, 24));
  }
  return parseEmoneyHistory(records);
}

export async function readTapcashHistory(): Promise<CardTransactionItem[]> {
  const response = await send([0x90, 0x32, 0x03, 0x00, 0x01, 0x00, 0xf0]);
  return response.isSuccess ? parseTapcashHistory(split(response.data, 16)) : [];
}

export async function readBrizziHistory(): Promise<CardTransactionItem[]> {
  let response = await send([0x90, 0xbd, 0x00, 0x00, 0x07, 0x00, 0x00, 0x00, 0x00, 0x17, 0x00, 0x00, 0x00]);
  let raw = response.data.slice();
  while (response.sw1 === 0x91 && response.sw2 === 0xaf) {
    response = await send([0x90, 0xaf, 0x00, 0x00, 0x00]);
    raw = raw.concat(response.data);
  }
  return parseBrizziHistory(split(raw, 32).map(record => record.map(byte => (byte & 0xff).toString(16).padStart(2, '0')).join('')));
}

export async function readFlazzHistory(): Promise<CardTransactionItem[]> {
  const records: number[][] = [];
  for (let offset = 0; offset < 16; offset++) {
    const response = await send([0x90, 0x32, 0x03, offset * 15, 0x00]);
    if (!response.isSuccess) break;
    records.push(response.data);
  }
  return parseFlazzHistory(records);
}
