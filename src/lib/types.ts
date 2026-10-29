export type CardIssuer = 'Mandiri' | 'BCA' | 'BRI' | 'BNI' | 'DKI' | 'KCI' | 'Mega' | 'Unknown';

export interface CardBalanceResult {
  cardType: string;
  issuer: CardIssuer;
  cardNumber: string;
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
