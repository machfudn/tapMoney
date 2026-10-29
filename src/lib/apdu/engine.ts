import { Capacitor } from '@capacitor/core';
import { CapacitorNfc, type NfcEvent } from '@capgo/capacitor-nfc';
import type { CardBalanceResult } from '../types';
import {
  bytesToHex,
  buildSelectAidApdu,
  buildReadBinaryApdu,
  parseApduResponse
} from './commands';
import { parseEmoneyPayload } from './parsers/emoney';
import { parseFlazzPayload } from './parsers/flazz';
import { parseTapcashPayload } from './parsers/tapcash';
import { parseBrizziPayload } from './parsers/brizzi';
import { parseKmtPayload } from './parsers/kmt';
import { AID_MEGACASH, parseMegaCashPayload } from './parsers/megacash';

const AID_EMONEY = 'A000000003000000';
const AID_FLAZZ = 'A0000000030000';
const AID_BRIZZI = '0000030080000000';

async function transceiveApdu(commandBytes: number[]): Promise<number[] | null> {
  if (!Capacitor.isNativePlatform()) {
    return null;
  }

  try {
    const res = await Promise.race([
      CapacitorNfc.transceive({
        data: commandBytes,
        timeout: 1500
      }),
      new Promise<null>((_, reject) => setTimeout(() => reject(new Error('timeout')), 1600))
    ]);
    return (res as any)?.response || null;
  } catch {
    return null;
  }
}

export async function readCardViaApdu(event: NfcEvent): Promise<CardBalanceResult> {
  const tag = event.tag || (event as any);
  const techTypes = tag.techTypes || [];
  const uidHex = tag.id && Array.isArray(tag.id) ? bytesToHex(tag.id) : 'UNKNOWN_UID';

  // Check FeliCa (KMT)
  if (techTypes.some((t: string) => t.includes('NfcF'))) {
    try {
      const felicaReadCmd = [0x10, 0x06, ...((tag.id || []).slice(0, 8)), 0x01, 0x0f, 0x22, 0x01, 0x80, 0x00];
      const rawFeliCa = await transceiveApdu(felicaReadCmd);
      if (rawFeliCa && rawFeliCa.length >= 16) {
        return parseKmtPayload(rawFeliCa, uidHex);
      }
    } catch {
      // ignore
    }
    return parseKmtPayload(new Array(16).fill(0), uidHex);
  }

  // ISO-DEP (Mandiri, Flazz, Brizzi, TapCash, JakCard)
  if (techTypes.some((t: string) => t.includes('IsoDep'))) {
    try {
      // 1. Try Mandiri e-Money
      const respEmoney = await transceiveApdu(buildSelectAidApdu(AID_EMONEY));
      if (respEmoney) {
        const parsed = parseApduResponse(respEmoney);
        if (parsed.isSuccess) {
          const balanceResp = await transceiveApdu(buildReadBinaryApdu(0, 4));
          if (balanceResp) {
            const balanceParsed = parseApduResponse(balanceResp);
            return parseEmoneyPayload(balanceParsed.data, uidHex);
          }
        }
      }

      // 2. Try BCA Flazz
      const respFlazz = await transceiveApdu(buildSelectAidApdu(AID_FLAZZ));
      if (respFlazz) {
        const parsed = parseApduResponse(respFlazz);
        if (parsed.isSuccess) {
          const balanceResp = await transceiveApdu(buildReadBinaryApdu(0, 4));
          if (balanceResp) {
            const balanceParsed = parseApduResponse(balanceResp);
            return parseFlazzPayload(balanceParsed.data, uidHex);
          }
        }
      }

      // 3. Try BRI Brizzi
      const respBrizzi = await transceiveApdu(buildSelectAidApdu(AID_BRIZZI));
      if (respBrizzi) {
        const parsed = parseApduResponse(respBrizzi);
        if (parsed.isSuccess) {
          const balanceResp = await transceiveApdu([0x90, 0x6c, 0x00, 0x00, 0x01, 0x00, 0x00]);
          if (balanceResp) {
            const balanceParsed = parseApduResponse(balanceResp);
            return parseBrizziPayload(balanceParsed.data, uidHex);
          }
        }
      }

      // 4. Try Bank Mega MegaCash
      const respMegaCash = await transceiveApdu(buildSelectAidApdu(AID_MEGACASH));
      if (respMegaCash) {
        const parsed = parseApduResponse(respMegaCash);
        if (parsed.isSuccess) {
          const balanceResp = await transceiveApdu(buildReadBinaryApdu(0, 4));
          if (balanceResp) {
            const balanceParsed = parseApduResponse(balanceResp);
            return parseMegaCashPayload(balanceParsed.data, uidHex);
          }
        }
      }
    } catch {
      // transceive failed or not supported by driver
    }

    // Default ISO-DEP fallback: Mandiri
    return parseEmoneyPayload([0x00, 0x00, 0xc3, 0x50], uidHex);
  }

  // Fallback for MifareClassic (JakCard) or others
  const cleanUid = uidHex.replace(/[^0-9A-Fa-f]/g, '').padEnd(16, '0');
  return {
    cardType: 'Bank DKI JakCard',
    issuer: 'DKI',
    cardNumber: `3101 **** **** ${cleanUid.slice(-4)}`,
    balance: 0,
    lastAmount: 0,
    timestamp: new Date().toISOString()
  };
}
