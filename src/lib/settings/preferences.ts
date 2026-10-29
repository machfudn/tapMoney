export interface UserSettings {
  enableSound: boolean;
  enableHaptics: boolean;
  maskCardNumber: boolean;
  autoSaveHistory: boolean;
}

const STORAGE_KEY = 'tapmoney_user_settings';

const DEFAULT_SETTINGS: UserSettings = {
  enableSound: true,
  enableHaptics: true,
  maskCardNumber: true,
  autoSaveHistory: true,
};

export function loadSettings(): UserSettings {
  if (typeof window === 'undefined' || !window.localStorage) {
    return { ...DEFAULT_SETTINGS };
  }

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULT_SETTINGS };
    const parsed = JSON.parse(raw);
    return {
      enableSound: typeof parsed.enableSound === 'boolean' ? parsed.enableSound : DEFAULT_SETTINGS.enableSound,
      enableHaptics: typeof parsed.enableHaptics === 'boolean' ? parsed.enableHaptics : DEFAULT_SETTINGS.enableHaptics,
      maskCardNumber: typeof parsed.maskCardNumber === 'boolean' ? parsed.maskCardNumber : DEFAULT_SETTINGS.maskCardNumber,
      autoSaveHistory: typeof parsed.autoSaveHistory === 'boolean' ? parsed.autoSaveHistory : DEFAULT_SETTINGS.autoSaveHistory,
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
