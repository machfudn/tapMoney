import { Capacitor } from '@capacitor/core';
import { CapacitorNfc, type NfcEvent } from '@capgo/capacitor-nfc';
import type { CardBalanceResult } from '../types';
import { IsoDepNfc } from '../iso-dep';
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
import { generateCardNumber } from './card-formatter';
import { readBrizziHistory, readEmoneyHistory, readFlazzHistory, readTapcashHistory } from './reader';

const AID_EMONEY = 'A000000003000000';
const AID_FLAZZ = 'A0000000030000';
const AID_FLAZZ_GEN2 = 'D2760000850101';
const AID_BRIZZI = '0000030080000000';
const AID_TAPCASH = 'A000000003000001';
const AID_TAPCASH_ALT = '315041592E5359532E4444463031'; // 1PAY.SYS.DDF01

async function transceiveApdu(commandBytes: number[]): Promise<number[] | null> {
  if (!Capacitor.isNativePlatform()) {
    return null;
  }

  try {
    const res = await Promise.race([
      IsoDepNfc.transceive({
        data: commandBytes,
        timeout: 1500
      }),
      new Promise<null>((_, reject) => setTimeout(() => reject(new Error('timeout')), 1600))
    ]);
    return (res as any)?.response || null;
  } catch {
    try {
      const res = await CapacitorNfc.transceive({ data: commandBytes, timeout: 1500 });
      return (res as any)?.response || null;
    } catch {
      return null;
    }
  }
}

export async function readCardViaApdu(event: NfcEvent): Promise<CardBalanceResult> {
  const tag = event.tag || (event as any);
  const techTypes = tag.techTypes || [];
  const uidHex = tag.id && Array.isArray(tag.id) ? bytesToHex(tag.id) : 'UNKNOWN_UID';
  const cardId = uidHex === 'UNKNOWN_UID' ? undefined : uidHex;

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
          const balanceResp = await transceiveApdu([0x00, 0xb5, 0x00, 0x00, 0x0a]);
          if (balanceResp) {
            const balanceParsed = parseApduResponse(balanceResp);
            const card = parseEmoneyPayload(balanceParsed.data, uidHex);
            card.history = await readEmoneyHistory();
            return card;
          }
        }
      }

      // 2. Try BCA Flazz
      let respFlazz = await transceiveApdu(buildSelectAidApdu(AID_FLAZZ));
      if (!respFlazz || !parseApduResponse(respFlazz).isSuccess) {
        respFlazz = await transceiveApdu(buildSelectAidApdu(AID_FLAZZ_GEN2));
      }
      if (respFlazz) {
        const parsed = parseApduResponse(respFlazz);
        if (parsed.isSuccess) {
          const balanceResp = await transceiveApdu(buildReadBinaryApdu(0, 4));
          if (balanceResp) {
            const balanceParsed = parseApduResponse(balanceResp);
            const card = parseFlazzPayload(balanceParsed.data, uidHex);
            card.history = await readFlazzHistory();
            return card;
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
            const card = parseBrizziPayload(balanceParsed.data, uidHex);
            card.history = await readBrizziHistory();
            return card;
          }
        }
      }

      // 4. Try BNI TapCash
      let respTapcash = await transceiveApdu(buildSelectAidApdu(AID_TAPCASH));
      if (!respTapcash || !parseApduResponse(respTapcash).isSuccess) {
        respTapcash = await transceiveApdu(buildSelectAidApdu(AID_TAPCASH_ALT));
      }
      if (respTapcash) {
        const parsed = parseApduResponse(respTapcash);
        if (parsed.isSuccess) {
          const balanceResp = await transceiveApdu(buildReadBinaryApdu(0, 4));
          if (balanceResp) {
            const balanceParsed = parseApduResponse(balanceResp);
            const card = parseTapcashPayload(balanceParsed.data, uidHex);
            card.history = await readTapcashHistory();
            return card;
          }
        }
      }

      // 5. Try Bank Mega MegaCash
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

    // Default ISO-DEP fallback when no issuer AID recognized
    return {
      cardType: 'Kartu Elektronik (ISO-DEP)',
      issuer: 'Unknown',
      cardNumber: generateCardNumber('8888', uidHex),
      balance: 0,
      lastAmount: 0,
      timestamp: new Date().toISOString()
    };
  }

  // Fallback for MifareClassic (JakCard) or others
  return {
    cardType: 'Bank DKI JakCard',
    issuer: 'DKI',
    cardNumber: generateCardNumber('3101', uidHex),
    balance: 0,
    lastAmount: 0,
    timestamp: new Date().toISOString()
  };
}
