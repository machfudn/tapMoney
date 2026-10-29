<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { Capacitor, type PluginListenerHandle } from '@capacitor/core';
  import { CapacitorNfc, type NfcEvent, type NfcStateChangeEvent } from '@capgo/capacitor-nfc';
  import {
    checkNfcStatus,
    openNfcSettings,
    formatRupiah,
    readCardViaApdu,
    identifyCardFromTag
  } from './lib/nfc';
  import { syncTapHistory, fetchCardDefinitions } from './lib/api';
  import {
    saveTapRecord,
    getStoredHistory,
    clearStoredHistory
  } from './lib/storage/db';
  import {
    loadSettings,
    saveSettings,
    applyTheme,
    type UserSettings,
    type ThemeMode
  } from './lib/settings/preferences';
  import {
    playTapBeep,
    triggerHaptics
  } from './lib/settings/feedback';
  import {
    shareOrCopyReceipt,
    printCurrentReceipt
  } from './lib/receipt/receipt';
  import type { CardBalanceResult, BackendCardDefinition, CardIssuer } from './lib/types';

  let nfcSupported = $state(false);
  let nfcEnabled = $state(false);
  let isScanning = $state(false);
  let currentCard = $state<CardBalanceResult | null>(null);
  let scanHistory = $state<CardBalanceResult[]>([]);
  let cardDefinitions = $state<BackendCardDefinition[]>([]);
  let statusMessage = $state('Tempelkan kartu di belakang ponsel');

  let settings = $state<UserSettings>(loadSettings());
  let showSettingsModal = $state(false);
  let toastMessage = $state<string | null>(null);
  let toastTimer: ReturnType<typeof setTimeout> | null = null;

  let stateChangeListener: PluginListenerHandle | null = null;
  let tagDiscoveredListener: PluginListenerHandle | null = null;
  let nfcEventListener: PluginListenerHandle | null = null;
  let pollInterval: ReturnType<typeof setInterval> | null = null;
  let systemThemeQuery: MediaQueryList | null = null;

  function showToast(msg: string) {
    toastMessage = msg;
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      toastMessage = null;
    }, 2400);
  }

  function formatDisplayCardNumber(num: string, masked: boolean): string {
    if (!num || num.includes('----')) return '---- ---- ---- ----';
    if (!masked) return num;
    const parts = num.split(' ');
    if (parts.length === 4) {
      return `${parts[0]} **** **** ${parts[3]}`;
    }
    return num;
  }

  async function handleCopyCardNumber(num: string) {
    if (!num || num.includes('----')) return;
    try {
      await navigator.clipboard.writeText(num.replace(/\s+/g, ''));
      if (settings.enableSound) playTapBeep();
      if (settings.enableHaptics) triggerHaptics();
      showToast('Nomor kartu disalin');
    } catch {
      showToast('Gagal menyalin nomor');
    }
  }

  async function handleShareReceipt() {
    if (!currentCard) return;
    const res = await shareOrCopyReceipt(currentCard, 'GT_CILILITAN_1');
    if (res.method === 'clipboard') {
      showToast('Struk disalin ke clipboard');
    } else if (res.method === 'share' && res.success) {
      showToast('Struk berhasil dibagikan');
    }
  }

  function handlePrintReceipt() {
    if (!currentCard) return;
    printCurrentReceipt();
  }

  function updateSetting<K extends keyof UserSettings>(key: K, val: UserSettings[K]) {
    settings[key] = val;
    saveSettings({ [key]: val });
    if (key === 'theme') {
      applyTheme(val as ThemeMode);
    }
  }

  function handleSystemThemeChange() {
    if (settings.theme === 'system') {
      applyTheme('system');
    }
  }

  async function processDiscoveredCard(event: NfcEvent) {
    try {
      let card: CardBalanceResult;
      try {
        card = await readCardViaApdu(event);
      } catch {
        card = identifyCardFromTag(event);
      }

      currentCard = card;

      if (settings.enableSound) playTapBeep();
      if (settings.enableHaptics) triggerHaptics();

      if (settings.autoSaveHistory) {
        await saveTapRecord(card, 'GT_CILILITAN_1');
        scanHistory = [card, ...scanHistory.slice(0, 19)];
        syncTapHistory(card, 'GT_CILILITAN_1');
      }

      statusMessage = `Kartu terbaca: ${card.cardType}`;
    } catch {
      statusMessage = 'Gagal memproses data kartu';
    }
  }

  async function startScanner() {
    if (!Capacitor.isNativePlatform()) {
      isScanning = true;
      return;
    }

    try {
      if (tagDiscoveredListener) {
        await tagDiscoveredListener.remove();
        tagDiscoveredListener = null;
      }
      if (nfcEventListener) {
        await nfcEventListener.remove();
        nfcEventListener = null;
      }

      tagDiscoveredListener = await CapacitorNfc.addListener('tagDiscovered', async (event: NfcEvent) => {
        await processDiscoveredCard(event);
      });

      nfcEventListener = await CapacitorNfc.addListener('nfcEvent', async (event: NfcEvent) => {
        await processDiscoveredCard(event);
      });

      await CapacitorNfc.startScanning();
      isScanning = true;
      statusMessage = 'Tempelkan kartu di belakang ponsel';
    } catch {
      isScanning = false;
      statusMessage = 'Gagal memulai pemindai NFC';
    }
  }

  async function stopScanner() {
    if (!Capacitor.isNativePlatform()) {
      isScanning = false;
      return;
    }

    try {
      if (tagDiscoveredListener) {
        await tagDiscoveredListener.remove();
        tagDiscoveredListener = null;
      }
      if (nfcEventListener) {
        await nfcEventListener.remove();
        nfcEventListener = null;
      }
      await CapacitorNfc.stopScanning();
    } catch {
      // ignore
    } finally {
      isScanning = false;
    }
  }

  async function refreshNfcState() {
    const status = await checkNfcStatus();
    const prevEnabled = nfcEnabled;
    nfcSupported = status.supported;
    nfcEnabled = status.enabled;

    if (!nfcSupported && Capacitor.isNativePlatform()) {
      statusMessage = 'Perangkat tidak memiliki hardware NFC';
      await stopScanner();
      return;
    }

    if (!nfcEnabled && Capacitor.isNativePlatform()) {
      statusMessage = 'NFC tidak aktif. Ketuk tombol untuk menyalakan.';
      await stopScanner();
    } else if (nfcEnabled && (!prevEnabled || !isScanning)) {
      await startScanner();
    }
  }

  async function initNfc() {
    if (!Capacitor.isNativePlatform()) {
      nfcSupported = true;
      nfcEnabled = true;
      isScanning = true;
      statusMessage = 'Mode Simulator Browser: Gunakan tombol di bawah';
      return;
    }

    try {
      stateChangeListener = await CapacitorNfc.addListener('nfcStateChange', async (e: NfcStateChangeEvent) => {
        nfcEnabled = e.enabled || e.status === 'NFC_OK';
        if (nfcEnabled) {
          statusMessage = 'NFC aktif. Memulai pemindaian...';
          await startScanner();
        } else {
          statusMessage = 'NFC dimatikan di pengaturan ponsel';
          await stopScanner();
        }
      });
    } catch {
      // ignore
    }

    await refreshNfcState();

    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        refreshNfcState();
      }
    });

    pollInterval = setInterval(() => {
      refreshNfcState();
    }, 2000);
  }

  async function simulateCardTap(type: 'Mandiri' | 'BCA' | 'BRI' | 'BNI' | 'KCI' | 'Mega') {
    const mockMap = {
      Mandiri: { type: 'Mandiri e-Money', num: '6032 1290 8820 1290', bal: 87500, issuer: 'Mandiri' as const },
      BCA: { type: 'BCA Flazz', num: '1809 3321 4410 8821', bal: 142000, issuer: 'BCA' as const },
      BRI: { type: 'BRI Brizzi', num: '5018 7721 9021 4410', bal: 26000, issuer: 'BRI' as const },
      BNI: { type: 'BNI TapCash', num: '7546 1102 5590 9012', bal: 53500, issuer: 'BNI' as const },
      KCI: { type: 'KMT Commuter Line', num: '1002 8840 7712 3341', bal: 18000, issuer: 'KCI' as const },
      Mega: { type: 'Bank Mega MegaCash', num: '6019 8800 4421 9912', bal: 65000, issuer: 'Mega' as const }
    };

    const target = mockMap[type];
    const card: CardBalanceResult = {
      cardType: target.type,
      issuer: target.issuer,
      cardNumber: target.num,
      balance: target.bal,
      lastAmount: 3500,
      timestamp: new Date().toISOString()
    };

    currentCard = card;

    if (settings.enableSound) playTapBeep();
    if (settings.enableHaptics) triggerHaptics();

    if (settings.autoSaveHistory) {
      await saveTapRecord(card, 'GT_CILILITAN_1');
      scanHistory = [card, ...scanHistory.slice(0, 19)];
      syncTapHistory(card, 'GT_CILILITAN_1');
    }

    statusMessage = `Simulasi sukses: ${card.cardType}`;
  }

  async function handleClearHistory() {
    await clearStoredHistory();
    scanHistory = [];
    showToast('Riwayat berhasil dibersihkan');
  }

  onMount(async () => {
    applyTheme(settings.theme);

    if (typeof window !== 'undefined' && window.matchMedia) {
      systemThemeQuery = window.matchMedia('(prefers-color-scheme: dark)');
      systemThemeQuery.addEventListener('change', handleSystemThemeChange);
    }

    try {
      const stored = await getStoredHistory(20);
      scanHistory = stored.map(s => ({
        cardType: s.cardType,
        issuer: s.issuer as CardIssuer,
        cardNumber: s.cardNumber,
        balance: s.balance,
        lastAmount: s.lastAmount,
        timestamp: s.timestamp
      }));
    } catch {
      scanHistory = [];
    }

    await initNfc();
    cardDefinitions = await fetchCardDefinitions();
  });

  onDestroy(() => {
    if (pollInterval) clearInterval(pollInterval);
    if (stateChangeListener) stateChangeListener.remove();
    if (tagDiscoveredListener) tagDiscoveredListener.remove();
    if (nfcEventListener) nfcEventListener.remove();
    if (systemThemeQuery) {
      systemThemeQuery.removeEventListener('change', handleSystemThemeChange);
    }
  });
