// Card number formatting and deterministic CAN derivation from UID

export function generateCardNumber(prefix: string, uidHex: string): string {
  const clean = uidHex.replace(/[^0-9A-Fa-f]/g, '').toUpperCase();
  // Generate deterministic 12-digit numeric suffix from UID bytes
  let digits = '';
  for (let i = 0; i < clean.length; i++) {
    digits += (clean.charCodeAt(i) * 7 + i * 3) % 10;
  }
  // Ensure exactly 12 digits
  digits = (digits + '819234567890').slice(0, 12);
  const p = prefix.slice(0, 4);
  return `${p} ${digits.slice(0, 4)} ${digits.slice(4, 8)} ${digits.slice(8, 12)}`;
}

export function formatDisplayCardNumber(cardNumber: string | undefined, mask: boolean): string {
  if (!cardNumber) return '•••• •••• •••• ••••';
  if (!mask) return cardNumber;
  const parts = cardNumber.split(' ');
  if (parts.length === 4) {
    return `${parts[0]} **** **** ${parts[3]}`;
  }
  // If not space-separated 4 blocks, mask middle characters
  if (cardNumber.length >= 12) {
    return `${cardNumber.slice(0, 4)} **** **** ${cardNumber.slice(-4)}`;
  }
  return cardNumber;
}

export function getRawCardNumber(cardNumber: string | undefined): string {
  if (!cardNumber) return '';
  return cardNumber.replace(/\s+/g, '');
}

export function formatDisplayBalance(amount: number | undefined, hide: boolean): string {
  if (hide) {
    return 'Rp ••••••';
  }
  const val = typeof amount === 'number' && !isNaN(amount) ? amount : 0;
  return new Intl.NumberFormat('id-ID', {
    style: 'currency',
    currency: 'IDR',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0
  }).format(val);
}
