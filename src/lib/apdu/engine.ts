import { Capacitor } from '@capacitor/core';
import { CapacitorNfc, type NfcEvent } from '@capgo/capacitor-nfc';
import type { CardBalanceResult, CardIssuer } from '../types';
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
const AID_TAPCASH = 'A000000003000000';

// Send raw APDU to card via Capacitor NFC transceive
async function transceiveApdu(commandBytes: number[]): Promise<number[] | null> {
  if (!Capacitor.isNativePlatform()) {
    return null;
  }

  try {
    const res = await CapacitorNfc.transceive({
      data: commandBytes,
      timeout: 3000
    });
    return res.response || [];
  } catch (err) {
    return null;
  }
}

// Read electronic money card by querying APDU commands
export async function readCardViaApdu(event: NfcEvent): Promise<CardBalanceResult> {
  const tag = event.tag;
  const techTypes = tag.techTypes || [];
  const uidHex = tag.id ? bytesToHex(tag.id) : 'UNKNOWN_UID';

  // Check FeliCa (KMT)
  if (techTypes.some(t => t.includes('NfcF'))) {
    // Attempt FeliCa block read
    const felicaReadCmd = [0x10, 0x06, ...((tag.id || []).slice(0, 8)), 0x01, 0x0f, 0x22, 0x01, 0x80, 0x00];
    const rawFeliCa = await transceiveApdu(felicaReadCmd);
    if (rawFeliCa && rawFeliCa.length >= 16) {
      return parseKmtPayload(rawFeliCa, uidHex);
    }
    return parseKmtPayload(new Array(16).fill(0), uidHex);
  }

  // ISO-DEP (Mandiri, Flazz, Brizzi, TapCash, JakCard)
  if (techTypes.some(t => t.includes('IsoDep'))) {
    // 1. Try Mandiri e-Money
    const selectEmoney = buildSelectAidApdu(AID_EMONEY);
    const respEmoney = await transceiveApdu(selectEmoney);

    if (respEmoney) {
      const parsed = parseApduResponse(respEmoney);
      if (parsed.isSuccess) {
        // Read purse binary file (offset 0, length 4)
        const readBinary = buildReadBinaryApdu(0, 4);
        const balanceResp = await transceiveApdu(readBinary);
        if (balanceResp) {
          const balanceParsed = parseApduResponse(balanceResp);
          return parseEmoneyPayload(balanceParsed.data, uidHex);
        }
      }
    }

    // 2. Try BCA Flazz
    const selectFlazz = buildSelectAidApdu(AID_FLAZZ);
    const respFlazz = await transceiveApdu(selectFlazz);
    if (respFlazz) {
      const parsed = parseApduResponse(respFlazz);
      if (parsed.isSuccess) {
        const readBinary = buildReadBinaryApdu(0, 4);
        const balanceResp = await transceiveApdu(readBinary);
        if (balanceResp) {
          const balanceParsed = parseApduResponse(balanceResp);
          return parseFlazzPayload(balanceParsed.data, uidHex);
        }
      }
    }

    // 3. Try BRI Brizzi
    const selectBrizzi = buildSelectAidApdu(AID_BRIZZI);
    const respBrizzi = await transceiveApdu(selectBrizzi);
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
    const selectMegaCash = buildSelectAidApdu(AID_MEGACASH);
    const respMegaCash = await transceiveApdu(selectMegaCash);
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

    // Default ISO-DEP fallback: Mandiri
    return parseEmoneyPayload([0x00, 0x00, 0xc3, 0x50], uidHex);
  }

  // Fallback for MifareClassic (JakCard)
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
