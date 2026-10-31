import { registerPlugin } from '@capacitor/core';

export interface IsoDepEvent {
  id: number[];
  techTypes: string[];
}

export interface IsoDepPlugin {
  isSupported(): Promise<{ supported: boolean; enabled: boolean }>;
  startScanning(): Promise<void>;
  stopScanning(): Promise<void>;
  transceive(options: { data: number[]; timeout?: number }): Promise<{ response: number[] }>;
  closeTag(): Promise<void>;
  addListener(eventName: 'cardDiscovered', listener: (event: IsoDepEvent) => void): Promise<{ remove: () => Promise<void> }>;
}

export const IsoDepNfc = registerPlugin<IsoDepPlugin>('IsoDepNfc');
