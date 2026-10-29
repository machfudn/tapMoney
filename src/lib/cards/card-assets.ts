import type { CardIssuer } from '../types';

// Resolve card asset URL compatible with Vite bundler & native ESM
const resolveCardAsset = (filename: string): string =>
  new URL(`../../assets/cards/${filename}`, import.meta.url).href;

export type KnownIssuer = Exclude<CardIssuer, 'Unknown'>;
export type ChipPosition = 'left' | 'right' | 'hidden';

export interface CardAssetPaths {
  bgWebp: string;
  bgPng: string;
  logoWebp: string;
  logoPng: string;
}

export interface CardTextColorStyle {
  color: string;
  secondaryColor: string;
  panColor: string;
  balanceColor: string;
  textShadow: string;
}

export interface CardThemeMetadata {
  id: CardIssuer;
  issuer: CardIssuer;
  cardType: string;
  displayName: string;
  bankName: string;
  bgImage: string;
  logoImage: string;
  bgImagePng: string;
  logoImagePng: string;
  textColorStyle: CardTextColorStyle;
  chipPosition: ChipPosition;
  fallbackGradient: string;
  accentColor: string;
}

export const CARD_ASSETS: Record<KnownIssuer, CardAssetPaths> = {
  BCA: {
    bgWebp: resolveCardAsset('flazz.webp'),
    bgPng: resolveCardAsset('flazz.png'),
    logoWebp: resolveCardAsset('logo_flazz.webp'),
    logoPng: resolveCardAsset('logo_flazz.png'),
  },
  Mandiri: {
    bgWebp: resolveCardAsset('emoney.webp'),
    bgPng: resolveCardAsset('emoney.png'),
    logoWebp: resolveCardAsset('logo_emoney.webp'),
    logoPng: resolveCardAsset('logo_emoney.png'),
  },
  BRI: {
    bgWebp: resolveCardAsset('brizzi.webp'),
    bgPng: resolveCardAsset('brizzi.png'),
    logoWebp: resolveCardAsset('logo_brizzi.webp'),
    logoPng: resolveCardAsset('logo_brizzi.png'),
  },
  BNI: {
    bgWebp: resolveCardAsset('tapcash.webp'),
    bgPng: resolveCardAsset('tapcash.png'),
    logoWebp: resolveCardAsset('logo_tapcash.webp'),
    logoPng: resolveCardAsset('logo_tapcash.png'),
  },
  DKI: {
    bgWebp: resolveCardAsset('jaklingko.webp'),
    bgPng: resolveCardAsset('jaklingko.png'),
    logoWebp: resolveCardAsset('logo_jaklingko.webp'),
    logoPng: resolveCardAsset('logo_jaklingko.png'),
  },
  KCI: {
    bgWebp: resolveCardAsset('kmt.webp'),
    bgPng: resolveCardAsset('kmt.png'),
    logoWebp: resolveCardAsset('logo_kmt.webp'),
    logoPng: resolveCardAsset('logo_kmt.png'),
  },
  Mega: {
    bgWebp: resolveCardAsset('megacash.webp'),
    bgPng: resolveCardAsset('megacash.png'),
    logoWebp: resolveCardAsset('logo_megacash.webp'),
    logoPng: resolveCardAsset('logo_megacash.png'),
  },
};

const defaultLightText: CardTextColorStyle = {
  color: '#ffffff',
  secondaryColor: 'rgba(255, 255, 255, 0.85)',
  panColor: 'rgba(255, 255, 255, 0.95)',
  balanceColor: '#ffffff',
  textShadow: '0 1px 3px rgba(0, 0, 0, 0.5)',
};