</script>

<main class="shell">
  <header class="app-header">
    <div class="header-inner">
      <div class="brand">
        <div class="brand-badge">
          <svg viewBox="0 0 24 24" class="brand-icon" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
            <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
          </svg>
        </div>
        <div>
          <h1 class="brand-name">TapMoney Info</h1>
          <p class="brand-caption">Pemeriksa Saldo Uang Elektronik</p>
        </div>
      </div>

      <div class="header-actions">
        <span class="status-pill {nfcEnabled ? 'status-pill--active' : 'status-pill--inactive'}">
          <span class="status-dot"></span>
          <span>{nfcEnabled ? 'NFC Aktif' : 'NFC Mati'}</span>
        </span>

        <button
          class="btn-icon"
          onclick={() => showSettingsModal = true}
          title="Pengaturan"
          aria-label="Pengaturan"
        >
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="3" />
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
          </svg>
        </button>
      </div>
    </div>
  </header>

  {#if !nfcEnabled && Capacitor.isNativePlatform()}
    <aside class="nfc-alert-banner">
      <div class="nfc-alert-body">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" class="nfc-alert-icon">
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="8" x2="12" y2="12" />
          <line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
        <div>
          <div class="nfc-alert-title">Sensor NFC Nonaktif</div>
          <div class="nfc-alert-desc">Aktifkan NFC di pengaturan ponsel untuk mulai memindai kartu fisik.</div>
        </div>
      </div>
      <button class="btn btn-nfc-activate" onclick={openNfcSettings}>
        Aktifkan NFC
      </button>
    </aside>
  {/if}

  <section class="card-hero">
    <div
      class="card-surface {currentCard ? currentCard.issuer.toLowerCase() : 'card-surface--empty'}"
      onclick={() => currentCard && handleCopyCardNumber(currentCard.cardNumber)}
      role="button"
      tabindex="0"
      onkeydown={(e) => e.key === 'Enter' && currentCard && handleCopyCardNumber(currentCard.cardNumber)}
    >
      <div class="card-header-row">
        <div class="card-brand-label">
          {currentCard ? currentCard.cardType : 'UANG ELEKTRONIK'}
        </div>
        <div class="nfc-contactless-mark">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M8.5 16.5a5 5 0 0 1 0-7" />
            <path d="M12 19a8.5 8.5 0 0 1 0-14" />
            <path d="M15.5 21.5a12 12 0 0 1 0-19" />
          </svg>
        </div>
      </div>

      <div class="smart-chip">
        <div class="smart-chip-grid"></div>
      </div>

      <div class="card-balance-block">
        <span class="card-balance-tag">Saldo Tersedia</span>
        <div class="card-balance-amount">
          {currentCard ? formatRupiah(currentCard.balance) : 'Rp 0'}
        </div>
      </div>

      <div class="card-footer-row">
        <div class="card-pan-wrapper">
          <span class="card-pan-label">Nomor Kartu</span>
          <span class="card-pan">
            {currentCard ? formatDisplayCardNumber(currentCard.cardNumber, settings.maskCardNumber) : '•••• •••• •••• ••••'}
          </span>
        </div>
        {#if currentCard}
          <div class="card-time-wrapper">
            <span class="card-time-label">Waktu Tap</span>
            <span class="card-time">
              {new Date(currentCard.timestamp).toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' })}
            </span>
          </div>
        {/if}
      </div>
    </div>

    {#if currentCard}
      <div class="action-grid">
        <button class="action-btn" onclick={() => handleCopyCardNumber(currentCard!.cardNumber)}>
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
          </svg>
          Salin No
        </button>
        <button class="action-btn" onclick={handleShareReceipt}>
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="18" cy="5" r="3" />
            <circle cx="6" cy="12" r="3" />
            <circle cx="18" cy="19" r="3" />
            <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
            <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
          </svg>
          Bagikan
        </button>
        <button class="action-btn" onclick={handlePrintReceipt}>
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="6 9 6 2 18 2 18 9" />
            <path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2" />
            <rect x="6" y="14" width="12" height="8" />
          </svg>
          Cetak
        </button>
      </div>
    {/if}

    <div class="radar-box {isScanning ? 'radar-box--active' : ''}">
      <div class="radar-pulse"></div>
      <p class="radar-label">{statusMessage}</p>
    </div>
  </section>

  {#if !Capacitor.isNativePlatform()}
    <section class="simulator-card">
      <div class="section-heading">
        <h2 class="section-title">Simulator Pengujian Browser</h2>
        <span class="sim-badge">Dev Mode</span>
      </div>
      <p class="sim-desc">Pilih kartu untuk mensimulasikan tap tanpa perangkat fisik.</p>
      <div class="sim-grid">
        <button class="btn btn-mock" onclick={() => simulateCardTap('Mandiri')}>e-Money</button>
        <button class="btn btn-mock" onclick={() => simulateCardTap('BCA')}>Flazz</button>
        <button class="btn btn-mock" onclick={() => simulateCardTap('BRI')}>Brizzi</button>
        <button class="btn btn-mock" onclick={() => simulateCardTap('BNI')}>TapCash</button>
        <button class="btn btn-mock" onclick={() => simulateCardTap('KCI')}>KMT</button>
        <button class="btn btn-mock" onclick={() => simulateCardTap('Mega')}>MegaCash</button>
      </div>
    </section>
  {/if}

  <section class="history-block">
    <div class="history-header">
      <div class="history-title-group">
        <h2 class="section-title">Riwayat Pemindaian</h2>
        <span class="count-badge">{scanHistory.length}</span>
      </div>
      {#if scanHistory.length > 0}
        <button class="btn-text-danger" onclick={handleClearHistory}>
          Bersihkan
        </button>
      {/if}
    </div>

    {#if scanHistory.length === 0}
      <div class="empty-tray">
        <svg viewBox="0 0 24 24" width="32" height="32" class="empty-icon" fill="none" stroke="currentColor" stroke-width="1.6">
          <rect x="2" y="5" width="20" height="14" rx="2" />
          <line x1="2" y1="10" x2="22" y2="10" />
        </svg>
        <div class="empty-title">Belum ada riwayat tap</div>
        <div class="empty-subtitle">Tempelkan kartu e-money pada punggung ponsel untuk membaca saldo.</div>
      </div>
    {:else}
      <div class="history-stack">
        {#each scanHistory as item (item.timestamp)}
          <div
            class="history-row"
            onclick={() => handleCopyCardNumber(item.cardNumber)}
            role="button"
            tabindex="0"
            onkeydown={(e) => e.key === 'Enter' && handleCopyCardNumber(item.cardNumber)}
          >
            <div class="history-row-main">
              <span class="history-card-type">{item.cardType}</span>
              <span class="history-card-pan">
                {formatDisplayCardNumber(item.cardNumber, settings.maskCardNumber)}
              </span>
              <span class="history-card-time">
                {new Date(item.timestamp).toLocaleString('id-ID', {
                  day: 'numeric',
                  month: 'short',
                  hour: '2-digit',
                  minute: '2-digit'
                })}
              </span>
            </div>
            <div class="history-row-side">
              <span class="history-amount">{formatRupiah(item.balance)}</span>
            </div>
          </div>
        {/each}
      </div>
    {/if}
  </section>

  {#if showSettingsModal}
    <div class="modal-backdrop" onclick={() => showSettingsModal = false} role="presentation">
      <div
        class="modal-sheet"
        onclick={(e) => e.stopPropagation()}
        onkeydown={(e) => e.key === 'Escape' && (showSettingsModal = false)}
        role="dialog"
        aria-modal="true"
        aria-labelledby="settings-title"
        tabindex="-1"
      >
        <div class="modal-sheet-header">
          <h2 id="settings-title" class="modal-sheet-title">Pengaturan</h2>
          <button class="btn-icon" onclick={() => showSettingsModal = false} aria-label="Tutup">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div class="settings-items">
          <!-- Theme Selector -->
          <div class="setting-group">
            <div class="setting-title">Tema Tampilan</div>
            <div class="setting-description">Pilih tema antarmuka aplikasi</div>
            <div class="theme-segmented-control">
              <button
                type="button"
                class="theme-segment {settings.theme === 'system' ? 'theme-segment--active' : ''}"
                onclick={() => updateSetting('theme', 'system')}
              >
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="2" y="3" width="20" height="14" rx="2" />
                  <line x1="8" y1="21" x2="16" y2="21" />
                  <line x1="12" y1="17" x2="12" y2="21" />
                </svg>
                <span>Sistem</span>
              </button>
              <button
                type="button"
                class="theme-segment {settings.theme === 'light' ? 'theme-segment--active' : ''}"
                onclick={() => updateSetting('theme', 'light')}
              >
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="5" />
                  <line x1="12" y1="1" x2="12" y2="3" />
                  <line x1="12" y1="21" x2="12" y2="23" />
                  <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
                  <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
                  <line x1="1" y1="12" x2="3" y2="12" />
                  <line x1="21" y1="12" x2="23" y2="12" />
                  <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
                  <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
                </svg>
                <span>Terang</span>
              </button>
              <button
                type="button"
                class="theme-segment {settings.theme === 'dark' ? 'theme-segment--active' : ''}"
                onclick={() => updateSetting('theme', 'dark')}
              >
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
                </svg>
                <span>Gelap</span>
              </button>
            </div>
          </div>

          <label class="setting-row">
            <div>
              <div class="setting-title">Umpan Balik Suara</div>
              <div class="setting-description">Bunyikan nada saat kartu berhasil dipindai</div>
            </div>
            <input
              type="checkbox"
              class="ios-switch"
              checked={settings.enableSound}
              onchange={(e) => updateSetting('enableSound', (e.target as HTMLInputElement).checked)}
            />
          </label>

          <label class="setting-row">
            <div>
              <div class="setting-title">Getaran (Haptics)</div>
              <div class="setting-description">Getarkan perangkat saat mendeteksi kartu</div>
            </div>
            <input
              type="checkbox"
              class="ios-switch"
              checked={settings.enableHaptics}
              onchange={(e) => updateSetting('enableHaptics', (e.target as HTMLInputElement).checked)}
            />
          </label>

          <label class="setting-row">
            <div>
              <div class="setting-title">Sensor Masking PAN</div>
              <div class="setting-description">Samarkan 8 digit tengah nomor kartu</div>
            </div>
            <input
              type="checkbox"
              class="ios-switch"
              checked={settings.maskCardNumber}
              onchange={(e) => updateSetting('maskCardNumber', (e.target as HTMLInputElement).checked)}
            />
          </label>

          <label class="setting-row">
            <div>
              <div class="setting-title">Sinkronisasi Riwayat</div>
              <div class="setting-description">Simpan setiap sesi tap ke storage lokal & cloud</div>
            </div>
            <input
              type="checkbox"
              class="ios-switch"
              checked={settings.autoSaveHistory}
              onchange={(e) => updateSetting('autoSaveHistory', (e.target as HTMLInputElement).checked)}
            />
          </label>
        </div>

        <button class="btn btn-primary" onclick={() => showSettingsModal = false}>
          Tutup
        </button>
      </div>
    </div>
  {/if}

  {#if toastMessage}
    <div class="toast-bubble" role="status">
      {toastMessage}
    </div>
  {/if}
</main>

<style>
  :global(*), :global(*::before), :global(*::after) {
    box-sizing: border-box;
  }

  /* Design Tokens System: Light vs Dark Theme Variables */
  :global(:root) {
    --bg-page: #090d16;
    --text-primary: #f8fafc;
    --text-secondary: #94a3b8;
    --text-muted: #64748b;
    --surface-card: #131b2e;
    --surface-card-hover: #1e293b;
    --surface-elevated: #1e293b;
    --surface-sheet: #0f172a;
    --border-subtle: rgba(255, 255, 255, 0.08);
    --border-hover: rgba(255, 255, 255, 0.16);
    --primary-color: #0284c7;
    --primary-hover: #0369a1;
    --brand-icon-color: #38bdf8;
    --accent-glow: rgba(56, 189, 248, 0.15);
    --modal-backdrop-bg: rgba(0, 0, 0, 0.75);
    --toast-bg: #1e293b;
  }

  :global(.theme-light) {
    --bg-page: #f8fafc;
    --text-primary: #0f172a;
    --text-secondary: #475569;
    --text-muted: #94a3b8;
    --surface-card: #ffffff;
    --surface-card-hover: #f1f5f9;
    --surface-elevated: #f8fafc;
    --surface-sheet: #ffffff;
    --border-subtle: #e2e8f0;
    --border-hover: #cbd5e1;
    --primary-color: #0284c7;
    --primary-hover: #0369a1;
    --brand-icon-color: #0284c7;
    --accent-glow: rgba(2, 132, 199, 0.12);
    --modal-backdrop-bg: rgba(15, 23, 42, 0.45);
    --toast-bg: #0f172a;
  }

  :global(.theme-dark) {
    --bg-page: #090d16;
    --text-primary: #f8fafc;
    --text-secondary: #94a3b8;
    --text-muted: #64748b;
    --surface-card: #131b2e;
    --surface-card-hover: #1e293b;
    --surface-elevated: #1e293b;
    --surface-sheet: #0f172a;
    --border-subtle: rgba(255, 255, 255, 0.08);
    --border-hover: rgba(255, 255, 255, 0.16);
    --primary-color: #0284c7;
    --primary-hover: #0369a1;
    --brand-icon-color: #38bdf8;
    --accent-glow: rgba(56, 189, 248, 0.15);
    --modal-backdrop-bg: rgba(0, 0, 0, 0.75);
    --toast-bg: #1e293b;
  }

  :global(body) {
    margin: 0;
    padding: 0;
    background-color: var(--bg-page);
    color: var(--text-primary);
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    -webkit-font-smoothing: antialiased;
    -webkit-tap-highlight-color: transparent;
    transition: background-color 0.25s ease, color 0.25s ease;
  }

  .shell {
    max-width: 480px;
    margin: 0 auto;
    padding: 24px 18px 48px;
    min-height: 100vh;
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  /* Header */
  .app-header {
    margin-bottom: 4px;
  }

  .header-inner {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .brand {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .brand-badge {
    width: 42px;
    height: 42px;
    border-radius: 12px;
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--brand-icon-color);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  }

  .brand-icon {
    width: 22px;
    height: 22px;
  }

  .brand-name {
    margin: 0;
    font-size: 19px;
    font-weight: 700;
    letter-spacing: -0.02em;
    color: var(--text-primary);
  }

  .brand-caption {
    margin: 2px 0 0 0;
    font-size: 12px;
    color: var(--text-secondary);
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .status-pill {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 4px 10px;
    border-radius: 9999px;
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.02em;
  }

  .status-pill--active {
    background: rgba(16, 185, 129, 0.12);
    color: #10b981;
    border: 1px solid rgba(16, 185, 129, 0.3);
  }

  .status-pill--inactive {
    background: rgba(239, 68, 68, 0.12);
    color: #ef4444;
    border: 1px solid rgba(239, 68, 68, 0.3);
  }

  .status-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background-color: currentColor;
  }

  .btn-icon {
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    color: var(--text-secondary);
    width: 36px;
    height: 36px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: all 0.2s ease;
  }

  .btn-icon:hover {
    color: var(--text-primary);
    background: var(--surface-card-hover);
    border-color: var(--border-hover);
  }

  /* NFC Disabled Alert Banner */
  .nfc-alert-banner {
    background: linear-gradient(135deg, rgba(239, 68, 68, 0.15), rgba(185, 28, 28, 0.08));
    border: 1px solid rgba(239, 68, 68, 0.3);
    border-radius: 14px;
    padding: 14px 16px;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .nfc-alert-body {
    display: flex;
    align-items: flex-start;
    gap: 12px;
  }

  .nfc-alert-icon {
    color: #ef4444;
    flex-shrink: 0;
    margin-top: 1px;
  }

  .nfc-alert-title {
    font-size: 14px;
    font-weight: 600;
    color: #f87171;
  }

  .nfc-alert-desc {
    font-size: 12px;
    color: var(--text-secondary);
    margin-top: 2px;
    line-height: 1.4;
  }

  .btn-nfc-activate {
    width: 100%;
    background: #dc2626;
    color: #ffffff;
    font-size: 13px;
    font-weight: 600;
    padding: 9px;
    border: none;
    border-radius: 8px;
    cursor: pointer;
    transition: background 0.15s ease;
  }

  .btn-nfc-activate:hover {
    background: #ef4444;
  }

  /* Card Hero */
  .card-hero {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .card-surface {
    height: 200px;
    border-radius: 20px;
    padding: 22px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    position: relative;
    overflow: hidden;
    cursor: pointer;
    box-shadow: 0 16px 36px rgba(0, 0, 0, 0.35);
    border: 1px solid rgba(255, 255, 255, 0.12);
    transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.25s ease;
  }

  .card-surface:active {
    transform: scale(0.985);
  }

  /* Card Skins */
  .card-surface--empty {
    background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
  }

  .card-surface.mandiri {
    background: linear-gradient(135deg, #0284c7 0%, #0369a1 40%, #0c4a6e 100%);
  }

  .card-surface.bca {
    background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 45%, #172554 100%);
  }

  .card-surface.bri {
    background: linear-gradient(135deg, #0284c7 0%, #1e40af 50%, #0f172a 100%);
  }

  .card-surface.bni {
    background: linear-gradient(135deg, #ea580c 0%, #c2410c 50%, #7c2d12 100%);
  }

  .card-surface.kci {
    background: linear-gradient(135deg, #dc2626 0%, #b91c1c 50%, #7f1d1d 100%);
  }

  .card-surface.mega {
    background: linear-gradient(135deg, #d97706 0%, #b45309 50%, #78350f 100%);
  }

  .card-header-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .card-brand-label {
    font-size: 13px;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: rgba(255, 255, 255, 0.95);
  }

  .nfc-contactless-mark {
    color: rgba(255, 255, 255, 0.85);
  }

  .smart-chip {
    width: 44px;
    height: 32px;
    border-radius: 6px;
    background: linear-gradient(135deg, #fcd34d 0%, #f59e0b 50%, #d97706 100%);
    box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.4), 0 2px 6px rgba(0, 0, 0, 0.25);
    position: relative;
    overflow: hidden;
  }

  .smart-chip-grid {
    position: absolute;
    inset: 4px;
    border: 1px solid rgba(120, 53, 15, 0.45);
    border-radius: 3px;
  }

  .card-balance-block {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .card-balance-tag {
    font-size: 11px;
    font-weight: 500;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: rgba(255, 255, 255, 0.65);
  }

  .card-balance-amount {
    font-size: 30px;
    font-weight: 800;
    letter-spacing: -0.02em;
    color: #ffffff;
    font-variant-numeric: tabular-nums;
  }

  .card-footer-row {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
  }

  .card-pan-wrapper {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .card-pan-label, .card-time-label {
    font-size: 10px;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: rgba(255, 255, 255, 0.55);
  }

  .card-pan {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 13px;
    font-weight: 600;
    letter-spacing: 0.08em;
    color: rgba(255, 255, 255, 0.9);
  }

  .card-time {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    color: rgba(255, 255, 255, 0.85);
  }

  /* Action Buttons Grid */
  .action-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 10px;
  }

  .action-btn {
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    color: var(--text-primary);
    padding: 10px 12px;
    border-radius: 12px;
    font-size: 12px;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    cursor: pointer;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
    transition: all 0.15s ease;
  }

  .action-btn:hover {
    background: var(--surface-card-hover);
    border-color: var(--border-hover);
  }

  /* Radar / Sensor Box */
  .radar-box {
    display: flex;
    align-items: center;
    gap: 12px;
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    padding: 13px 16px;
    border-radius: 14px;
  }

  .radar-pulse {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--brand-icon-color);
    flex-shrink: 0;
  }

  .radar-box--active .radar-pulse {
    animation: radar-ping 1.6s cubic-bezier(0, 0, 0.2, 1) infinite;
  }

  @keyframes radar-ping {
    0% { transform: scale(0.95); opacity: 0.8; }
    50% { transform: scale(1.4); opacity: 1; box-shadow: 0 0 10px var(--brand-icon-color); }
    100% { transform: scale(0.95); opacity: 0.8; }
  }

  .radar-label {
    margin: 0;
    font-size: 13px;
    color: var(--text-secondary);
    line-height: 1.4;
  }

  /* Section Styling */
  .section-heading {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 6px;
  }

  .section-title {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    letter-spacing: -0.01em;
    color: var(--text-primary);
  }

  /* Simulator Card */
  .simulator-card {
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    border-radius: 16px;
    padding: 16px;
  }

  .sim-badge {
    background: var(--primary-color);
    color: #ffffff;
    font-size: 10px;
    font-weight: 700;
    padding: 2px 7px;
    border-radius: 9999px;
    text-transform: uppercase;
  }

  .sim-desc {
    margin: 0 0 12px 0;
    font-size: 12px;
    color: var(--text-secondary);
  }

  .sim-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
  }

  .btn-mock {
    background: var(--surface-elevated);
    border: 1px solid var(--border-subtle);
    color: var(--text-primary);
    font-size: 12px;
    font-weight: 600;
    padding: 9px 8px;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.15s ease;
  }

  .btn-mock:hover {
    background: var(--surface-card-hover);
    border-color: var(--brand-icon-color);
  }

  /* History Block */
  .history-block {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .history-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .history-title-group {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .count-badge {
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    color: var(--text-secondary);
    font-size: 11px;
    font-weight: 600;
    padding: 1px 7px;
    border-radius: 9999px;
  }

  .btn-text-danger {
    background: transparent;
    border: none;
    color: #ef4444;
    font-size: 12px;
    font-weight: 500;
    cursor: pointer;
    padding: 4px;
    transition: opacity 0.15s ease;
  }

  .btn-text-danger:hover {
    opacity: 0.8;
  }

  .empty-tray {
    background: var(--surface-card);
    border: 1px dashed var(--border-subtle);
    border-radius: 16px;
    padding: 36px 20px;
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
  }

  .empty-icon {
    color: var(--text-muted);
    margin-bottom: 4px;
  }

  .empty-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-primary);
  }

  .empty-subtitle {
    font-size: 12px;
    color: var(--text-muted);
    max-width: 260px;
    line-height: 1.4;
  }

  .history-stack {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .history-row {
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    border-radius: 12px;
    padding: 13px 16px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    cursor: pointer;
    transition: background 0.15s ease, border-color 0.15s ease;
  }

  .history-row:hover {
    background: var(--surface-card-hover);
    border-color: var(--border-hover);
  }

  .history-row-main {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .history-card-type {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
  }

  .history-card-pan {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 11px;
    color: var(--text-secondary);
  }

  .history-card-time {
    font-size: 10px;
    color: var(--text-muted);
  }

  .history-amount {
    font-size: 15px;
    font-weight: 700;
    color: #10b981;
    font-variant-numeric: tabular-nums;
  }

  /* Settings Modal */
  .modal-backdrop {
    position: fixed;
    inset: 0;
    background: var(--modal-backdrop-bg);
    backdrop-filter: blur(4px);
    display: flex;
    align-items: flex-end;
    justify-content: center;
    z-index: 1000;
  }

  .modal-sheet {
    background: var(--surface-sheet);
    border-top: 1px solid var(--border-subtle);
    border-radius: 20px 20px 0 0;
    width: 100%;
    max-width: 480px;
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 20px;
    box-shadow: 0 -10px 30px rgba(0, 0, 0, 0.2);
  }

  .modal-sheet-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .modal-sheet-title {
    margin: 0;
    font-size: 17px;
    font-weight: 700;
    color: var(--text-primary);
  }

  .settings-items {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  /* Theme Segmented Control */
  .setting-group {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--border-subtle);
  }

  .theme-segmented-control {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    background: var(--surface-card);
    border: 1px solid var(--border-subtle);
    padding: 4px;
    border-radius: 10px;
    gap: 4px;
  }

  .theme-segment {
    background: transparent;
    border: none;
    color: var(--text-secondary);
    padding: 8px 10px;
    border-radius: 8px;
    font-size: 12px;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    cursor: pointer;
    transition: all 0.15s ease;
  }

  .theme-segment--active {
    background: var(--primary-color);
    color: #ffffff;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
  }

  .setting-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    cursor: pointer;
  }

  .setting-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-primary);
  }

  .setting-description {
    font-size: 12px;
    color: var(--text-secondary);
    margin-top: 2px;
  }

  .ios-switch {
    width: 22px;
    height: 22px;
    accent-color: var(--primary-color);
    cursor: pointer;
  }

  .btn-primary {
    width: 100%;
    background: var(--primary-color);
    color: #ffffff;
    border: none;
    font-size: 14px;
    font-weight: 600;
    padding: 12px;
    border-radius: 10px;
    cursor: pointer;
    transition: background 0.15s ease;
  }

  .btn-primary:hover {
    background: var(--primary-hover);
  }

  /* Toast Notification */
  .toast-bubble {
    position: fixed;
    bottom: 24px;
    left: 50%;
    transform: translateX(-50%);
    background: var(--toast-bg);
    border: 1px solid var(--border-subtle);
    color: var(--text-primary);
    font-size: 13px;
    font-weight: 500;
    padding: 10px 18px;
    border-radius: 9999px;
    box-shadow: 0 10px 25px rgba(0, 0, 0, 0.35);
    z-index: 2000;
  }
</style>
