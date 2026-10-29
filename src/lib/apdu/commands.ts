export interface ApduResponse {
  data: number[];
  sw1: number;
  sw2: number;
  isSuccess: boolean;
  hex: string;
}

// Convert byte array to hexadecimal uppercase string
export function bytesToHex(bytes: number[]): string {
  return bytes.map(b => (b & 0xff).toString(16).padStart(2, '0')).join('').toUpperCase();
}

// Convert hexadecimal string to byte array
export function hexToBytes(hex: string): number[] {
  const clean = hex.replace(/[^0-9A-Fa-f]/g, '');
  const bytes: number[] = [];
  for (let i = 0; i < clean.length; i += 2) {
    bytes.push(parseInt(clean.substring(i, i + 2), 16));
  }
  return bytes;
}

// Construct ISO 7816-4 APDU command packet
export function buildApdu(
  cla: number,
  ins: number,
  p1: number,
  p2: number,
  data: number[] = [],
  le?: number
): number[] {
  const apdu: number[] = [cla & 0xff, ins & 0xff, p1 & 0xff, p2 & 0xff];

  if (data.length > 0) {
    apdu.push(data.length & 0xff);
    apdu.push(...data);
  }

  if (typeof le === 'number') {
    apdu.push(le & 0xff);
  }

  return apdu;
}

// Build standard SELECT FILE / AID APDU
export function buildSelectAidApdu(aidHex: string): number[] {
  const aidBytes = hexToBytes(aidHex);
  return buildApdu(0x00, 0xa4, 0x04, 0x00, aidBytes, 0x00);
}

// Build standard READ BINARY APDU
export function buildReadBinaryApdu(offset = 0, length = 0): number[] {
  const p1 = (offset >> 8) & 0xff;
  const p2 = offset & 0xff;
  return buildApdu(0x00, 0xb0, p1, p2, [], length);
}

// Build standard READ RECORD APDU
export function buildReadRecordApdu(recordNum: number, sfi: number, length = 0): number[] {
  const p2 = ((sfi & 0x1f) << 3) | 0x04;
  return buildApdu(0x00, 0xb2, recordNum & 0xff, p2, [], length);
}

// Parse raw APDU response into data and status words
export function parseApduResponse(response: number[]): ApduResponse {
  if (response.length < 2) {
    return {
      data: response,
      sw1: 0x00,
      sw2: 0x00,
      isSuccess: false,
      hex: bytesToHex(response)
    };
  }

  const sw1 = response[response.length - 2];
  const sw2 = response[response.length - 1];
  const data = response.slice(0, response.length - 2);
  const isSuccess = sw1 === 0x90 && sw2 === 0x00;

  return {
    data,
    sw1,
    sw2,
    isSuccess,
    hex: bytesToHex(data)
  };
}