export const CARD_THEMES: Record<CardIssuer, CardThemeMetadata> = {
  BCA: {
    id: 'BCA',
    issuer: 'BCA',
    cardType: 'BCA Flazz',
    displayName: 'Flazz',
    bankName: 'Bank Central Asia',
    bgImage: CARD_ASSETS.BCA.bgWebp,
    logoImage: CARD_ASSETS.BCA.logoWebp,
    bgImagePng: CARD_ASSETS.BCA.bgPng,
    logoImagePng: CARD_ASSETS.BCA.logoPng,
    textColorStyle: defaultLightText,
    chipPosition: 'left',
    fallbackGradient: 'linear-gradient(135deg, #2563eb 0%, #1d4ed8 45%, #172554 100%)',
    accentColor: '#005baa',
  },
  Mandiri: {
    id: 'Mandiri',
    issuer: 'Mandiri',
    cardType: 'Mandiri e-Money',
    displayName: 'e-Money',
    bankName: 'Bank Mandiri',
    bgImage: CARD_ASSETS.Mandiri.bgWebp,
    logoImage: CARD_ASSETS.Mandiri.logoWebp,
    bgImagePng: CARD_ASSETS.Mandiri.bgPng,
    logoImagePng: CARD_ASSETS.Mandiri.logoPng,
    textColorStyle: defaultLightText,
    chipPosition: 'left',
    fallbackGradient: 'linear-gradient(135deg, #0284c7 0%, #0369a1 40%, #0c4a6e 100%)',
    accentColor: '#003d79',
  },
  BRI: {
    id: 'BRI',
    issuer: 'BRI',
    cardType: 'BRI Brizzi',
    displayName: 'Brizzi',
    bankName: 'Bank Rakyat Indonesia',
    bgImage: CARD_ASSETS.BRI.bgWebp,
    logoImage: CARD_ASSETS.BRI.logoWebp,
    bgImagePng: CARD_ASSETS.BRI.bgPng,
    logoImagePng: CARD_ASSETS.BRI.logoPng,
    textColorStyle: defaultLightText,
    chipPosition: 'left',
    fallbackGradient: 'linear-gradient(135deg, #0284c7 0%, #1e40af 50%, #0f172a 100%)',
    accentColor: '#00529c',
  },
  BNI: {
    id: 'BNI',
    issuer: 'BNI',
    cardType: 'BNI TapCash',
    displayName: 'TapCash',
    bankName: 'Bank Negara Indonesia',
    bgImage: CARD_ASSETS.BNI.bgWebp,
    logoImage: CARD_ASSETS.BNI.logoWebp,
    bgImagePng: CARD_ASSETS.BNI.bgPng,
    logoImagePng: CARD_ASSETS.BNI.logoPng,
    textColorStyle: defaultLightText,
    chipPosition: 'left',
    fallbackGradient: 'linear-gradient(135deg, #ea580c 0%, #c2410c 50%, #7c2d12 100%)',
    accentColor: '#f15a24',
  },
  DKI: {
    id: 'DKI',
    issuer: 'DKI',
    cardType: 'Bank DKI JakCard',
    displayName: 'JakLingko / JakCard',
    bankName: 'Bank DKI',
    bgImage: CARD_ASSETS.DKI.bgWebp,
    logoImage: CARD_ASSETS.DKI.logoWebp,
    bgImagePng: CARD_ASSETS.DKI.bgPng,
    logoImagePng: CARD_ASSETS.DKI.logoPng,
    textColorStyle: defaultLightText,
    chipPosition: 'left',
    fallbackGradient: 'linear-gradient(135deg, #0d9488 0%, #0f766e 50%, #115e59 100%)',
    accentColor: '#00a39d',
  },
  KCI: {
    id: 'KCI',
    issuer: 'KCI',
    cardType: 'KMT Commuter Line',
    displayName: 'Kartu Multi Trip',
    bankName: 'KCI Commuter Line',
    bgImage: CARD_ASSETS.KCI.bgWebp,
    logoImage: CARD_ASSETS.KCI.logoWebp,
    bgImagePng: CARD_ASSETS.KCI.bgPng,
    logoImagePng: CARD_ASSETS.KCI.logoPng,
    textColorStyle: defaultLightText,
    chipPosition: 'left',
    fallbackGradient: 'linear-gradient(135deg, #dc2626 0%, #b91c1c 50%, #7f1d1d 100%)',
    accentColor: '#da251d',
  },
  Mega: {
    id: 'Mega',
    issuer: 'Mega',
    cardType: 'Bank Mega MegaCash',
    displayName: 'MegaCash',
    bankName: 'Bank Mega',
    bgImage: CARD_ASSETS.Mega.bgWebp,
    logoImage: CARD_ASSETS.Mega.logoWebp,
    bgImagePng: CARD_ASSETS.Mega.bgPng,
    logoImagePng: CARD_ASSETS.Mega.logoPng,
    textColorStyle: defaultLightText,
    chipPosition: 'left',
    fallbackGradient: 'linear-gradient(135deg, #d97706 0%, #b45309 50%, #78350f 100%)',
    accentColor: '#f7941d',
  },
  Unknown: {
    id: 'Unknown',
    issuer: 'Unknown',
    cardType: 'Kartu Elektronik',
    displayName: 'Uang Elektronik',
    bankName: 'NFC Card',
    bgImage: '',
    logoImage: '',
    bgImagePng: '',
    logoImagePng: '',
    textColorStyle: defaultLightText,
    chipPosition: 'left',
    fallbackGradient: 'linear-gradient(135deg, #1e293b 0%, #0f172a 100%)',
    accentColor: '#64748b',
  },
};

/**
 * Get card theme metadata by CardIssuer.
 */
export function getCardTheme(issuer: CardIssuer): CardThemeMetadata {
  return CARD_THEMES[issuer] ?? CARD_THEMES.Unknown;
}

/**
 * Get card theme metadata by cardType name string.
 */
export function getCardThemeByType(cardType?: string): CardThemeMetadata {
  if (!cardType) return CARD_THEMES.Unknown;
  const lower = cardType.toLowerCase();

  if (lower.includes('flazz') || lower.includes('bca')) return CARD_THEMES.BCA;
  if (lower.includes('emoney') || lower.includes('e-money') || lower.includes('mandiri')) return CARD_THEMES.Mandiri;
  if (lower.includes('brizzi') || lower.includes('bri')) return CARD_THEMES.BRI;
  if (lower.includes('tapcash') || lower.includes('bni')) return CARD_THEMES.BNI;
  if (lower.includes('jaklingko') || lower.includes('jaklinko') || lower.includes('jakcard') || lower.includes('dki')) return CARD_THEMES.DKI;
  if (lower.includes('kmt') || lower.includes('commuter') || lower.includes('kci')) return CARD_THEMES.KCI;
  if (lower.includes('megacash') || lower.includes('mega')) return CARD_THEMES.Mega;

  return CARD_THEMES.Unknown;
}

/**
 * Resolve theme metadata preferring issuer, falling back to cardType string.
 */
export function getCardThemeByIssuerOrType(issuer?: CardIssuer, cardType?: string): CardThemeMetadata {
  if (issuer && issuer !== 'Unknown' && CARD_THEMES[issuer]) {
    return CARD_THEMES[issuer];
  }
  return getCardThemeByType(cardType);
}
