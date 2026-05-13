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

Run **`npm install` before** `typecheck` / `test` if `node_modules` was deleted or this is a fresh clone.

## Do not run this by mistake

- **`npx tsc`** from **`mobile/`** (without a local TypeScript install) can prompt npm to install the wrong package named `tsc` on the registry — that is **not** the TypeScript compiler.
- Use **`npm run typecheck`** from this folder, or **`cd HealthLife`** and then **`npx tsc --noEmit`** (TypeScript is a devDependency of the app).
- CI-style tests: `npm test -- --ci --coverage=false --no-watchman`

## Security notes (`npm audit`)

After `npm install`, **`npm audit`** often reports **~15** issues in **transitive** packages (not your app code). Typical sources in this project:

| Area | Risk context |
|------|----------------|
| **`jest-expo` → `jsdom` → `http-proxy-agent`** | **Dev / CI only** (test runner). Not shipped in the production app binary. |
| **`expo` / `@expo/cli` → `postcss`, `tar`, `cacache`** | **Build / Metro** tooling. Vulnerabilities usually assume untrusted input to those tools (e.g. malicious `node_modules`), not normal Expo usage. |
| **`react-native-health` → `@expo/plist` → `@xmldom/xmldom`** | Native module’s **old nested** Expo plist stack. Mitigation long-term: upgrade or replace the library when upstream publishes a fixed chain. |

**Do not run `npm audit fix --force` here.** npm will try to **downgrade** to incompatible versions (e.g. **`expo@49`**, **`jest-expo@47`**, **`react-native-health@1.13`**) and **break** SDK 52 / your tests.

Safe workflow:

1. Run **`npm audit`** to read advisories.
2. Prefer **SDK upgrades** (`expo upgrade` when you plan a release) and **library updates** over blind `audit fix`.
3. Keep **`npm test`** and **`npm run typecheck`** green after any dependency change.

This app uses **`.npmrc`** with **`legacy-peer-deps=true`** so `npm install` matches typical Expo/RN installs and avoids strict peer-resolution noise (e.g. **`jest-expo`** vs **`react`**). That does not hide security issues; it only changes how peers are resolved at install time.
