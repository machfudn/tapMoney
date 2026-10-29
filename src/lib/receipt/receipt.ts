import type { CardBalanceResult } from '../types';

export function formatRupiah(amount: number): string {
  return new Intl.NumberFormat('id-ID', {
    style: 'currency',
    currency: 'IDR',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0
  }).format(amount);
}

export function generateReceiptText(card: CardBalanceResult, terminalName = 'TERMINAL_TAP_01'): string {
  const dateStr = new Date(card.timestamp).toLocaleString('id-ID', {
    dateStyle: 'medium',
    timeStyle: 'medium'
  });

  return [
    '================================',
    '        TAPMONEY INFO           ',
    '    BUKTI PEMERIKSAAN SALDO     ',
    '================================',
    `Waktu    : ${dateStr}`,
    `Lokasi   : ${terminalName}`,
    `Kartu    : ${card.cardType}`,
    `No Kartu : ${card.cardNumber}`,
    '--------------------------------',
    `SALDO    : ${formatRupiah(card.balance)}`,
    '--------------------------------',
    'Simpan bukti ini sebagai catatan',
    'transaksi uang elektronik Anda. ',
    '================================'
  ].join('\n');
}

export async function shareOrCopyReceipt(
  card: CardBalanceResult,
  terminalName?: string
): Promise<{ method: 'share' | 'clipboard' | 'none'; success: boolean }> {
  const text = generateReceiptText(card, terminalName);

  if (typeof navigator !== 'undefined' && navigator.share) {
    try {
      await navigator.share({
        title: `Struk Saldo ${card.cardType}`,
        text: text
      });
      return { method: 'share', success: true };
    } catch (err) {
      if ((err as Error).name === 'AbortError') {
        return { method: 'share', success: false };
      }
    }
  }

  if (typeof navigator !== 'undefined' && navigator.clipboard && navigator.clipboard.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return { method: 'clipboard', success: true };
    } catch {
      return { method: 'none', success: false };
    }
  }

  return { method: 'none', success: false };
}

export function printCurrentReceipt(): void {
  if (typeof window !== 'undefined' && window.print) {
    window.print();
  }
}
