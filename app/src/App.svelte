<script lang="ts">
  import { onMount } from 'svelte';
  import { Capacitor } from '@capacitor/core';
  import { CapacitorNfc } from '@capgo/capacitor-nfc';
  import {
    checkNfcStatus,
    formatRupiah,
    identifyCardFromTag
  } from './lib/nfc';
  import { syncTapHistory, fetchCardDefinitions } from './lib/api';
  import type { CardBalanceResult, BackendCardDefinition } from './lib/types';

  let nfcSupported = $state(false);
  let nfcEnabled = $state(false);
  let isScanning = $state(false);
  let currentCard = $state<CardBalanceResult | null>(null);
  let scanHistory = $state<CardBalanceResult[]>([]);
  let cardDefinitions = $state<BackendCardDefinition[]>([]);
  let statusMessage = $state('Tempelkan kartu di belakang ponsel');

  async function initNfc() {
    const status = await checkNfcStatus();
    nfcSupported = status.supported;
    nfcEnabled = status.enabled;

    if (Capacitor.isNativePlatform() && nfcEnabled) {
      try {
        await CapacitorNfc.addListener('tagDiscovered', async (event) => {
          const card = identifyCardFromTag(event);
          currentCard = card;
          scanHistory = [card, ...scanHistory.slice(0, 9)];
          statusMessage = `Kartu terbaca: ${card.cardType}`;
          await syncTapHistory(card, 'GT_CILILITAN_1');
        });

        await CapacitorNfc.startScanning();
        isScanning = true;
      } catch (err) {
        statusMessage = 'Gagal memulai pemindai NFC';
      }
    } else if (!Capacitor.isNativePlatform()) {
      statusMessage = 'Mode Browser Dev: Gunakan tombol simulasi di bawah';
    }
  }

  function simulateCardTap(type: 'Mandiri' | 'BCA' | 'BRI' | 'BNI' | 'KCI') {
    const mockMap = {
      Mandiri: { type: 'Mandiri e-Money', num: '6032 **** **** 1290', bal: 87500, issuer: 'Mandiri' as const },
      BCA: { type: 'BCA Flazz', num: '1809 **** **** 8821', bal: 142000, issuer: 'BCA' as const },
      BRI: { type: 'BRI Brizzi', num: '5018 **** **** 4410', bal: 26000, issuer: 'BRI' as const },
      BNI: { type: 'BNI TapCash', num: '7546 **** **** 9012', bal: 53500, issuer: 'BNI' as const },
      KCI: { type: 'KMT Commuter Line', num: '1002 **** **** 3341', bal: 18000, issuer: 'KCI' as const }
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
    scanHistory = [card, ...scanHistory.slice(0, 9)];
    statusMessage = `Simulasi sukses: ${card.cardType}`;
    syncTapHistory(card, 'GT_CILILITAN_1');
  }

  onMount(async () => {
    await initNfc();
    cardDefinitions = await fetchCardDefinitions();
  });
</script>

<main class="container">
  <header class="app-header">
    <div class="header-content">
      <h1 class="brand-title">TapMoney Info</h1>
      <span class="badge {nfcEnabled || !Capacitor.isNativePlatform() ? 'badge-ok' : 'badge-warn'}">
        {Capacitor.isNativePlatform() ? (nfcEnabled ? 'NFC Aktif' : 'NFC Mati') : 'Dev Web'}
      </span>
    </div>
    <p class="brand-subtitle">Cek saldo kartu e-Money, Flazz, Brizzi, TapCash & KMT</p>
  </header>

  <section class="card-display-section">
    <div class="virtual-card {currentCard ? currentCard.issuer.toLowerCase() : 'idle'}">
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
        <span class="card-number">{currentCard ? currentCard.cardNumber : '---- ---- ---- ----'}</span>
        <span class="card-date">{currentCard ? new Date(currentCard.timestamp).toLocaleTimeString('id-ID') : '--:--'}</span>
      </div>
    </div>

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
      </div>
    </section>
  {/if}

  <section class="history-section">
    <h2 class="section-title">Riwayat Tap Terbaru</h2>
    {#if scanHistory.length === 0}
      <div class="empty-state">Belum ada riwayat kartu yang dipindai.</div>
    {:else}
      <div class="history-list">
        {#each scanHistory as item (item.timestamp)}
          <div class="history-item">
            <div class="history-left">
              <span class="history-type">{item.cardType}</span>
              <span class="history-number">{item.cardNumber}</span>
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
</style>
