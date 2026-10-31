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

// Verified AID & Command definitions (eSaldo Info parity)
// Mandiri e-Money DF: 00 A4 04 00 08 00 00 00 00 00 00 00 01
const APDU_SELECT_EMONEY = [0x00, 0xa4, 0x04, 0x00, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01];
const APDU_EMONEY_NUMBER = [0x00, 0xb3, 0x00, 0x00, 0x3f];
const APDU_EMONEY_BALANCE = [0x00, 0xb5, 0x00, 0x00, 0x0a];

// BCA Flazz DF (Gen 1 & Gen 2) & PBOC Commands
const APDU_SELECT_FLAZZ_GEN1 = [0x00, 0xa4, 0x04, 0x00, 0x0b, 0xa0, 0x00, 0x00, 0x00, 0x18, 0x0f, 0x00, 0x00, 0x01, 0x80, 0x01];
const APDU_SELECT_FLAZZ_GEN2 = [0x00, 0xa4, 0x04, 0x00, 0x07, 0xd2, 0x76, 0x00, 0x00, 0x85, 0x01, 0x01];
const APDU_FLAZZ_READ_REC_1 = [0x00, 0xb2, 0x01, 0x0c, 0x00];
const APDU_FLAZZ_READ_REC_2 = [0x00, 0xb2, 0x01, 0x14, 0x00];
const APDU_FLAZZ_READ_REC_3 = [0x00, 0xb2, 0x02, 0x0c, 0x00];
const APDU_FLAZZ_READ_BIN = [0x00, 0xb0, 0x00, 0x00, 0x00];
const APDU_FLAZZ_BALANCE = [0x80, 0x5c, 0x00, 0x02, 0x04];

// BRI Brizzi DESFire Select App & Read commands
const APDU_SELECT_BRIZZI = [0x90, 0x5a, 0x00, 0x00, 0x03, 0x01, 0x00, 0x00, 0x00];
const APDU_BRIZZI_CARD_NUM = [0x90, 0xbd, 0x00, 0x00, 0x07, 0x00, 0x00, 0x00, 0x00, 0x17, 0x00, 0x00, 0x00];
const APDU_BRIZZI_BALANCE = [0x90, 0x6c, 0x00, 0x00, 0x01, 0x00, 0x00];

// BNI TapCash DF & Read commands
const APDU_SELECT_TAPCASH_1 = [0x00, 0xa4, 0x04, 0x00, 0x08, 0xa0, 0x00, 0x42, 0x4e, 0x49, 0x10, 0x00, 0x01];
const APDU_SELECT_TAPCASH_2 = [0x00, 0xa4, 0x04, 0x00, 0x08, 0xa0, 0x00, 0x42, 0x4e, 0x49, 0x99, 0x99, 0x99];
const APDU_TAPCASH_READ = [0x90, 0x32, 0x03, 0x00, 0x00];

// Bank DKI JakCard DF
const APDU_SELECT_JAKCARD = [0x00, 0xa4, 0x04, 0x00, 0x09, 0xa0, 0x00, 0x00, 0x00, 0x03, 0x86, 0x98, 0x07, 0x01];

async function transceiveApdu(commandBytes: number[]): Promise<number[] | null> {
  if (!Capacitor.isNativePlatform()) {
    return null;
  }

  try {
    const res = await IsoDepNfc.transceive({
      data: commandBytes,
      timeout: 3500
    });
    return res?.response || null;
  } catch {
    try {
      const res = await CapacitorNfc.transceive({ data: commandBytes, timeout: 3500 });
      return (res as any)?.response || null;
    } catch {
      return null;
    }
  }
}

