# Mobile Build Guide

## Prerequisites

- Node.js 20+
- Expo CLI: `npm install -g expo-cli eas-cli`
- iOS: macOS + Xcode 15+ + Apple Developer Account ($99/year)
- Android: Android Studio + Google Play Console ($25 one-time)

## Local Development (Expo Go)

```bash
cd mobile/HealthLife
npm install
npx expo start
```

> **Note:** HealthKit and Health Connect are NOT available in Expo Go.
> Use a development build for native health data access.

## Development Build (with HealthKit/Health Connect)

```bash
# Install EAS CLI
npm install -g eas-cli

# Login to Expo
eas login

# Configure project (first time only)
eas build:configure

# Build for iOS simulator
eas build --platform ios --profile development

# Build for Android emulator
eas build --platform android --profile development
```

## Production Build

### iOS (App Store)

```bash
# Build production IPA
eas build --platform ios --profile production

# Submit to App Store Connect
eas submit --platform ios
```

**Before submitting:**
1. Create app in App Store Connect (https://appstoreconnect.apple.com)
2. Set `bundleIdentifier` in app.json: `com.healthlife.app`
3. Add HealthKit capability in Apple Developer Console
4. Replace `REPLACE_WITH_EAS_PROJECT_ID` in app.json with your EAS project ID
5. Configure signing certificates via `eas credentials`

### Android (Google Play)

```bash
# Build production AAB
eas build --platform android --profile production

# Submit to Google Play
eas submit --platform android
```

**Before submitting:**
1. Create app in Google Play Console (https://play.google.com/console)
2. Set `package` in app.json: `com.healthlife.app`
3. Configure signing keystore via `eas credentials`

## eas.json Configuration

Create `eas.json` in `mobile/HealthLife/`:

```json
{
  "cli": { "version": ">= 5.0.0" },
  "build": {
    "development": {
      "developmentClient": true,
      "distribution": "internal"
    },
    "preview": {
      "distribution": "internal"
    },
    "production": {
      "autoIncrement": true
    }
  },
  "submit": {
    "production": {}
  }
}
```

## Environment Variables for Production Build

Set in EAS secrets (https://expo.dev/accounts/[account]/projects/[project]/secrets):

```
API_BASE_URL=https://api.healthlife.com
```

Or use `app.config.js` for dynamic config:

```js
export default {
  expo: {
    extra: {
      apiBaseUrl: process.env.API_BASE_URL ?? 'https://api.healthlife.com',
    },
  },
};
```

## HealthKit Setup (iOS)

1. In Apple Developer Console → Certificates, IDs & Profiles → Identifiers
2. Select your App ID → Capabilities → Enable HealthKit
3. The `NSHealthShareUsageDescription` and `NSHealthUpdateUsageDescription`
   strings in `app.json` will appear in the iOS permission dialog

## Health Connect Setup (Android)

1. Health Connect is built into Android 14+ and available as an app on Android 9-13
2. The permissions in `app.json` → `android.permissions` are declared automatically
3. Users must grant permissions in the Health Connect app settings
