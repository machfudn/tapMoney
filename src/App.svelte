<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { Capacitor, type PluginListenerHandle } from '@capacitor/core';
  import { CapacitorNfc, type NfcEvent, type NfcStateChangeEvent } from '@capgo/capacitor-nfc';
  import { IsoDepNfc, type IsoDepEvent } from './lib/iso-dep';
  import {
    checkNfcStatus,
    openNfcSettings,
    formatRupiah,
    readCardViaApdu,
    identifyCardFromTag
  } from './lib/nfc';
  import {
    formatDisplayBalance,
    formatDisplayCardNumber,
    getRawCardNumber
  } from './lib/apdu/card-formatter';
  import { syncTapHistory, fetchCardDefinitions } from './lib/api';
  import {
    saveTapRecord,
    getStoredHistory,
    getStoredCards,
    deleteStoredCard,
    clearAllStoredData,
    type StoredCardRecord
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
  import { getCardTheme } from './lib/cards/card-assets';
  import CardVisual from './lib/cards/CardVisual.svelte';

  let nfcSupported = $state(false);
  let nfcEnabled = $state(false);
  let isScanning = $state(false);
  let currentCard = $state<CardBalanceResult | null>(null);
  let activeTheme = $derived(currentCard ? getCardTheme(currentCard.issuer) : getCardTheme('Unknown'));
  let savedCards = $state<StoredCardRecord[]>([]);
  let cardDefinitions = $state<BackendCardDefinition[]>([]);
  let statusMessage = $state('Tempelkan kartu di belakang ponsel');

  let activeHistoryTab = $state<'chip' | 'saved'>('chip');

  let settings = $state<UserSettings>(loadSettings());
  let showSettingsModal = $state(false);
  let toastMessage = $state<string | null>(null);
  let toastTimer: ReturnType<typeof setTimeout> | null = null;

  let stateChangeListener: PluginListenerHandle | null = null;
  let tagDiscoveredListener: PluginListenerHandle | null = null;
  let nfcEventListener: PluginListenerHandle | null = null;
  let isoDepListener: PluginListenerHandle | null = null;
  let processingScan = false;
  let lastScanKey = '';
  let lastScanAt = 0;
  let pollInterval: ReturnType<typeof setInterval> | null = null;
  let systemThemeQuery: MediaQueryList | null = null;

  function showToast(msg: string) {
    toastMessage = msg;
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      toastMessage = null;
    }, 2800);
  }

  function handleCopyCardNumber(pan: string) {
    const raw = getRawCardNumber(pan);
    if (!raw) return;
    navigator.clipboard?.writeText?.(raw);
    showToast(`Nomor ${raw} disalin ke clipboard`);
  }

  async function handleShareReceipt() {
    if (!currentCard) return;
    const res = await shareOrCopyReceipt(currentCard, 'GT_CILILITAN_1');
    if (res.success) {
      showToast(res.method === 'share' ? 'Membuka menu share...' : 'Struk disalin ke clipboard');
    } else {
      showToast('Gagal membagikan struk');
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

  function calculateBalanceDelta(newCard: CardBalanceResult): {
    delta: number;
    type: 'Top up' | 'Payment' | 'None';
  } {
    let prevCard: CardBalanceResult | null = null;
    if (currentCard && currentCard.cardNumber === newCard.cardNumber) {
      prevCard = currentCard;
    } else {
      const match = savedCards.find(c => c.cardNumber === newCard.cardNumber);
      if (match) {
        prevCard = {
          cardNumber: match.cardNumber,
          cardType: match.cardType,
          issuer: match.issuer as CardIssuer,
          balance: match.balance ?? match.lastBalance ?? 0,
          timestamp: match.timestamp
        };
      }
    }

    if (!prevCard) {
      return { delta: 0, type: 'None' };
    }

    const delta = newCard.balance - prevCard.balance;
    if (delta > 0) {
      return { delta, type: 'Top up' };
    } else if (delta < 0) {
      return { delta: Math.abs(delta), type: 'Payment' };
    }
    return { delta: 0, type: 'None' };
  }

  async function processDiscoveredCard(event: NfcEvent) {
    const tag = event.tag || (event as any);
    const scanKey = Array.isArray(tag?.id) ? tag.id.join(',') : JSON.stringify(tag?.id || event);
    const now = Date.now();
    if (processingScan || (scanKey === lastScanKey && now - lastScanAt < 1200)) return;
    processingScan = true;
    lastScanKey = scanKey;
    lastScanAt = now;
    try {
      let card: CardBalanceResult;
      try {
        card = await readCardViaApdu(event);
      } catch {
        card = identifyCardFromTag(event);
      }

      const { delta, type: changeType } = calculateBalanceDelta(card);
      card.lastAmount = delta > 0 ? delta : (card.lastAmount || 0);

      currentCard = card;

      if (settings.enableSound) playTapBeep();
      if (settings.enableHaptics) triggerHaptics();

      if (settings.autoSaveHistory) {
        await saveTapRecord(card, 'GT_CILILITAN_1');
        savedCards = await getStoredCards();
        syncTapHistory(card, 'GT_CILILITAN_1');
      }

      if (changeType === 'Top up') {
        showToast(`Saldo bertambah +${formatRupiah(delta)}`);
        statusMessage = `Top up berhasil: +${formatRupiah(delta)}`;
      } else if (changeType === 'Payment') {
        showToast(`Saldo terpotong -${formatRupiah(delta)}`);
        statusMessage = `Terpotong: -${formatRupiah(delta)}`;
      } else {
        statusMessage = `Kartu terbaca: ${card.cardType}`;
      }
    } catch {
      statusMessage = 'Gagal memproses data kartu';
    } finally {
      processingScan = false;
      try {
        await IsoDepNfc.closeTag();
      } catch {
        // ignore
      }
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
      if (isoDepListener) {
        await isoDepListener.remove();
        isoDepListener = null;
      }

      isoDepListener = await IsoDepNfc.addListener('cardDiscovered', async (event: IsoDepEvent) => {
        await processDiscoveredCard({ tag: event } as NfcEvent);
      });

      tagDiscoveredListener = await CapacitorNfc.addListener('tagDiscovered', async (event: NfcEvent) => {
        await processDiscoveredCard(event);
      });

      nfcEventListener = await CapacitorNfc.addListener('nfcEvent', async (event: NfcEvent) => {
        await processDiscoveredCard(event);
      });

      await IsoDepNfc.startScanning();
      isScanning = true;
      statusMessage = 'Sensor siap. Tempelkan kartu uang elektronik.';
    } catch {
      statusMessage = 'Gagal mengaktifkan sensor pemindaian';
      isScanning = false;
    }
  }

  async function stopScanner() {
    try {
      if (tagDiscoveredListener) {
        await tagDiscoveredListener.remove();
        tagDiscoveredListener = null;
      }
      if (nfcEventListener) {
        await nfcEventListener.remove();
        nfcEventListener = null;
      }
      if (isoDepListener) {
        await isoDepListener.remove();
        isoDepListener = null;
      }
      await IsoDepNfc.stopScanning();
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

  async function simulateCardTap(type: 'Mandiri' | 'BCA' | 'BRI' | 'BNI' | 'KCI' | 'Mega', customBalance?: number) {
    const mockMap = {
      Mandiri: { type: 'Mandiri e-Money', num: '6032 1290 8820 1290', bal: 87500, issuer: 'Mandiri' as const },
      BCA: { type: 'BCA Flazz', num: '1809 3321 4410 8821', bal: 142000, issuer: 'BCA' as const },
      BRI: { type: 'BRI Brizzi', num: '5018 7721 9021 4410', bal: 26000, issuer: 'BRI' as const },
      BNI: { type: 'BNI TapCash', num: '7546 1102 5590 9012', bal: 53500, issuer: 'BNI' as const },
      KCI: { type: 'KMT Commuter Line', num: '1002 8840 7712 3341', bal: 18000, issuer: 'KCI' as const },
      Mega: { type: 'Bank Mega MegaCash', num: '6019 8800 4421 9912', bal: 65000, issuer: 'Mega' as const }
    };

    const target = mockMap[type];
    const cardBalance = typeof customBalance === 'number' ? customBalance : target.bal;
    const card: CardBalanceResult = {
      cardType: target.type,
      issuer: target.issuer,
      cardNumber: target.num,
      balance: cardBalance,
      lastAmount: 0,
      timestamp: new Date().toISOString(),
      history: [
        { type: 'Payment', amount: 15000, date: '2026-10-30', time: '14:22:10', balance: cardBalance },
        { type: 'Top up', amount: 50000, date: '2026-10-28', time: '09:15:00', balance: cardBalance + 15000 }
      ]
    };

    const { delta, type: changeType } = calculateBalanceDelta(card);
    card.lastAmount = delta > 0 ? delta : 0;

    currentCard = card;

    if (settings.enableSound) playTapBeep();
    if (settings.enableHaptics) triggerHaptics();

    if (settings.autoSaveHistory) {
      await saveTapRecord(card, 'GT_CILILITAN_1');
      savedCards = await getStoredCards();
      syncTapHistory(card, 'GT_CILILITAN_1');
    }

    if (changeType === 'Top up') {
      showToast(`Saldo bertambah +${formatRupiah(delta)}`);
      statusMessage = `Top up sukses: +${formatRupiah(delta)}`;
    } else if (changeType === 'Payment') {
      showToast(`Saldo terpotong -${formatRupiah(delta)}`);
      statusMessage = `Terpotong: -${formatRupiah(delta)}`;
    } else {
      showToast(`Simulasi sukses: ${card.cardType}`);
      statusMessage = `Simulasi sukses: ${card.cardType}`;
    }
  }

  if (typeof window !== 'undefined') {
    (window as any).__simulateCardTap = simulateCardTap;
  }

  function handleSelectSavedCard(item: StoredCardRecord) {
    currentCard = {
      cardNumber: item.cardNumber,
      cardType: item.cardType,
      issuer: (item.issuer as CardIssuer) || 'Unknown',
      balance: item.balance ?? item.lastBalance ?? 0,
      lastAmount: item.lastAmount ?? 0,
      cardId: item.cardId,
      history: item.history || [],
      timestamp: item.timestamp || item.updatedAt || new Date().toISOString()
    };
    showToast(`Kartu ${item.cardType} dimuat ulang`);
    statusMessage = `Kartu terpilih: ${item.cardType}`;
  }

  async function handleDeleteSavedCard(e: MouseEvent, cardNumber: string) {
    e.stopPropagation();
    await deleteStoredCard(cardNumber);
    savedCards = await getStoredCards();
    if (currentCard?.cardNumber === cardNumber) {
      currentCard = null;
    }
    showToast('Kartu dihapus dari daftar tersimpan');
  }

  async function handleClearAllData() {
    await clearAllStoredData();
    savedCards = [];
    currentCard = null;
    statusMessage = 'Tempelkan kartu di belakang ponsel';
    showToast('Semua data dan kartu tersimpan dibersihkan');
  }

  function handleClearCurrentCard() {
    currentCard = null;
    statusMessage = 'Tempelkan kartu di belakang ponsel';
    showToast('Tampilan kartu aktif ditutup');
  }

  function formatTransactionDate(date: string) {
    return new Date(`${date}T00:00:00`).toLocaleDateString('id-ID', { day: 'numeric', month: 'short', year: 'numeric' });
  }

  onMount(async () => {
    applyTheme(settings.theme);

    if (typeof window !== 'undefined' && window.matchMedia) {
      systemThemeQuery = window.matchMedia('(prefers-color-scheme: dark)');
      systemThemeQuery.addEventListener('change', handleSystemThemeChange);
    }

    try {
      savedCards = await getStoredCards();
    } catch {
      savedCards = [];
    }

    await initNfc();
    cardDefinitions = await fetchCardDefinitions();
  });

  onDestroy(() => {
    if (pollInterval) clearInterval(pollInterval);
    if (stateChangeListener) stateChangeListener.remove();
    if (tagDiscoveredListener) tagDiscoveredListener.remove();
    if (nfcEventListener) nfcEventListener.remove();
    if (isoDepListener) isoDepListener.remove();
    if (systemThemeQuery) {
      systemThemeQuery.removeEventListener('change', handleSystemThemeChange);
    }
  });
</script>

<main class="shell">
  <!-- Neobrutalist Header -->
  <header class="neo-header">
    <div class="brand-group">
      <div class="brand-box">
        <svg viewBox="0 0 24 24" class="brand-icon" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round">
          <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
          <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        </svg>
      </div>
      <div>
        <h1 class="brand-title">TapMoney Info</h1>
        <p class="brand-sub">NFC Smart Card Reader</p>
      </div>
    </div>

    <div class="header-tools">
      <span class="neo-status-pill {nfcEnabled ? 'status--active' : 'status--inactive'}">
        <span class="status-indicator"></span>
        <span>{nfcEnabled ? 'NFC AKTIF' : 'NFC MATI'}</span>
      </span>

      <button
        class="neo-btn-icon"
        onclick={() => showSettingsModal = true}
        title="Pengaturan"
        aria-label="Pengaturan"
      >
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="3" />
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
        </svg>
      </button>
    </div>
  </header>

  <!-- NFC Disabled Banner -->
  {#if !nfcEnabled && Capacitor.isNativePlatform()}
    <aside class="neo-alert-box">
      <div class="alert-content">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.5" class="alert-icon">
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="8" x2="12" y2="12" />
          <line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
        <div>
          <div class="alert-title">SENSOR NFC NONAKTIF</div>
          <div class="alert-desc">Aktifkan NFC di setelan perangkat untuk membaca kartu fisik.</div>
        </div>
      </div>
      <button class="neo-btn neo-btn-danger" onclick={openNfcSettings}>
        Buka Pengaturan NFC
      </button>
    </aside>
  {/if}

  <!-- Main Card Area -->
  <section class="card-section">
    <CardVisual
      card={currentCard}
      {activeTheme}
      hideBalance={settings.hideBalance}
      maskCardNumber={settings.maskCardNumber}
      onToggleBalance={() => updateSetting('hideBalance', !settings.hideBalance)}
      onToggleMask={() => updateSetting('maskCardNumber', !settings.maskCardNumber)}
      onCopyCardNumber={handleCopyCardNumber}
    />

    {#if currentCard}
      <div class="neo-action-grid">
        <button class="neo-action-btn" onclick={() => handleCopyCardNumber(currentCard!.cardNumber)}>
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
          </svg>
          Salin No
        </button>
        <button class="neo-action-btn" onclick={handleShareReceipt}>
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="18" cy="5" r="3" />
            <circle cx="6" cy="12" r="3" />
            <circle cx="18" cy="19" r="3" />
            <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
            <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
          </svg>
          Bagikan
        </button>
        <button class="neo-action-btn" onclick={handlePrintReceipt}>
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="6 9 6 2 18 2 18 9" />
            <path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2" />
            <rect x="6" y="14" width="12" height="8" />
          </svg>
          Cetak
        </button>
        <button class="neo-action-btn btn-danger-action" onclick={handleClearCurrentCard} title="Tutup kartu aktif">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="3 6 5 6 21 6" />
            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
          </svg>
          Tutup
        </button>
      </div>
    {/if}

    <!-- Radar / Sensor box -->
    <div class="neo-radar-box {isScanning ? 'neo-radar--active' : ''}">
      <div class="radar-dot"></div>
      <p class="radar-text">{statusMessage}</p>
    </div>
  </section>

  <!-- Browser Simulator for Testing -->
  {#if !Capacitor.isNativePlatform()}
    <section class="neo-card-box">
      <div class="box-heading">
        <h2 class="box-title">SIMULATOR UJI KARTU</h2>
        <span class="neo-tag neo-tag--accent">DEV SIM</span>
      </div>
      <p class="box-sub">Pilih salah satu kartu untuk mensimulasikan pembacaan NFC.</p>
      <div class="neo-sim-grid">
        <button class="neo-sim-btn" onclick={() => simulateCardTap('Mandiri')}>e-Money</button>
        <button class="neo-sim-btn" onclick={() => simulateCardTap('BCA')}>Flazz</button>
        <button class="neo-sim-btn neo-sim-btn--yellow" onclick={() => simulateCardTap('BCA', (currentCard?.issuer === 'BCA' ? currentCard.balance : 142000) + 100000)}>
          Flazz +100k
        </button>
        <button class="neo-sim-btn" onclick={() => simulateCardTap('BRI')}>Brizzi</button>
        <button class="neo-sim-btn" onclick={() => simulateCardTap('BNI')}>TapCash</button>
        <button class="neo-sim-btn" onclick={() => simulateCardTap('KCI')}>KMT</button>
        <button class="neo-sim-btn" onclick={() => simulateCardTap('Mega')}>MegaCash</button>
      </div>
    </section>
  {/if}

  <!-- Dual History Views -->
  <section class="neo-card-box">
    <div class="tabs-control-row">
      <div class="segmented-tabs">
        <button
          type="button"
          class="seg-tab {activeHistoryTab === 'chip' ? 'seg-tab--active' : ''}"
          onclick={() => activeHistoryTab = 'chip'}
        >
          <span>RIWAYAT CHIP</span>
          {#if currentCard?.history && currentCard.history.length > 0}
            <span class="seg-badge">{currentCard.history.length}</span>
          {/if}
        </button>
        <button
          type="button"
          class="seg-tab {activeHistoryTab === 'saved' ? 'seg-tab--active' : ''}"
          onclick={() => activeHistoryTab = 'saved'}
        >
          <span>KARTU TERSIMPAN</span>
          {#if savedCards.length > 0}
            <span class="seg-badge">{savedCards.length}</span>
          {/if}
        </button>
      </div>

      {#if activeHistoryTab === 'saved' && savedCards.length > 0}
        <button class="neo-btn-clean" onclick={handleClearAllData}>
          Bersihkan
        </button>
      {/if}
    </div>

    <!-- Tab 1: On-Chip Physical History -->
    {#if activeHistoryTab === 'chip'}
      {#if currentCard?.history && currentCard.history.length > 0}
        <div class="neo-list-stack">
          {#each currentCard.history as tx (`${tx.date}-${tx.time}-${tx.amount}-${tx.terminalId || ''}`)}
            <div class="neo-list-item">
              <div class="list-item-main">
                <div class="tx-badge-bar">
                  <span class="tx-type-badge {tx.type === 'Top up' ? 'badge--pos' : 'badge--neg'}">
                    {tx.type.toUpperCase()}
                  </span>
                  {#if tx.terminalId}
                    <span class="terminal-id">TID: {tx.terminalId}</span>
                  {/if}
                </div>
                <div class="tx-time-text">
                  {formatTransactionDate(tx.date)} • {tx.time}
                </div>
              </div>
              <div class="list-item-side">
                <span class="tx-amount-val {tx.type === 'Top up' ? 'amount--pos' : 'amount--neg'}">
                  {tx.type === 'Top up' ? '+' : '-'}{formatRupiah(tx.amount)}
                </span>
                {#if typeof tx.balance === 'number'}
                  <span class="tx-rest-balance">Sisa: {formatRupiah(tx.balance)}</span>
                {/if}
              </div>
            </div>
          {/each}
        </div>
      {:else}
        <div class="neo-empty-tray">
          <svg viewBox="0 0 24 24" width="36" height="36" class="neo-empty-icon" fill="none" stroke="currentColor" stroke-width="2.2">
            <rect x="2" y="5" width="20" height="14" rx="2" />
            <line x1="2" y1="10" x2="22" y2="10" />
          </svg>
          <div class="neo-empty-title">Tidak ada transaksi chip</div>
          <div class="neo-empty-sub">
            {currentCard ? 'Kartu ini tidak memiliki catatan riwayat transaksi di dalam chip.' : 'Tempelkan kartu fisik uang elektronik untuk membaca log transaksi on-chip.'}
          </div>
        </div>
      {/if}
    {:else}
      <!-- Tab 2: Saved Scanned Cards List (Reloadable on click) -->
      {#if savedCards.length > 0}
        <div class="neo-list-stack">
          {#each savedCards as item (item.cardNumber)}
            <div
              class="neo-list-item neo-card-item {currentCard?.cardNumber === item.cardNumber ? 'item--active' : ''}"
              onclick={() => handleSelectSavedCard(item)}
              role="button"
              tabindex="0"
              onkeydown={(e) => e.key === 'Enter' && handleSelectSavedCard(item)}
            >
              <div class="list-item-main">
                <div class="card-header-bar">
                  <span class="card-type-title">{item.cardType}</span>
                  {#if currentCard?.cardNumber === item.cardNumber}
                    <span class="active-pill">AKTIF</span>
                  {/if}
                </div>
                <span class="card-pan-code">
                  {formatDisplayCardNumber(item.cardNumber, settings.maskCardNumber)}
                </span>
                <span class="card-scan-date">
                  Tap: {new Date(item.timestamp || item.updatedAt || '').toLocaleString('id-ID', {
                    day: 'numeric',
                    month: 'short',
                    hour: '2-digit',
                    minute: '2-digit'
                  })}
                </span>
              </div>

              <div class="list-item-side">
                <span class="card-balance-highlight">
                  {formatDisplayBalance(item.balance ?? item.lastBalance ?? 0, settings.hideBalance)}
                </span>
                <button
                  type="button"
                  class="neo-delete-btn"
                  onclick={(e) => handleDeleteSavedCard(e, item.cardNumber)}
                  title="Hapus dari daftar tersimpan"
                  aria-label="Hapus dari daftar tersimpan"
                >
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="3 6 5 6 21 6" />
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                  </svg>
                </button>
              </div>
            </div>
          {/each}
        </div>
      {:else}
        <div class="neo-empty-tray">
          <svg viewBox="0 0 24 24" width="36" height="36" class="neo-empty-icon" fill="none" stroke="currentColor" stroke-width="2.2">
            <rect x="2" y="4" width="20" height="16" rx="2" />
            <line x1="6" y1="8" x2="10" y2="8" />
            <line x1="6" y1="12" x2="18" y2="12" />
            <line x1="6" y1="16" x2="14" y2="16" />
          </svg>
          <div class="neo-empty-title">Belum ada kartu tersimpan</div>
          <div class="neo-empty-sub">
            Pindai kartu untuk menyimpan informasi saldo dan nomor kartu secara otomatis.
          </div>
        </div>
      {/if}
    {/if}
  </section>

  <!-- Settings Modal -->
  {#if showSettingsModal}
    <div class="neo-modal-backdrop" onclick={() => showSettingsModal = false} role="presentation">
      <div
        class="neo-modal-card"
        onclick={(e) => e.stopPropagation()}
        onkeydown={(e) => e.key === 'Escape' && (showSettingsModal = false)}
        role="dialog"
        aria-modal="true"
        aria-labelledby="settings-title"
        tabindex="-1"
      >
        <div class="modal-card-header">
          <h2 id="settings-title" class="modal-card-title">PENGATURAN</h2>
          <button class="neo-btn-icon" onclick={() => showSettingsModal = false} aria-label="Tutup">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.6">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div class="settings-list">
          <!-- Theme Selector -->
          <div class="setting-item-group">
            <div class="setting-name">Tema Tampilan</div>
            <div class="setting-hint">Pilih skema warna antarmuka Neobrutalism</div>
            <div class="neo-theme-segmented">
              <button
                type="button"
                class="theme-btn {settings.theme === 'system' ? 'theme-btn--active' : ''}"
                onclick={() => updateSetting('theme', 'system')}
              >
                SISTEM
              </button>
              <button
                type="button"
                class="theme-btn {settings.theme === 'light' ? 'theme-btn--active' : ''}"
                onclick={() => updateSetting('theme', 'light')}
              >
                TERANG
              </button>
              <button
                type="button"
                class="theme-btn {settings.theme === 'dark' ? 'theme-btn--active' : ''}"
                onclick={() => updateSetting('theme', 'dark')}
              >
                GELAP
              </button>
            </div>
          </div>

          <label class="setting-toggle-row">
            <div>
              <div class="setting-name">Nada Bunyi (Beep)</div>
              <div class="setting-hint">Bunyikan audio saat kartu terdeteksi</div>
            </div>
            <input
              type="checkbox"
              class="neo-checkbox"
              checked={settings.enableSound}
              onchange={(e) => updateSetting('enableSound', (e.target as HTMLInputElement).checked)}
            />
          </label>

          <label class="setting-toggle-row">
            <div>
              <div class="setting-name">Getaran (Haptics)</div>
              <div class="setting-hint">Getarkan ponsel saat kartu terbaca</div>
            </div>
            <input
              type="checkbox"
              class="neo-checkbox"
              checked={settings.enableHaptics}
              onchange={(e) => updateSetting('enableHaptics', (e.target as HTMLInputElement).checked)}
            />
          </label>

          <label class="setting-toggle-row">
            <div>
              <div class="setting-name">Sensor Nomor (Mask PAN)</div>
              <div class="setting-hint">Samarkan digit tengah nomor kartu</div>
            </div>
            <input
              type="checkbox"
              class="neo-checkbox"
              checked={settings.maskCardNumber}
              onchange={(e) => updateSetting('maskCardNumber', (e.target as HTMLInputElement).checked)}
            />
          </label>

          <label class="setting-toggle-row">
            <div>
              <div class="setting-name">Sembunyikan Saldo</div>
              <div class="setting-hint">Tutup angka saldo dengan sensor titik</div>
            </div>
            <input
              type="checkbox"
              class="neo-checkbox"
              checked={settings.hideBalance}
              onchange={(e) => updateSetting('hideBalance', (e.target as HTMLInputElement).checked)}
            />
          </label>

          <label class="setting-toggle-row">
            <div>
              <div class="setting-name">Simpan Otomatis</div>
              <div class="setting-hint">Simpan kartu ke basis data lokal perangkat</div>
            </div>
            <input
              type="checkbox"
              class="neo-checkbox"
              checked={settings.autoSaveHistory}
              onchange={(e) => updateSetting('autoSaveHistory', (e.target as HTMLInputElement).checked)}
            />
          </label>
        </div>

        <button class="neo-btn neo-btn-primary" onclick={() => showSettingsModal = false}>
          Tutup Pengaturan
        </button>
      </div>
    </div>
  {/if}

  <!-- Neobrutalist Toast Notification -->
  {#if toastMessage}
    <div class="neo-toast" role="status">
      {toastMessage}
    </div>
  {/if}
</main>

<style>
  :global(*), :global(*::before), :global(*::after) {
    box-sizing: border-box;
  }

  /* Neobrutalism Design Tokens */
  :global(:root) {
    --neo-bg: #f5f5f0;
    --neo-border: #000000;
    --neo-shadow: #000000;
    --neo-surface: #ffffff;
    --neo-surface-alt: #f0f0eb;
    --neo-text: #000000;
    --neo-text-subtle: #4b5563;
    --neo-yellow: #ffe600;
    --neo-blue: #3b82f6;
    --neo-green: #00e599;
    --neo-red: #ff4d4d;
    --neo-purple: #a855f7;
    --neo-modal-backdrop: rgba(0, 0, 0, 0.65);
  }

  :global(.theme-light) {
    --neo-bg: #f5f5f0;
    --neo-border: #000000;
    --neo-shadow: #000000;
    --neo-surface: #ffffff;
    --neo-surface-alt: #f0f0eb;
    --neo-text: #000000;
    --neo-text-subtle: #4b5563;
    --neo-yellow: #ffe600;
    --neo-blue: #3b82f6;
    --neo-green: #00e599;
    --neo-red: #ff4d4d;
    --neo-purple: #a855f7;
    --neo-modal-backdrop: rgba(0, 0, 0, 0.65);
  }

  :global(.theme-dark) {
    --neo-bg: #141416;
    --neo-border: #ffffff;
    --neo-shadow: #ffffff;
    --neo-surface: #202024;
    --neo-surface-alt: #2a2a30;
    --neo-text: #ffffff;
    --neo-text-subtle: #9ca3af;
    --neo-yellow: #ffe600;
    --neo-blue: #60a5fa;
    --neo-green: #34d399;
    --neo-red: #f87171;
    --neo-purple: #c084fc;
    --neo-modal-backdrop: rgba(0, 0, 0, 0.82);
  }

  :global(body) {
    margin: 0;
    padding: 0;
    background-color: var(--neo-bg);
    color: var(--neo-text);
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    -webkit-font-smoothing: antialiased;
    -webkit-tap-highlight-color: transparent;
    transition: background-color 0.2s ease, color 0.2s ease;
  }

  .shell {
    max-width: 480px;
    margin: 0 auto;
    padding: 20px 16px 48px;
    min-height: 100vh;
    display: flex;
    flex-direction: column;
    gap: 18px;
  }

  /* Header */
  .neo-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
  }

  .brand-group {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .brand-box {
    width: 44px;
    height: 44px;
    background: var(--neo-yellow);
    border: 2.5px solid var(--neo-border);
    box-shadow: 3px 3px 0px var(--neo-shadow);
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #000000;
  }

  .brand-icon {
    width: 24px;
    height: 24px;
  }

  .brand-title {
    margin: 0;
    font-size: 20px;
    font-weight: 900;
    letter-spacing: -0.03em;
    color: var(--neo-text);
    text-transform: uppercase;
  }

  .brand-sub {
    margin: 1px 0 0 0;
    font-size: 11px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: var(--neo-text-subtle);
  }

  .header-tools {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .neo-status-pill {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 5px 10px;
    border: 2px solid var(--neo-border);
    box-shadow: 2px 2px 0px var(--neo-shadow);
    border-radius: 6px;
    font-size: 10px;
    font-weight: 900;
    letter-spacing: 0.04em;
    text-transform: uppercase;
  }

  .status--active {
    background: var(--neo-green);
    color: #000000;
  }

  .status--inactive {
    background: var(--neo-red);
    color: #ffffff;
  }

  .status-indicator {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: currentColor;
    border: 1px solid #000000;
  }

  .neo-btn-icon {
    width: 40px;
    height: 40px;
    background: var(--neo-surface);
    border: 2.5px solid var(--neo-border);
    box-shadow: 3px 3px 0px var(--neo-shadow);
    border-radius: 8px;
    color: var(--neo-text);
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: transform 0.1s ease, box-shadow 0.1s ease;
  }

  .neo-btn-icon:active {
    transform: translate(2px, 2px);
    box-shadow: 1px 1px 0px var(--neo-shadow);
  }

  /* Alert Box */
  .neo-alert-box {
    background: var(--neo-yellow);
    color: #000000;
    border: 2.5px solid var(--neo-border);
    box-shadow: 4px 4px 0px var(--neo-shadow);
    border-radius: 12px;
    padding: 14px;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .alert-content {
    display: flex;
    align-items: flex-start;
    gap: 10px;
  }

  .alert-icon {
    flex-shrink: 0;
    margin-top: 1px;
    color: #000000;
  }

  .alert-title {
    font-size: 13px;
    font-weight: 900;
    letter-spacing: 0.04em;
  }

  .alert-desc {
    font-size: 12px;
    font-weight: 600;
    margin-top: 2px;
    line-height: 1.35;
  }

  /* Card Section */
  .card-section {
    display: flex;
    flex-direction: column;
    gap: 14px;
    width: 100%;
  }

  /* Actions Grid */
  .neo-action-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 8px;
  }

  .neo-action-btn {
    background: var(--neo-surface);
    border: 2px solid var(--neo-border);
    box-shadow: 3px 3px 0px var(--neo-shadow);
    border-radius: 8px;
    color: var(--neo-text);
    padding: 10px 4px;
    font-size: 11px;
    font-weight: 800;
    text-transform: uppercase;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4px;
    cursor: pointer;
    transition: transform 0.1s ease, box-shadow 0.1s ease;
  }

  .neo-action-btn:active {
    transform: translate(2px, 2px);
    box-shadow: 1px 1px 0px var(--neo-shadow);
  }

  .btn-danger-action {
    background: var(--neo-red);
    color: #ffffff;
  }

  /* Radar Box */
  .neo-radar-box {
    display: flex;
    align-items: center;
    gap: 10px;
    background: var(--neo-surface);
    border: 2px solid var(--neo-border);
    box-shadow: 3px 3px 0px var(--neo-shadow);
    border-radius: 8px;
    padding: 11px 14px;
  }

  .radar-dot {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: var(--neo-yellow);
    border: 1.5px solid var(--neo-border);
    flex-shrink: 0;
  }

  .neo-radar--active .radar-dot {
    background: var(--neo-green);
    animation: neo-pulse 1.4s infinite;
  }

  @keyframes neo-pulse {
    0% { transform: scale(0.9); }
    50% { transform: scale(1.3); }
    100% { transform: scale(0.9); }
  }

  .radar-text {
    margin: 0;
    font-size: 12px;
    font-weight: 700;
    color: var(--neo-text);
    line-height: 1.3;
  }

  /* Universal Neobrutalist Box */
  .neo-card-box {
    background: var(--neo-surface);
    border: 2.5px solid var(--neo-border);
    box-shadow: 4px 4px 0px var(--neo-shadow);
    border-radius: 12px;
    padding: 16px;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .box-heading {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .box-title {
    margin: 0;
    font-size: 13px;
    font-weight: 900;
    letter-spacing: 0.05em;
    color: var(--neo-text);
  }

  .box-sub {
    margin: 0;
    font-size: 12px;
    font-weight: 600;
    color: var(--neo-text-subtle);
  }

  .neo-tag {
    font-size: 10px;
    font-weight: 900;
    padding: 2px 7px;
    border: 1.5px solid var(--neo-border);
    border-radius: 4px;
    text-transform: uppercase;
  }

  .neo-tag--accent {
    background: var(--neo-yellow);
    color: #000000;
  }

  /* Sim Grid */
  .neo-sim-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
  }

  .neo-sim-btn {
    background: var(--neo-surface-alt);
    border: 2px solid var(--neo-border);
    box-shadow: 2px 2px 0px var(--neo-shadow);
    color: var(--neo-text);
    font-size: 11px;
    font-weight: 800;
    padding: 8px 6px;
    border-radius: 6px;
    cursor: pointer;
    text-align: center;
    transition: transform 0.1s ease, box-shadow 0.1s ease;
  }

  .neo-sim-btn:active {
    transform: translate(2px, 2px);
    box-shadow: 0px 0px 0px var(--neo-shadow);
  }

  .neo-sim-btn--yellow {
    background: var(--neo-yellow);
    color: #000000;
  }

  /* Segmented Tabs */
  .tabs-control-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
  }

  .segmented-tabs {
    display: flex;
    background: var(--neo-surface-alt);
    border: 2px solid var(--neo-border);
    border-radius: 8px;
    padding: 3px;
    gap: 4px;
  }

  .seg-tab {
    background: transparent;
    border: none;
    color: var(--neo-text-subtle);
    font-size: 11px;
    font-weight: 900;
    letter-spacing: 0.04em;
    padding: 6px 10px;
    border-radius: 6px;
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    transition: all 0.15s ease;
  }

  .seg-tab--active {
    background: var(--neo-yellow);
    color: #000000;
    border: 1.5px solid var(--neo-border);
    box-shadow: 2px 2px 0px var(--neo-shadow);
  }

  .seg-badge {
    background: #000000;
    color: #ffffff;
    font-size: 9px;
    font-weight: 900;
    padding: 1px 5px;
    border-radius: 9999px;
  }

  .neo-btn-clean {
    background: transparent;
    border: 1.5px solid var(--neo-border);
    color: var(--neo-red);
    font-size: 11px;
    font-weight: 800;
    padding: 4px 8px;
    border-radius: 6px;
    cursor: pointer;
  }

  /* List Items */
  .neo-list-stack {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .neo-list-item {
    background: var(--neo-surface-alt);
    border: 2px solid var(--neo-border);
    box-shadow: 3px 3px 0px var(--neo-shadow);
    border-radius: 8px;
    padding: 12px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    cursor: pointer;
    transition: transform 0.1s ease, box-shadow 0.1s ease;
  }

  .neo-list-item:active {
    transform: translate(2px, 2px);
    box-shadow: 1px 1px 0px var(--neo-shadow);
  }

  .neo-card-item.item--active {
    border-color: var(--neo-blue);
    background: var(--neo-surface);
  }

  .list-item-main {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .tx-badge-bar,
  .card-header-bar {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .tx-type-badge {
    font-size: 9px;
    font-weight: 900;
    padding: 2px 6px;
    border: 1px solid var(--neo-border);
    border-radius: 4px;
    text-transform: uppercase;
  }

  .badge--pos {
    background: var(--neo-green);
    color: #000000;
  }

  .badge--neg {
    background: var(--neo-yellow);
    color: #000000;
  }

  .terminal-id {
    font-family: ui-monospace, monospace;
    font-size: 10px;
    font-weight: 700;
    color: var(--neo-text-subtle);
  }

  .tx-time-text {
    font-size: 11px;
    font-weight: 600;
    color: var(--neo-text-subtle);
  }

  .card-type-title {
    font-size: 13px;
    font-weight: 800;
    color: var(--neo-text);
  }

  .active-pill {
    background: var(--neo-green);
    color: #000000;
    border: 1px solid var(--neo-border);
    font-size: 8px;
    font-weight: 900;
    padding: 1px 4px;
    border-radius: 3px;
  }

  .card-pan-code {
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 11px;
    font-weight: 700;
    color: var(--neo-text-subtle);
  }

  .card-scan-date {
    font-size: 10px;
    font-weight: 600;
    color: var(--neo-text-subtle);
  }

  .list-item-side {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
  }

  .tx-amount-val {
    font-family: ui-monospace, Menlo, monospace;
    font-size: 14px;
    font-weight: 900;
  }

  .amount--pos {
    color: var(--neo-green);
  }

  .amount--neg {
    color: var(--neo-text);
  }

  .tx-rest-balance {
    font-size: 10px;
    font-weight: 700;
    color: var(--neo-text-subtle);
  }

  .card-balance-highlight {
    font-family: ui-monospace, Menlo, monospace;
    font-size: 15px;
    font-weight: 900;
    color: var(--neo-text);
  }

  .neo-delete-btn {
    background: transparent;
    border: 1px solid var(--neo-border);
    color: var(--neo-red);
    cursor: pointer;
    border-radius: 4px;
    padding: 3px;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: transform 0.1s ease;
  }

  .neo-delete-btn:active {
    transform: scale(0.9);
  }

  /* Empty Tray */
  .neo-empty-tray {
    border: 2px dashed var(--neo-border);
    border-radius: 8px;
    padding: 28px 16px;
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
  }

  .neo-empty-icon {
    color: var(--neo-text-subtle);
  }

  .neo-empty-title {
    font-size: 13px;
    font-weight: 900;
    text-transform: uppercase;
    color: var(--neo-text);
  }

  .neo-empty-sub {
    font-size: 11px;
    font-weight: 600;
    color: var(--neo-text-subtle);
    max-width: 240px;
    line-height: 1.35;
  }

  /* Universal Buttons */
  .neo-btn {
    border: 2.5px solid var(--neo-border);
    box-shadow: 3px 3px 0px var(--neo-shadow);
    font-size: 12px;
    font-weight: 900;
    text-transform: uppercase;
    padding: 10px 14px;
    border-radius: 8px;
    cursor: pointer;
    transition: transform 0.1s ease, box-shadow 0.1s ease;
  }

  .neo-btn:active {
    transform: translate(2px, 2px);
    box-shadow: 1px 1px 0px var(--neo-shadow);
  }

  .neo-btn-primary {
    background: var(--neo-yellow);
    color: #000000;
    width: 100%;
  }

  .neo-btn-danger {
    background: var(--neo-red);
    color: #ffffff;
    width: 100%;
  }

  /* Modal */
  .neo-modal-backdrop {
    position: fixed;
    inset: 0;
    background: var(--neo-modal-backdrop);
    display: flex;
    align-items: flex-end;
    justify-content: center;
    z-index: 1000;
  }

  .neo-modal-card {
    background: var(--neo-surface);
    border: 3px solid var(--neo-border);
    box-shadow: 0px -4px 0px var(--neo-shadow);
    border-radius: 16px 16px 0 0;
    width: 100%;
    max-width: 480px;
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .modal-card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .modal-card-title {
    margin: 0;
    font-size: 16px;
    font-weight: 900;
    letter-spacing: 0.04em;
  }

  .settings-list {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .setting-item-group {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding-bottom: 10px;
    border-bottom: 2px solid var(--neo-border);
  }

  .setting-name {
    font-size: 13px;
    font-weight: 800;
  }

  .setting-hint {
    font-size: 11px;
    font-weight: 600;
    color: var(--neo-text-subtle);
  }

  .neo-theme-segmented {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    background: var(--neo-surface-alt);
    border: 2px solid var(--neo-border);
    border-radius: 6px;
    padding: 3px;
    gap: 4px;
    margin-top: 4px;
  }

  .theme-btn {
    background: transparent;
    border: none;
    color: var(--neo-text-subtle);
    font-size: 11px;
    font-weight: 800;
    padding: 7px;
    border-radius: 4px;
    cursor: pointer;
  }

  .theme-btn--active {
    background: var(--neo-yellow);
    color: #000000;
    border: 1px solid var(--neo-border);
    box-shadow: 1.5px 1.5px 0px var(--neo-shadow);
  }

  .setting-toggle-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    cursor: pointer;
  }

  .neo-checkbox {
    width: 22px;
    height: 22px;
    accent-color: var(--neo-yellow);
    border: 2px solid var(--neo-border);
    cursor: pointer;
  }

  /* Toast */
  .neo-toast {
    position: fixed;
    bottom: 24px;
    left: 50%;
    transform: translateX(-50%);
    background: var(--neo-yellow);
    color: #000000;
    border: 2.5px solid var(--neo-border);
    box-shadow: 4px 4px 0px var(--neo-shadow);
    font-size: 12px;
    font-weight: 900;
    padding: 10px 18px;
    border-radius: 6px;
    z-index: 2000;
    text-transform: uppercase;
  }
</style>
