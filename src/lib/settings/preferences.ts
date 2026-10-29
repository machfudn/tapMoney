export type ThemeMode = 'system' | 'light' | 'dark';

export interface UserSettings {
  enableSound: boolean;
  enableHaptics: boolean;
  maskCardNumber: boolean;
  hideBalance: boolean;
  autoSaveHistory: boolean;
  theme: ThemeMode;
}

const STORAGE_KEY = 'tapmoney_user_settings';

const DEFAULT_SETTINGS: UserSettings = {
  enableSound: true,
  enableHaptics: true,
  maskCardNumber: true,
  hideBalance: false,
  autoSaveHistory: true,
  theme: 'system',
};

export function loadSettings(): UserSettings {
  if (typeof window === 'undefined' || !window.localStorage) {
    return { ...DEFAULT_SETTINGS };
  }

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULT_SETTINGS };
    const parsed = JSON.parse(raw);
    const validThemes: ThemeMode[] = ['system', 'light', 'dark'];
    return {
      enableSound: typeof parsed.enableSound === 'boolean' ? parsed.enableSound : DEFAULT_SETTINGS.enableSound,
      enableHaptics: typeof parsed.enableHaptics === 'boolean' ? parsed.enableHaptics : DEFAULT_SETTINGS.enableHaptics,
      maskCardNumber: typeof parsed.maskCardNumber === 'boolean' ? parsed.maskCardNumber : DEFAULT_SETTINGS.maskCardNumber,
      hideBalance: typeof parsed.hideBalance === 'boolean' ? parsed.hideBalance : DEFAULT_SETTINGS.hideBalance,
      autoSaveHistory: typeof parsed.autoSaveHistory === 'boolean' ? parsed.autoSaveHistory : DEFAULT_SETTINGS.autoSaveHistory,
      theme: validThemes.includes(parsed.theme) ? parsed.theme : DEFAULT_SETTINGS.theme,
    };
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

export function saveSettings(partial: Partial<UserSettings>): UserSettings {
  const current = loadSettings();
  const updated: UserSettings = { ...current, ...partial };

  if (typeof window !== 'undefined' && window.localStorage) {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
    } catch {
      // Fallback silently if storage quota exceeded
    }
  }

  return updated;
}

export function applyTheme(theme: ThemeMode): void {
  if (typeof document === 'undefined') return;

  const root = document.documentElement;
  root.classList.remove('theme-light', 'theme-dark');

  if (theme === 'system') {
    const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    root.classList.add(prefersDark ? 'theme-dark' : 'theme-light');
    root.setAttribute('data-theme', prefersDark ? 'dark' : 'light');
  } else {
    root.classList.add(`theme-${theme}`);
    root.setAttribute('data-theme', theme);
  }
}
