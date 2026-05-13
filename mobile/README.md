# HealthLife mobile

The React Native / Expo app is in **`HealthLife/`**. You can work from either directory.

## From `mobile/` (this folder)

```powershell
npm install
npm run typecheck
npm test
npm start
```

`npm install` here runs `postinstall`, which installs dependencies inside **`HealthLife`**.

## From `mobile/HealthLife` (recommended for Expo)

```powershell
cd HealthLife
npm install
npm run typecheck
npx tsc --noEmit
npm test
npx expo start
```

## Do not run this by mistake

- **`npx tsc`** from **`mobile/`** (without a local TypeScript install) can prompt npm to install the wrong package named `tsc` on the registry — that is **not** the TypeScript compiler.
- Use **`npm run typecheck`** from this folder, or **`cd HealthLife`** and then **`npx tsc --noEmit`** (TypeScript is a devDependency of the app).
- CI-style tests: `npm test -- --ci --coverage=false --no-watchman`

## Security notes (`npm audit`)

Expo and React Native pull large dependency trees; some advisories are transitive and not fixable without upstream releases. Prefer **`npm audit`** for context; avoid **`npm audit fix --force`** unless you understand the breaking upgrades.

This app uses **`.npmrc`** with **`legacy-peer-deps=true`** so `npm install` matches typical Expo/RN installs and avoids noisy strict peer conflicts (for example between **`jest-expo`** nested tooling and **`react`**). If **`npm test`** passes, those peer warnings are safe to ignore.
