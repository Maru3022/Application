# HealthLife Mobile — Build Guide

## Prerequisites
| Tool | Version | Install |
|------|---------|---------|
| Node.js | 20 LTS | https://nodejs.org |
| Expo CLI | latest | `npm i -g expo-cli` |
| EAS CLI | latest | `npm i -g eas-cli` |
| Xcode (iOS) | 15+ | Mac only — App Store |
| Android Studio | latest | https://developer.android.com/studio |

## Quick Start (Expo Go — no native build needed)

```bash
cd mobile/HealthLife
npm install
npx expo start
```

Scan the QR code with the **Expo Go** app on your phone.  
iOS and Android are both supported via Expo Go without any native build.

---

## Native Build — `npx expo prebuild`

> **Requires macOS with Xcode 15+** for the iOS native project.  
> Android prebuild works on any OS.

The `/ios` and `/android` directories are **generated artifacts** — they are excluded from source control (`.gitkeep` placeholders only) and must be created locally:

```bash
# Install dependencies first
npm install

# Generate native projects (macOS + Xcode 15+ required for iOS)
npx expo prebuild --clean

# Run on simulator / device
npx expo run:ios      # macOS only
npx expo run:android  # any OS with Android Studio
```

After prebuild the generated `/ios` and `/android` folders are fully functional native projects that can be opened in Xcode / Android Studio directly.

---

## EAS Cloud Build (recommended for CI/CD)

EAS Build runs prebuild in the cloud — **no local Mac needed for iOS**.

```bash
# One-time login
eas login

# Link to your EAS project (updates app.json → extra.eas.projectId)
eas project:init

# Build
eas build --platform ios --profile development
eas build --platform android --profile development

# Build both
eas build --platform all --profile production
```

Profiles are defined in `eas.json`:
- `development` — dev client, simulator-compatible
- `preview`     — internal distribution APK/IPA
- `production`  — App Store / Play Store release

---

## Environment Variables

| Variable | Description |
|----------|-------------|
| `API_BASE_URL` | Backend base URL (set in `src/constants/index.ts`) |
| `APP_ENV` | Injected by EAS (`development` / `staging` / `production`) |

---

## Troubleshooting

**`/ios` or `/android` folder missing**  
Run `npx expo prebuild --clean`. These folders are intentionally git-ignored.

**Xcode version error during prebuild**  
Upgrade to Xcode 15+ from the Mac App Store.

**`eas build` fails with "project not found"**  
Run `eas project:init` and update `extra.eas.projectId` in `app.json`.
