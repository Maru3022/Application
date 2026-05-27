import { DefaultTheme } from 'react-native-paper';
import { Platform } from 'react-native';

/**
 * API base URL resolution:
 *
 * 1. EXPO_PUBLIC_API_URL env var — set this when running with Ngrok or a real server:
 *      EXPO_PUBLIC_API_URL=https://xxxx.ngrok-free.app npx expo start --tunnel
 *
 * 2. Android emulator — 10.0.2.2 maps to host machine's localhost
 *
 * 3. iOS simulator / physical device on same Wi-Fi — use your machine's LAN IP.
 *    Find it with: ipconfig (Windows) or ifconfig (Mac/Linux)
 *    Example: http://192.168.1.100:8080
 *
 * For physical iPhone via Ngrok tunnel, always set EXPO_PUBLIC_API_URL.
 */
function resolveApiUrl(): string {
  // Highest priority: explicit env var (works for Ngrok, staging, production)
  if (process.env.EXPO_PUBLIC_API_URL) {
    return process.env.EXPO_PUBLIC_API_URL;
  }

  if (!__DEV__) {
    return 'https://api.healthlife.com';
  }

  // Development fallbacks
  if (Platform.OS === 'android') {
    // Android emulator: 10.0.2.2 → host machine localhost
    return 'http://10.0.2.2:8080';
  }

  // iOS simulator or physical device — replace with your machine's LAN IP
  // Run `ipconfig` on Windows to find it (look for IPv4 Address)
  return 'http://localhost:8080';
}

export const API_BASE_URL = resolveApiUrl();

export const THEME = {
  ...DefaultTheme,
  colors: {
    ...DefaultTheme.colors,
    primary: '#4CAF50',
    accent: '#8BC34A',
    background: '#F5F5F5',
    surface: '#FFFFFF',
    error: '#F44336',
  },
};

export const STORAGE_KEYS = {
  ACCESS_TOKEN: 'access_token',
  REFRESH_TOKEN: 'refresh_token',
  USER_ID: 'user_id',
  ONBOARDED: 'onboarded',
};
