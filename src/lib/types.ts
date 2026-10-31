export type CardIssuer = 'Mandiri' | 'BCA' | 'BRI' | 'BNI' | 'DKI' | 'KCI' | 'Mega' | 'Unknown';

export interface CardBalanceResult {
  cardType: string;
  issuer: CardIssuer;
  cardNumber: string;
  /** Stable physical-card identity. Never display as PAN. */
  cardId?: string;
  balance: number;
  lastAmount?: number;
  history?: CardTransactionItem[];
  timestamp: string;
}

export interface CardTransactionItem {
  type: 'Payment' | 'Top up' | 'Unknown';
  amount: number;
  date: string;
  time: string;
  balance?: number;
  terminalId?: string;
}

export interface BackendCardDefinition {
  id: string;
  name: string;
  issuer: string;
  tech: 'IsoDep' | 'NfcF' | 'NfcA';
  aidHex?: string;
  description: string;
}
