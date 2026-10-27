import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'id.machfudin.tapmoney',
  appName: 'TapMoney Info',
  webDir: 'dist',
  server: {
    androidScheme: 'https'
  }
};

export default config;