export async function readCardViaApdu(event: NfcEvent): Promise<CardBalanceResult> {
  const tag = event.tag || (event as any);
  const techTypes: string[] = tag.techTypes || [];
  const uidHex = tag.id && Array.isArray(tag.id) ? bytesToHex(tag.id) : 'UNKNOWN_UID';
  const cardId = uidHex === 'UNKNOWN_UID' ? undefined : uidHex;

  // 1. Check FeliCa (KMT Commuter Line)
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

  // 2. ISO-DEP (Mandiri e-Money, BCA Flazz, BRI Brizzi, BNI TapCash, JakCard)
  if (techTypes.some((t: string) => t.includes('IsoDep') || t.includes('NfcA'))) {
    try {
      // --- Probing Step 1: Mandiri e-Money ---
      const respEmoney = await transceiveApdu(APDU_SELECT_EMONEY);
      if (respEmoney && parseApduResponse(respEmoney).isSuccess) {
        let isNewCard = false;
        let realCardNumber: string | undefined;
        const respNum = await transceiveApdu(APDU_EMONEY_NUMBER);
        if (respNum && respNum.length >= 8) {
          realCardNumber = bytesToHex(respNum.slice(0, 8));
          if (respNum.length >= 20 && (respNum[18] & 0xff) === 0x83) {
            isNewCard = true;
          }
        }

        // Read live balance
        const balanceResp = await transceiveApdu(APDU_EMONEY_BALANCE);
        if (balanceResp) {
          const balanceParsed = parseApduResponse(balanceResp);
          const card = parseEmoneyPayload(balanceParsed.data, uidHex, realCardNumber);
          try {
            card.history = await readEmoneyHistory(isNewCard);
          } catch {
            card.history = [];
          }
          return card;
        }
      }

      // --- Probing Step 2: BCA Flazz ---
      let isGen2 = false;
      let respFlazz = await transceiveApdu(APDU_SELECT_FLAZZ_GEN1);
      if (!respFlazz || !parseApduResponse(respFlazz).isSuccess) {
        respFlazz = await transceiveApdu(APDU_SELECT_FLAZZ_GEN2);
        if (respFlazz && parseApduResponse(respFlazz).isSuccess) {
          isGen2 = true;
        }
      }
      if (respFlazz && parseApduResponse(respFlazz).isSuccess) {
        let realCardNumber: string | undefined;
        let liveBalance: number | undefined;

        // Try reading Track 2 record for real 16-digit PAN across standard record indices (no GPO)
        const recordCommands = [APDU_FLAZZ_READ_REC_1, APDU_FLAZZ_READ_REC_2, APDU_FLAZZ_READ_REC_3, APDU_FLAZZ_READ_BIN];
        for (const cmd of recordCommands) {
          const recResp = await transceiveApdu(cmd);
          if (recResp && recResp.length > 2) {
            // Check ASCII: ;PAN=
            const ascii = String.fromCharCode(...recResp.filter(b => b >= 32 && b <= 126));
            const semiIdx = ascii.indexOf(';');
            const eqIdx = ascii.indexOf('=');
            if (semiIdx !== -1 && eqIdx !== -1 && eqIdx > semiIdx) {
              const panCandidate = ascii.substring(semiIdx + 1, eqIdx).replace(/\D/g, '');
              if (panCandidate.length >= 16) {
                realCardNumber = panCandidate.slice(0, 16);
                break;
              }
            }
            // Check BCD/HEX Track 2 equivalent data: 'D' separator
            const hexStr = bytesToHex(recResp);
            const dIdx = hexStr.indexOf('D');
            if (dIdx >= 16) {
              const panCandidate = hexStr.substring(dIdx - 16, dIdx);
              if (/^\d{16}$/.test(panCandidate)) {
                realCardNumber = panCandidate;
                break;
              }
            }
          }
        }

        // Standard PBOC Get Balance: 80 5C 00 02 04 (3-byte Big Endian integer at resp[1..3])
        const balResp = await transceiveApdu(APDU_FLAZZ_BALANCE);
        if (balResp && balResp.length >= 4) {
          const balParsed = parseApduResponse(balResp);
          if (balParsed.isSuccess && balParsed.data.length >= 4) {
            liveBalance = (
              ((balParsed.data[1] & 0xff) << 16) |
              ((balParsed.data[2] & 0xff) << 8) |
              (balParsed.data[3] & 0xff)
            ) >>> 0;
          } else if (balParsed.isSuccess && balParsed.data.length >= 3) {
            liveBalance = (
              ((balParsed.data[0] & 0xff) << 16) |
              ((balParsed.data[1] & 0xff) << 8) |
              (balParsed.data[2] & 0xff)
            ) >>> 0;
          }
        }

        const card = parseFlazzPayload(balResp ? parseApduResponse(balResp).data : [], uidHex, realCardNumber, liveBalance);
        try {
          card.history = await readFlazzHistory(isGen2);
        } catch {
          card.history = [];
        }
        return card;
      }

      // --- Probing Step 3: BRI Brizzi (DESFire) ---
      const respBrizzi = await transceiveApdu(APDU_SELECT_BRIZZI);
      if (respBrizzi && parseApduResponse(respBrizzi).isSuccess) {
        let realCardNumber: string | undefined;

        // Read File 0 for real 16-digit PAN
        const cardNumResp = await transceiveApdu(APDU_BRIZZI_CARD_NUM);
        if (cardNumResp && cardNumResp.length >= 11) {
          const hex = bytesToHex(cardNumResp);
          if (hex.length >= 22) {
            realCardNumber = hex.substring(6, 22);
          }
        }

        // Get Value for File 0 (balance)
        const balanceResp = await transceiveApdu(APDU_BRIZZI_BALANCE);
        if (balanceResp) {
          const balanceParsed = parseApduResponse(balanceResp);
          const card = parseBrizziPayload(balanceParsed.data, uidHex, realCardNumber);
          try {
            card.history = await readBrizziHistory();
          } catch {
            card.history = [];
          }
          return card;
        }
      }

      // --- Probing Step 4: BNI TapCash ---
      let respTapcash = await transceiveApdu(APDU_SELECT_TAPCASH_1);
      if (!respTapcash || !parseApduResponse(respTapcash).isSuccess) {
        respTapcash = await transceiveApdu(APDU_SELECT_TAPCASH_2);
      }
      if (respTapcash && parseApduResponse(respTapcash).isSuccess) {
        // Read Tapcash multi-record (Balance at bytes 2..4, Card Number at bytes 8..15)
        const readResp = await transceiveApdu(APDU_TAPCASH_READ);
        if (readResp && readResp.length >= 16) {
          let balance = ((readResp[2] & 0xff) << 16) | ((readResp[3] & 0xff) << 8) | (readResp[4] & 0xff);
          if ((readResp[2] & 0x80) !== 0) {
            balance = (balance | 0xff000000) >>> 0;
          }
          const realCardNumber = bytesToHex(readResp.slice(8, 16));
          const totalRecords = readResp.length >= 41 ? readResp[40] : undefined;
          const card = parseTapcashPayload([], uidHex, realCardNumber, balance);
          try {
            card.history = await readTapcashHistory(totalRecords);
          } catch {
            card.history = [];
          }
          return card;
        }
      }

      // --- Probing Step 5: Bank Mega MegaCash ---
      const respMegaCash = await transceiveApdu(buildSelectAidApdu(AID_MEGACASH));
      if (respMegaCash && parseApduResponse(respMegaCash).isSuccess) {
        const balanceResp = await transceiveApdu(buildReadBinaryApdu(0, 4));
        if (balanceResp) {
          const balanceParsed = parseApduResponse(balanceResp);
          return parseMegaCashPayload(balanceParsed.data, uidHex);
        }
      }

      // --- Probing Step 6: Bank DKI JakCard ---
      const respJakCard = await transceiveApdu(APDU_SELECT_JAKCARD);
      if (respJakCard && parseApduResponse(respJakCard).isSuccess) {
        const balanceResp = await transceiveApdu(buildReadBinaryApdu(0, 4));
        const balance = balanceResp && balanceResp.length >= 4
          ? ((balanceResp[0] << 24) | (balanceResp[1] << 16) | (balanceResp[2] << 8) | balanceResp[3]) >>> 0
          : 0;
        return {
          cardType: 'Bank DKI JakCard',
          issuer: 'DKI',
          cardNumber: generateCardNumber('3101', uidHex),
          cardId,
          balance: balance <= 20_000_000 ? balance : 0,
          lastAmount: 0,
          timestamp: new Date().toISOString()
        };
      }
    } catch {
      // APDU probe cycle failed
    }

    // Default ISO-DEP fallback when no issuer recognized
    return {
      cardType: 'Kartu Elektronik (ISO-DEP)',
      issuer: 'Unknown',
      cardNumber: generateCardNumber('8888', uidHex),
      cardId,
      balance: 0,
      lastAmount: 0,
      timestamp: new Date().toISOString()
    };
  }

  // Fallback for unknown NFC card types
  return {
    cardType: 'Kartu Elektronik (NFC)',
    issuer: 'Unknown',
    cardNumber: generateCardNumber('8888', uidHex),
    cardId,
    balance: 0,
    lastAmount: 0,
    timestamp: new Date().toISOString()
  };
}
