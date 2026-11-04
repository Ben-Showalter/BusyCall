# Upstream tracking

This repo is built on [juha-h/baresip-studio](https://github.com/juha-h/baresip-studio)
(BSD-3-Clause, Copyright (c) 2018 TutPro Inc.). We keep its backend and replace its UI
(see `PLAN.md §4.1` and `AGENTS.md §0`).

## Current base

| | |
|---|---|
| Upstream repo | https://github.com/juha-h/baresip-studio |
| Upstream version | `94.1.5` (`app/build.gradle.kts` `versionName`) |
| Upstream commit | `6db813f10740ca835d8f7fbdca9c837315974a6e` ("Submodule update", 2026-11-03) |
| Native libraries | `libbaresip-android` submodule → https://github.com/juha-h/libbaresip-android (`master`, pinned at `bf8448d`). Built by `.github/workflows/native-libs.yml` and published as release `native-libs-<version>` (`native-libs.properties`); the build downloads it into `distribution/` |

## Changes to upstream files

Every edit to an upstream file is listed here: file, what changed, why, and the
upstream PR if one was offered. Keep each change as small as possible.

| File | Change | Why | Upstream PR |
|---|---|---|---|
| `app/build.gradle.kts` | `applicationId` `com.tutpro.baresip` → `com.busycall.app` | Installs next to the real baresip app instead of replacing it. `namespace` stays `com.tutpro.baresip` so upstream code compiles and merges unchanged. | No (fork-only) |
| `app/build.gradle.kts` | One `apply(from = "gradle/native-libs.gradle.kts")` line at the end | Downloads the prebuilt native libraries into `distribution/` so the app builds in Android Studio on Windows. All the logic is in our own file (`gradle/native-libs.gradle.kts`); see BUILDING.md. | No (fork-only) |
| `app/src/main/AndroidManifest.xml` | Removed MAIN/LAUNCHER from `.MainActivity`; added BusyCall's activities (`com.busycall.app.*`), with HomeActivity as the launcher | BusyCall's own screens replace upstream's UI step by step. `MainActivity` and its other intent filters (dial, call, links) stay, so calls still work through upstream's screens until ours exist (Home › Options › Classic screens). | No (fork-only) |
| `app/src/main/kotlin/com/tutpro/baresip/MainActivity.kt` | One line at the top of `onCreate` and of `onNewIntent`: `if (CallRouter.route(this, intent)) { finish(); return }`; a `routedToBusyCall` flag so `onDestroy` skips cleanup that was never set up | Upstream opens `MainActivity` for every call event (`call show`, `call answer`, `call missed`, MAIN from the status notification). `com.busycall.app.call.CallRouter` sends those to BusyCall's call screens; everything else (messages, transfers, Classic screens) is unchanged. No change to `BaresipService` or `ConnectionService`. | No (fork-only) |
| `app/build.gradle.kts` | One more `apply(from = "gradle/busycall.gradle.kts")` line | BusyCall's own build additions: JUnit for its unit tests, ZXing core for QR setup codes, and copying the setup page into `assets/` | No (fork-only) |
| `app/src/main/kotlin/com/tutpro/baresip/BaresipService.kt` | A companion map `registrationFailures` (aor → baresip's reason), set in the "registering failed" branch of `uaEvent`, cleared on "registered" and "unregistering"; 5 lines, marked `// BusyCall` | Upstream only toasts the reason, and only while its screen is open. BusyCall shows it on Home and Numbers in plain words ("Wrong extension or password"), as AGENTS §6 requires. No behaviour change. | No (could be offered) |
| `app/src/main/AndroidManifest.xml` | `uses-permission CAMERA`; `uses-feature` `camera.any` and `camera.autofocus` with `required="false"`; BusyCall's Scan, Make a setup code, Contacts and Call log activities | Scan QR setup code (PLAN §5.1). The camera stays optional, so phones without one still install. | No (fork-only) |
| `app/src/main/res/values/strings.xml` | `app_name` `baresip` → `BusyCall` | Launcher, notification and phone-account name. The other "baresip" strings credit the library and stay. Untranslatable, so no translation files change. | No (fork-only) |

## Notes

- `minSdk` stays **28**, as upstream (owner's decision, 2026-11-04). See
  `docs/SPIKES.md` S1 for what going lower would take.
- Hard-coded `com.tutpro.baresip…` strings (intent actions, notification
  channel IDs, wakelock tags) are only names, so they keep working under the
  new `applicationId`. `BaresipService.onCreate` sets an `intent` with
  `setPackage("com.tutpro.baresip")`, but that field is never broadcast, so it
  is left alone. File providers already use `${applicationId}`.

## Merging a new upstream release

1. `git fetch https://github.com/juha-h/baresip-studio master`
2. Merge into a branch, resolve conflicts (most should be in upstream UI files we
   deleted: keep them deleted).
3. Update the "Current base" table above.
4. If the `libbaresip-android` submodule or `ndkVersion` changed, bump
   `version` in `native-libs.properties` and let the workflow publish new libraries.
5. Run the S1 smoke test on a real phone before merging to `master`.
