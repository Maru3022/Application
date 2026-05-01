import { DefaultTheme } from 'react-native-paper';

export const API_BASE_URL = __DEV__
  ? 'http://10.0.2.2:8080'  // Android emulator
  : 'https://api.healthlife.com';

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
