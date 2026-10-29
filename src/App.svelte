<script lang="ts">
  import { onMount } from 'svelte';
  import { Capacitor } from '@capacitor/core';
  import { CapacitorNfc } from '@capgo/capacitor-nfc';
  import {
    checkNfcStatus,
    formatRupiah,
    readCardViaApdu
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
    type UserSettings
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
      showToast('Nomor kartu disalin ke clipboard');
    } catch {
      showToast('Gagal menyalin nomor kartu');
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
  }

  async function initNfc() {
    const status = await checkNfcStatus();
    nfcSupported = status.supported;
    nfcEnabled = status.enabled;

    if (Capacitor.isNativePlatform() && nfcEnabled) {
      try {
        await CapacitorNfc.addListener('tagDiscovered', async (event) => {
          const card = await readCardViaApdu(event);
          currentCard = card;

          if (settings.enableSound) playTapBeep();
          if (settings.enableHaptics) triggerHaptics();

          if (settings.autoSaveHistory) {
            await saveTapRecord(card, 'GT_CILILITAN_1');
            scanHistory = [card, ...scanHistory.slice(0, 19)];
            await syncTapHistory(card, 'GT_CILILITAN_1');
          }

          statusMessage = `Kartu terbaca: ${card.cardType}`;
        });

        await CapacitorNfc.startScanning();
        isScanning = true;
      } catch {
        statusMessage = 'Gagal memulai pemindai NFC';
      }
    } else if (!Capacitor.isNativePlatform()) {
      statusMessage = 'Mode Browser Dev: Gunakan tombol simulasi di bawah';
    }
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
</script>

<main class="container">
  <header class="app-header">
    <div class="header-content">
      <div class="brand-group">
        <h1 class="brand-title">TapMoney Info</h1>
        <span class="badge {nfcEnabled || !Capacitor.isNativePlatform() ? 'badge-ok' : 'badge-warn'}">
          {Capacitor.isNativePlatform() ? (nfcEnabled ? 'NFC Aktif' : 'NFC Mati') : 'Dev Web'}
        </span>
      </div>

      <button
        class="btn-icon"
        onclick={() => showSettingsModal = true}
        title="Buka Pengaturan"
        aria-label="Pengaturan"
      >
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 15a3 3 0 100-6 3 3 0 000 6z" />
          <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06a1.65 1.65 0 00.33-1.82 1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06a1.65 1.65 0 001.82.33H9a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z" />
        </svg>
      </button>
    </div>
    <p class="brand-subtitle">Cek saldo Mandiri e-Money, Flazz, Brizzi, TapCash, KMT & MegaCash</p>
  </header>

  <section class="card-display-section">
    <div
      class="virtual-card {currentCard ? currentCard.issuer.toLowerCase() : 'idle'}"
      onclick={() => currentCard && handleCopyCardNumber(currentCard.cardNumber)}
      title="Ketuk kartu untuk menyalin nomor"
      role="button"
      tabindex="0"
      onkeydown={(e) => e.key === 'Enter' && currentCard && handleCopyCardNumber(currentCard.cardNumber)}
    >
      <div class="card-top">
        <span class="card-issuer">{currentCard ? currentCard.cardType : 'SIAP MEMINDAI'}</span>
        <span class="nfc-icon">NFC</span>
      </div>

      <div class="card-chip"></div>

      <div class="card-middle">
        <div class="card-balance-label">Sisa Saldo</div>
        <div class="card-balance-value">
          {currentCard ? formatRupiah(currentCard.balance) : 'Rp 0'}
        </div>
      </div>

      <div class="card-bottom">
        <span class="card-number">
          {currentCard ? formatDisplayCardNumber(currentCard.cardNumber, settings.maskCardNumber) : '---- ---- ---- ----'}
        </span>
        <span class="card-date">
          {currentCard ? new Date(currentCard.timestamp).toLocaleTimeString('id-ID') : '--:--'}
        </span>
      </div>
    </div>

    {#if currentCard}
      <div class="card-actions-bar">
        <button class="btn btn-action" onclick={() => handleCopyCardNumber(currentCard!.cardNumber)}>
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
            <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1" />
          </svg>
          Salin No
        </button>
        <button class="btn btn-action" onclick={handleShareReceipt}>
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="18" cy="5" r="3" />
            <circle cx="6" cy="12" r="3" />
            <circle cx="18" cy="19" r="3" />
            <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
            <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
          </svg>
          Bagikan
        </button>
        <button class="btn btn-action" onclick={handlePrintReceipt}>
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="6 9 6 2 18 2 18 9" />
            <path d="M6 18H4a2 2 0 01-2-2v-5a2 2 0 012-2h16a2 2 0 012 2v5a2 2 0 01-2 2h-2" />
            <rect x="6" y="14" width="12" height="8" />
          </svg>
          Cetak
        </button>
      </div>
    {/if}

    <div class="scan-status-box">
      <div class="pulse-indicator {isScanning ? 'pulsing' : ''}"></div>
      <p class="status-text">{statusMessage}</p>
    </div>
  </section>

  {#if !Capacitor.isNativePlatform()}
    <section class="sim-section">
      <h2 class="section-title">Uji Simulasi Tap (Dev Mode)</h2>
      <div class="sim-buttons">
        <button class="btn btn-outline" onclick={() => simulateCardTap('Mandiri')}>e-Money</button>
        <button class="btn btn-outline" onclick={() => simulateCardTap('BCA')}>Flazz</button>
        <button class="btn btn-outline" onclick={() => simulateCardTap('BRI')}>Brizzi</button>
        <button class="btn btn-outline" onclick={() => simulateCardTap('BNI')}>TapCash</button>
        <button class="btn btn-outline" onclick={() => simulateCardTap('KCI')}>KMT</button>
        <button class="btn btn-outline" onclick={() => simulateCardTap('Mega')}>MegaCash</button>
      </div>
    </section>
  {/if}

  <section class="history-section">
    <div class="history-header">
      <h2 class="section-title">Riwayat Tap ({scanHistory.length})</h2>
      {#if scanHistory.length > 0}
        <button class="btn-clear" onclick={handleClearHistory} title="Hapus semua riwayat" aria-label="Hapus riwayat">Hapus</button>
      {/if}
    </div>
    {#if scanHistory.length === 0}
      <div class="empty-state">Belum ada riwayat kartu yang dipindai.</div>
    {:else}
      <div class="history-list">
        {#each scanHistory as item (item.timestamp)}
          <div
            class="history-item"
            onclick={() => handleCopyCardNumber(item.cardNumber)}
            role="button"
            tabindex="0"
            onkeydown={(e) => e.key === 'Enter' && handleCopyCardNumber(item.cardNumber)}
            title="Klik untuk salin nomor"
          >
            <div class="history-left">
              <span class="history-type">{item.cardType}</span>
              <span class="history-number">
                {formatDisplayCardNumber(item.cardNumber, settings.maskCardNumber)}
              </span>
              <span class="history-time">{new Date(item.timestamp).toLocaleString('id-ID')}</span>
            </div>
            <div class="history-right">
              <span class="history-balance">{formatRupiah(item.balance)}</span>
            </div>
          </div>
        {/each}
      </div>
    {/if}
  </section>

  {#if showSettingsModal}
    <div class="modal-backdrop" onclick={() => showSettingsModal = false} role="presentation">
      <div
        class="modal-card"
        onclick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="settings-title"
        tabindex="-1"
        onkeydown={(e) => e.key === 'Escape' && (showSettingsModal = false)}
      >
        <div class="modal-header">
          <h2 id="settings-title" class="modal-title">Pengaturan Aplikasi</h2>
          <button class="btn-icon" onclick={() => showSettingsModal = false} aria-label="Tutup">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div class="settings-list">
          <label class="setting-item">
            <div class="setting-info">
              <span class="setting-name">Suara Beep</span>
              <span class="setting-desc">Nada audio saat kartu terbaca</span>
            </div>
            <input
              type="checkbox"
              class="setting-toggle"
              checked={settings.enableSound}
              onchange={(e) => updateSetting('enableSound', (e.target as HTMLInputElement).checked)}
            />
          </label>

          <label class="setting-item">
            <div class="setting-info">
              <span class="setting-name">Getaran (Haptics)</span>
              <span class="setting-desc">Getar ponsel saat mendeteksi kartu</span>
            </div>
            <input
              type="checkbox"
              class="setting-toggle"
              checked={settings.enableHaptics}
              onchange={(e) => updateSetting('enableHaptics', (e.target as HTMLInputElement).checked)}
            />
          </label>

          <label class="setting-item">
            <div class="setting-info">
              <span class="setting-name">Samarkan Nomor Kartu</span>
              <span class="setting-desc">Tampilkan digit tengah sebagai bintang</span>
            </div>
            <input
              type="checkbox"
              class="setting-toggle"
              checked={settings.maskCardNumber}
              onchange={(e) => updateSetting('maskCardNumber', (e.target as HTMLInputElement).checked)}
            />
          </label>

          <label class="setting-item">
            <div class="setting-info">
              <span class="setting-name">Simpan Riwayat Otomatis</span>
              <span class="setting-desc">Catat riwayat tap ke penyimpanan lokal</span>
            </div>
            <input
              type="checkbox"
              class="setting-toggle"
              checked={settings.autoSaveHistory}
              onchange={(e) => updateSetting('autoSaveHistory', (e.target as HTMLInputElement).checked)}
            />
          </label>
        </div>

        <button class="btn btn-close-modal" onclick={() => showSettingsModal = false}>
          Selesai
        </button>
      </div>
    </div>
  {/if}

  {#if toastMessage}
    <div class="toast-popup" role="status">
      {toastMessage}
    </div>
  {/if}
</main>

<style>
  :global(body) {
    margin: 0;
    padding: 0;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    background-color: #0d1117;
    color: #c9d1d9;
  }

  .container {
    max-width: 480px;
    margin: 0 auto;
    padding: 20px 16px;
    min-height: 100vh;
    box-sizing: border-box;
  }

  .app-header {
    margin-bottom: 24px;
  }

  .header-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .brand-group {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .brand-title {
    margin: 0;
    font-size: 22px;
    font-weight: 700;
    color: #f0f6fc;
  }

  .brand-subtitle {
    margin: 6px 0 0 0;
    font-size: 13px;
    color: #8b949e;
  }

  .btn-icon {
    background: transparent;
    border: 1px solid #30363d;
    color: #c9d1d9;
    width: 36px;
    height: 36px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: all 0.15s ease;
  }

  .btn-icon:hover {
    background: #21262d;
    color: #f0f6fc;
    border-color: #8b949e;
  }

  .badge {
    font-size: 11px;
    font-weight: 600;
    padding: 3px 8px;
    border-radius: 12px;
  }

  .badge-ok {
    background: #238636;
    color: #ffffff;
  }

  .badge-warn {
    background: #9e6a03;
    color: #ffffff;
  }

  .card-display-section {
    display: flex;
    flex-direction: column;
    gap: 16px;
    margin-bottom: 24px;
  }

  .virtual-card {
    height: 190px;
    border-radius: 16px;
    padding: 18px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    box-sizing: border-box;
    box-shadow: 0 10px 25px rgba(0, 0, 0, 0.4);
    border: 1px solid rgba(255, 255, 255, 0.1);
    transition: transform 0.2s ease, background 0.3s ease;
  }

  .virtual-card.idle {
    background: linear-gradient(135deg, #1f2937, #111827);
  }

  .virtual-card.mandiri {
    background: linear-gradient(135deg, #0f4c81, #0b2545);
  }

  .virtual-card.bca {
    background: linear-gradient(135deg, #005691, #002b49);
  }

  .virtual-card.bri {
    background: linear-gradient(135deg, #00529b, #002244);
  }

  .virtual-card.bni {
    background: linear-gradient(135deg, #e05e00, #803000);
  }

  .virtual-card.kci {
    background: linear-gradient(135deg, #b91c1c, #7f1d1d);
  }

  .virtual-card.mega {
    background: linear-gradient(135deg, #b45309, #78350f);
  }

  .card-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .card-issuer {
    font-size: 14px;
    font-weight: 700;
    letter-spacing: 0.5px;
    color: #f0f6fc;
  }

  .nfc-icon {
    font-size: 11px;
    font-weight: 800;
    padding: 2px 6px;
    border: 1px solid rgba(255, 255, 255, 0.4);
    border-radius: 4px;
    color: #f0f6fc;
  }

  .card-chip {
    width: 38px;
    height: 28px;
    background: #d4af37;
    border-radius: 6px;
    margin: 8px 0;
  }

  .card-middle {
    margin-top: 4px;
  }

  .card-balance-label {
    font-size: 11px;
    text-transform: uppercase;
    color: rgba(255, 255, 255, 0.7);
    letter-spacing: 0.5px;
  }

  .card-balance-value {
    font-size: 26px;
    font-weight: 800;
    color: #ffffff;
    letter-spacing: 0.5px;
  }

  .card-bottom {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    font-family: monospace;
    font-size: 13px;
    color: rgba(255, 255, 255, 0.85);
  }

  .card-actions-bar {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
  }

  .btn-action {
    background: #161b22;
    border: 1px solid #30363d;
    color: #c9d1d9;
    padding: 8px 10px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    font-size: 12px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.15s ease;
  }

  .btn-action:hover {
    background: #21262d;
    color: #f0f6fc;
    border-color: #8b949e;
  }

  .scan-status-box {
    display: flex;
    align-items: center;
    gap: 12px;
    background: #161b22;
    padding: 12px 16px;
    border-radius: 10px;
    border: 1px solid #30363d;
  }

  .pulse-indicator {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: #388bfd;
    flex-shrink: 0;
  }

  .pulse-indicator.pulsing {
    animation: pulse 1.5s infinite;
  }

  @keyframes pulse {
    0% { transform: scale(0.95); opacity: 0.7; }
    50% { transform: scale(1.3); opacity: 1; }
    100% { transform: scale(0.95); opacity: 0.7; }
  }

  .status-text {
    margin: 0;
    font-size: 13px;
    color: #8b949e;
  }

  .section-title {
    font-size: 15px;
    font-weight: 600;
    margin: 0 0 12px 0;
    color: #e6edf3;
  }

  .sim-section {
    margin-bottom: 24px;
    background: #161b22;
    padding: 16px;
    border-radius: 12px;
    border: 1px solid #30363d;
  }

  .sim-buttons {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(70px, 1fr));
    gap: 8px;
  }

  .btn {
    padding: 8px 10px;
    border-radius: 6px;
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.15s ease;
  }

  .btn-outline {
    background: #21262d;
    color: #c9d1d9;
    border: 1px solid #30363d;
  }

  .btn-outline:hover {
    background: #30363d;
    color: #f0f6fc;
  }

  .history-section {
    margin-top: 20px;
  }

  .history-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
  }

  .btn-clear {
    background: transparent;
    border: 1px solid #30363d;
    color: #8b949e;
    padding: 4px 10px;
    border-radius: 6px;
    font-size: 11px;
    cursor: pointer;
    transition: all 0.15s ease;
  }

  .btn-clear:hover {
    color: #f85149;
    border-color: #f85149;
  }

  .history-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .history-item {
    background: #161b22;
    border: 1px solid #30363d;
    border-radius: 10px;
    padding: 12px 14px;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .history-left {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .history-type {
    font-size: 14px;
    font-weight: 600;
    color: #f0f6fc;
  }

  .history-number {
    font-size: 12px;
    color: #8b949e;
    font-family: monospace;
  }

  .history-time {
    font-size: 11px;
    color: #6e7681;
  }

  .history-right {
    text-align: right;
  }

  .history-balance {
    font-size: 15px;
    font-weight: 700;
    color: #3fb950;
  }

  .empty-state {
    text-align: center;
    padding: 28px 16px;
    font-size: 13px;
    color: #8b949e;
    background: #161b22;
    border-radius: 10px;
    border: 1px dashed #30363d;
  }

  /* Modal */
  .modal-backdrop {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.7);
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 16px;
    z-index: 1000;
  }

  .modal-card {
    background: #161b22;
    border: 1px solid #30363d;
    border-radius: 14px;
    width: 100%;
    max-width: 420px;
    padding: 20px;
    box-sizing: border-box;
    box-shadow: 0 15px 35px rgba(0, 0, 0, 0.6);
  }

  .modal-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
  }

  .modal-title {
    margin: 0;
    font-size: 17px;
    font-weight: 700;
    color: #f0f6fc;
  }

  .settings-list {
    display: flex;
    flex-direction: column;
    gap: 14px;
    margin-bottom: 20px;
  }

  .setting-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    cursor: pointer;
  }

  .setting-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .setting-name {
    font-size: 14px;
    font-weight: 600;
    color: #e6edf3;
  }

  .setting-desc {
    font-size: 12px;
    color: #8b949e;
  }

  .setting-toggle {
    width: 20px;
    height: 20px;
    accent-color: #388bfd;
    cursor: pointer;
  }

  .btn-close-modal {
    width: 100%;
    background: #238636;
    color: #ffffff;
    border: none;
    padding: 10px;
    border-radius: 8px;
    font-weight: 600;
    font-size: 14px;
    cursor: pointer;
  }

  .btn-close-modal:hover {
    background: #2ea043;
  }

  /* Toast Notification */
  .toast-popup {
    position: fixed;
    bottom: 24px;
    left: 50%;
    transform: translateX(-50%);
    background: #21262d;
    color: #f0f6fc;
    border: 1px solid #30363d;
    padding: 10px 18px;
    border-radius: 20px;
    font-size: 13px;
    font-weight: 500;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
    z-index: 2000;
    white-space: nowrap;
    animation: fadeIn 0.2s ease;
  }

  @keyframes fadeIn {
    from { opacity: 0; transform: translate(-50%, 10px); }
    to { opacity: 1; transform: translate(-50%, 0); }
  }

  /* Print Stylesheet for Receipt */
  @media print {
    :global(body) {
      background: #ffffff !important;
      color: #000000 !important;
    }
    .app-header, .sim-section, .history-section, .scan-status-box, .card-actions-bar, .modal-backdrop, .toast-popup {
      display: none !important;
    }
    .virtual-card {
      background: #ffffff !important;
      color: #000000 !important;
      border: 1px solid #000000 !important;
      box-shadow: none !important;
    }
    .card-issuer, .card-balance-value, .card-number, .card-date {
      color: #000000 !important;
    }
  }
</style>
