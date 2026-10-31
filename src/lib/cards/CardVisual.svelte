<script lang="ts">
  import type { CardBalanceResult } from '../types';
  import type { CardThemeMetadata } from './card-assets';
  import { formatDisplayBalance, formatDisplayCardNumber } from '../apdu/card-formatter';

  interface Props {
    card: CardBalanceResult | null;
    activeTheme: CardThemeMetadata;
    hideBalance: boolean;
    maskCardNumber: boolean;
    onToggleBalance?: () => void;
    onToggleMask?: () => void;
    onCopyCardNumber?: (num: string) => void;
  }

  let {
    card,
    activeTheme,
    hideBalance,
    maskCardNumber,
    onToggleBalance,
    onToggleMask,
    onCopyCardNumber
  }: Props = $props();
</script>

<div
  class="card-visual {card ? card.issuer.toLowerCase() : 'card-visual--empty'}"
  onclick={() => card && onCopyCardNumber?.(card.cardNumber)}
  role="button"
  tabindex="0"
  onkeydown={(e) => e.key === 'Enter' && card && onCopyCardNumber?.(card.cardNumber)}
  title={card ? 'Ketuk untuk menyalin nomor kartu' : undefined}
>
  {#if activeTheme?.bgImage}
    <img
      class="card-bg-fullbleed"
      src={activeTheme.bgImage}
      alt=""
      aria-hidden="true"
      loading="eager"
    />
  {/if}

  <div class="card-contrast-scrim" aria-hidden="true"></div>

  <!-- Top Row -->
  <div class="card-layer card-header-row">
    <div class="card-brand-badge">
      {#if activeTheme?.logoImage && card}
        <img class="card-brand-logo" src={activeTheme.logoImage} alt={card.issuer} />
      {:else}
        <span class="card-brand-text">{card ? card.cardType : 'UANG ELEKTRONIK'}</span>
      {/if}
    </div>

    <div class="card-contactless-wave" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
        <path d="M8.5 16.5a5 5 0 0 1 0-7" />
        <path d="M12 19a8.5 8.5 0 0 1 0-14" />
        <path d="M15.5 21.5a12 12 0 0 1 0-19" />
      </svg>
    </div>
  </div>

  <!-- Middle Row: Chip & Balance -->
  <div class="card-layer card-middle-row">
    <div class="emv-chip" aria-hidden="true">
      <div class="emv-chip-lines"></div>
      <div class="emv-chip-core"></div>
    </div>

    <div class="card-balance-box">
      <div class="balance-label-row">
        <span class="balance-tag">Saldo Tersedia</span>
        <button
          type="button"
          class="neo-eye-btn"
          onclick={(e) => { e.stopPropagation(); onToggleBalance?.(); }}
          title={hideBalance ? 'Tampilkan saldo' : 'Sembunyikan saldo'}
          aria-label={hideBalance ? 'Tampilkan saldo' : 'Sembunyikan saldo'}
        >
          {#if hideBalance}
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
              <line x1="1" y1="1" x2="23" y2="23" />
            </svg>
          {:else}
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
          {/if}
        </button>
      </div>
      <div class="balance-digits">
        {card ? formatDisplayBalance(card.balance, hideBalance) : 'Rp 0'}
      </div>
    </div>
  </div>

  <!-- Bottom Row: PAN & Time -->
  <div class="card-layer card-footer-row">
    <div class="pan-box">
      <div class="pan-label-row">
        <span class="pan-tag">Nomor Kartu</span>
        <button
          type="button"
          class="neo-eye-btn"
          onclick={(e) => { e.stopPropagation(); onToggleMask?.(); }}
          title={maskCardNumber ? 'Tampilkan nomor kartu' : 'Sensor nomor kartu'}
          aria-label={maskCardNumber ? 'Tampilkan nomor kartu' : 'Sensor nomor kartu'}
        >
          {#if maskCardNumber}
            <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
              <line x1="1" y1="1" x2="23" y2="23" />
            </svg>
          {:else}
            <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
          {/if}
        </button>
      </div>
      <div class="pan-digits">
        {card ? formatDisplayCardNumber(card.cardNumber, maskCardNumber) : '•••• •••• •••• ••••'}
      </div>
    </div>

    {#if card}
      <div class="timestamp-box">
        <span class="timestamp-tag">Waktu Tap</span>
        <span class="timestamp-value">
          {new Date(card.timestamp).toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' })}
        </span>
      </div>
    {/if}
  </div>
</div>

<style>
  .card-visual {
    aspect-ratio: 1.586 / 1;
    min-height: 204px;
    width: 100%;
    max-width: 420px;
    margin: 0 auto;
    border-radius: 16px;
    padding: 18px 20px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    position: relative;
    overflow: hidden;
    cursor: pointer;
    border: 3px solid #000000;
    box-shadow: 5px 5px 0px #000000;
    transition: transform 0.15s ease, box-shadow 0.15s ease;
    user-select: none;
    background-color: #1e293b;
  }

  :global(.theme-dark) .card-visual {
    border-color: #000000;
    box-shadow: 5px 5px 0px #ffffff;
  }

  .card-visual:active {
    transform: translate(2px, 2px);
    box-shadow: 2px 2px 0px #000000;
  }

  :global(.theme-dark) .card-visual:active {
    box-shadow: 2px 2px 0px #ffffff;
  }

  /* Full-bleed background art */
  .card-bg-fullbleed {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    z-index: 0;
    pointer-events: none;
  }

  /* High contrast gradient scrim for text legibility */
  .card-contrast-scrim {
    position: absolute;
    inset: 0;
    background: linear-gradient(
      180deg,
      rgba(0, 0, 0, 0.42) 0%,
      rgba(0, 0, 0, 0.15) 35%,
      rgba(0, 0, 0, 0.35) 70%,
      rgba(0, 0, 0, 0.62) 100%
    );
    z-index: 1;
    pointer-events: none;
  }

  .card-visual--empty {
    background: #27272a;
    border: 3px dashed #71717a;
  }

  .card-layer {
    position: relative;
    z-index: 2;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  /* Header */
  .card-brand-badge {
    display: flex;
    align-items: center;
    max-height: 32px;
  }

  .card-brand-logo {
    max-height: 28px;
    max-width: 120px;
    object-fit: contain;
    filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.7));
  }

  .card-brand-text {
    font-size: 13px;
    font-weight: 800;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: #ffffff;
    text-shadow: 0 2px 4px rgba(0, 0, 0, 0.9), 0 0 2px #000;
  }

  .card-contactless-wave {
    color: #ffffff;
    filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.9));
    display: flex;
    align-items: center;
  }

  /* Middle */
  .card-middle-row {
    gap: 16px;
    align-items: center;
  }

  .emv-chip {
    width: 44px;
    height: 34px;
    border-radius: 6px;
    background: linear-gradient(135deg, #fde047 0%, #eab308 30%, #ca8a04 70%, #854d0e 100%);
    border: 1.5px solid #000000;
    box-shadow: 2px 2px 0px rgba(0, 0, 0, 0.5);
    position: relative;
    overflow: hidden;
    flex-shrink: 0;
  }

  .emv-chip-lines {
    position: absolute;
    inset: 0;
  }

  .emv-chip-lines::before {
    content: '';
    position: absolute;
    top: 50%;
    left: 0;
    right: 0;
    height: 1px;
    background: #713f12;
    transform: translateY(-50%);
  }

  .emv-chip-lines::after {
    content: '';
    position: absolute;
    top: 0;
    bottom: 0;
    left: 35%;
    width: 30%;
    border-left: 1px solid #713f12;
    border-right: 1px solid #713f12;
  }

  .emv-chip-core {
    position: absolute;
    top: 25%;
    bottom: 25%;
    left: 32%;
    right: 32%;
    border: 1px solid #713f12;
    border-radius: 3px;
    background: rgba(254, 240, 138, 0.35);
  }

  .card-balance-box {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 2px;
    text-align: right;
  }

  .balance-label-row,
  .pan-label-row {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .balance-tag,
  .pan-tag,
  .timestamp-tag {
    font-size: 10px;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: #ffffff;
    text-shadow: 0 1px 3px rgba(0, 0, 0, 0.95), 0 0 2px #000000;
  }

  .balance-digits {
    font-family: ui-monospace, 'Courier New', Menlo, Consolas, monospace;
    font-size: 27px;
    font-weight: 900;
    letter-spacing: -0.02em;
    color: #ffffff;
    font-variant-numeric: tabular-nums;
    text-shadow:
      2px 2px 0px #000000,
      -1px -1px 0px #000000,
      1px -1px 0px #000000,
      -1px 1px 0px #000000,
      0 3px 6px rgba(0, 0, 0, 0.9);
  }

  /* Footer */
  .card-footer-row {
    align-items: flex-end;
  }

  .pan-box {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .pan-digits {
    font-family: 'OCR A Extended', 'Courier New', Consolas, monospace, ui-monospace;
    font-size: 14.5px;
    font-weight: 800;
    letter-spacing: 0.16em;
    color: #ffffff;
    text-shadow:
      1.5px 1.5px 0px #000000,
      -1px -1px 0px #000000,
      0 2px 4px rgba(0, 0, 0, 0.95);
    user-select: all;
  }

  .timestamp-box {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 2px;
  }

  .timestamp-value {
    font-family: ui-monospace, 'Courier New', Menlo, monospace;
    font-size: 12px;
    font-weight: 700;
    color: #ffffff;
    text-shadow: 0 1px 3px rgba(0, 0, 0, 0.95), 0 0 2px #000;
  }

  /* Neobrutalist eye toggle button */
  .neo-eye-btn {
    background: #000000;
    border: 1.5px solid #ffffff;
    color: #ffffff;
    cursor: pointer;
    border-radius: 4px;
    padding: 2px 4px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
    box-shadow: 1.5px 1.5px 0px #ffffff;
    transition: transform 0.1s ease;
  }

  .neo-eye-btn:active {
    transform: translate(1px, 1px);
    box-shadow: 0.5px 0.5px 0px #ffffff;
  }
</style>
