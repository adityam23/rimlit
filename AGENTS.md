# Repository Guidelines

> This project is focused on reusability and modularity of highly secure code. Simple, succinct code is better than complicated spaghetti code that has five different helpers for five different things that all kind of do the same thing. Always prefer simple, modular, and highly secure code. Do not assume you know the latest best practices — always confirm, when implementing a core feature that needs security (e.g. auth), the latest OWASP and other recommendations. Iterating to the correct solution beats overthinking in an attempt to one-shot.

## Working Agreement

- **Ask before assuming major decisions.** If any part of a task is ambiguous, ask the user to clarify before proceeding. Never assume a major decision — always confirm first.
- **Record decisions before implementing.** Read `TIMELINE.md` before designing a change, and record any new non-obvious decision there, with its reason, before starting the implementation. Update or prune entries when a decision changes or the maintainer asks.

## Project Overview

Rimlit (`app.rimlit`) — an Android app that lights up the screen edges when any app posts a notification, Samsung-style, on all devices. Material 3 + Jetpack Compose, Kotlin. Sideloaded only (never published on Play). Feature-complete: notification listener, on-screen overlay glow, AOD-style lock-screen glow, three animated styles, solid/gradient/rainbow/auto palettes, per-app palettes, tuning sliders and instant previews. Maintainers keep a gitignored `HANDOFF.md` with the architecture, contracts, gotchas and roadmap (next: a formally specified notification/glow state machine). It may be absent in a fresh clone; never commit it.

## Architecture & Data Flow

Single Gradle module `:app`. Packages under `app.rimlit`: `model` → `data` → `notify` → `overlay` → `ui/glow` → `ui/components` → `ui` (+ `ui/theme`), plus `permissions`. No DI framework; `navigation-compose` for the four screens. The full data-flow diagram lives in `HANDOFF.md`.

State flows one way: repository emits `Flow<GlowSettings>` → ViewModel `stateIn(...)` → UI `collectAsStateWithLifecycle()`. Every glow — notification, overlay, lock screen, preview — renders from one `GlowSpec` built by `GlowSettings.toGlowSpec(...)`.

## Key Directories

- `app/src/main/java/app/rimlit/` — `MainActivity`, `MainViewModel` (+ `previewSpec()`), `GlowListenerService`, `LockScreenGlowActivity` (AOD lock-screen glow)
- `.../model/` — `GlowSettings.kt` (settings, enums, `Ranges`, `toGlowSpec`), `GlowSpec.kt` (parcelable render input, `GlowPalette`)
- `.../data/` — `SettingsRepository.kt` (DataStore, single `update {}`), `AppListRepository.kt`
- `.../notify/` — `GlowNotifications.kt` (channels, test notification, full-screen-intent trampoline), `NotificationColorExtractor.kt` (1–3 color palette from notification imagery)
- `.../overlay/` — `EdgeOverlayController.kt` (overlay window, app or accessibility type), `GlowAccessibilityService.kt` (optional, inert: hosts the accessibility overlay that draws above the lock screen)
- `.../ui/glow/` — `EdgeGlow` (renderer), `GlowHost` (fade/hold timeline), `GlowGeometry`, `GlowLayout`, `BloomMask`, `GlowColors`, `WindowCutout`
- `.../ui/components/` — `SettingsComponents.kt` (scaffold, list, `SettingCard`, `SettingIcon`, `CommitSlider`, `SettingSlider`, `PreviewButtons`), `PaletteEditor.kt` (+ `paletteBrush`)
- `.../ui/` — `MainScreen`, `StyleScreen`, `AppsScreen`, `BehaviorScreen`, `AppNavHost` (nav); `.../ui/theme/`
- `.../permissions/` — `Permissions.kt` (`PermissionKind` with its grant check and settings intent; `Permissions.statuses`/`open`)
- `app/src/main/res/` — `values/` (strings, themes, launcher background), `drawable/` + `mipmap-anydpi/` (adaptive icon), `xml/data_extraction_rules.xml`, `xml/glow_accessibility_service.xml`
- `gradle/` — `libs.versions.toml` (version catalog)

## Development Commands

```bash
gradle :app:assembleDebug                 # build debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
gradle :app:lintDebug                     # Android Lint
gradle :app:detektMain                    # detekt: smells, Compose rules, official-style formatting
gradle :app:detektMain -PdetektAutoCorrect  # fix formatting findings in place
gradle :app:assembleRelease               # minified release APK (unsigned unless RELEASE_* env vars are set)
gradle clean                              # clean
```

No wrapper — `gradle` is the entrypoint (system Gradle 9.8.1). No test task is wired yet (see Testing & QA). CI (`.github/workflows/ci.yml`) runs the same checks on every PR and push, and publishes a signed release to GitHub Releases on merge to `main`; the secret names are in the workflow, and the full signing setup is in `docs/RELEASING.md`.

## Code Conventions & Common Patterns

- **Style**: Kotlin official code style (`kotlin.code.style=official`). One responsibility per file.
- **State management**: ViewModel exposes `StateFlow`; long-lived settings via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue)`; transient state via `MutableStateFlow` + `asStateFlow()`. UI collects with `collectAsStateWithLifecycle()`.
- **Persistence**: Preferences DataStore (`preferencesDataStore(name = "settings")` delegate). Repository exposes `Flow<GlowSettings>` and one `suspend fun update(transform)` (atomic `edit {}`); never add per-field setters. Keys live in a private `object Keys`; defaults come from `GlowSettings()`, never restated literals.
- **Enum persistence**: serialize as `name`, parse with the private `toEnumOr(default)` helper — tolerate unknown values. Numbers are clamped to `GlowSettings.Ranges`.
- **Dependency injection**: none. `MainViewModel` is constructed via a companion `Factory = viewModelFactory { initializer { this[APPLICATION_KEY]... } }`.
- **UI**: Material 3. Build screens from `ui/components` (`SubScreenScaffold`, `SettingsList`, `SectionTitle`, `SettingCard`, `SettingIcon`, `SettingSlider`/`CommitSlider`, `PreviewButtons`, `PaletteEditor`, `paletteBrush`) — do not hand-roll card rows, sliders or palette brushes. Public composables take a `modifier: Modifier = Modifier`. All user-facing text via `stringResource(R.string.*)`. Enum→text mapping as private extensions. Editable screens render from an in-memory draft for instant previews and persist once per finished edit (tap or slider release).
- **Version gating**: runtime API checks use `Build.VERSION.SDK_INT >= Build.VERSION_CODES.X` guards — never assume the latest SDK at runtime.
- **Error handling**: safe fallbacks (see enum persistence); no silently-swallowing catch blocks elsewhere.
- **Colors**: vivid hues come from `model/Hues.kt` (`vividColor`, `hueOf`); never restate the HSL saturation/lightness.
- **Static analysis**: detekt must pass. Fix findings rather than suppressing them; a project-wide rule change goes in `config/detekt/detekt.yml` with its reason.

## Important Files

- `app/src/main/AndroidManifest.xml` — permissions (`POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `SYSTEM_ALERT_WINDOW`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and components (`MainActivity`, `LockScreenGlowActivity`, `GlowListenerService`, `GlowAccessibilityService` + `res/xml/glow_accessibility_service.xml`).
- `app/src/main/java/app/rimlit/MainActivity.kt` — entry point; requests `POST_NOTIFICATIONS`; refreshes permission state in `onResume`.
- `app/build.gradle.kts` — module config (SDK levels, deps, Java 11 / Kotlin jvmTarget 11, lint config). Release signing and `versionCode` come only from `RELEASE_*` environment variables.
- `build.gradle.kts` — root; bumps KGP to the catalog `kotlin` version (2.4.21) via `buildscript classpath` (JDK 25 support).
- `gradle/libs.versions.toml` — single source of versions and aliases.
- `config/detekt/detekt.yml` — detekt overrides on top of the defaults, each with its reason.
- `settings.gradle.kts` — repos + `include(":app")`.
- `gradle.properties` — caching, parallel, configuration-cache, androidx flags.
- `app/proguard-rules.pro` — R8 rules; currently empty because AAPT2 already keeps manifest-declared components. Add rules only for code reached by reflection.
- `.github/workflows/ci.yml` — CI: lint/detekt/test/debug build on every PR and push; on `main`, a signed release APK published as a GitHub Release (full signing setup in `docs/RELEASING.md`).
- `README.md` — public overview, screenshots, install and build basics.
- `docs/` — gitignored maintainer docs, e.g. `docs/RELEASING.md` (full signing-key setup). May be absent in a fresh clone; never commit it.
- `HANDOFF.md` — gitignored maintainer notes: status, architecture, contracts, gotchas, roadmap. Read it first; never commit it or copy it into tracked files.
- `TIMELINE.md` — gitignored decision log: each non-obvious decision, dated, with its reason. Read it before designing a change and update it before implementing one; never commit it.

## Runtime & Tooling Preferences

- **JDK 25.** The build targets it (see the KGP bump below); don't add toolchain auto-provisioning.
- **Gradle 9.8.1**, system install; **no wrapper** — use `gradle`, not `./gradlew`.
- **AGP 9.4.1 with built-in Kotlin** — do NOT re-add `org.jetbrains.kotlin.android`. Kotlin support is built in; KGP is bumped to 2.4.21 (catalog `kotlin`) in the root `buildscript` block for JDK 25.
- **Compose compiler** and **Parcelize**: `org.jetbrains.kotlin.plugin.compose` / `org.jetbrains.kotlin.plugin.parcelize`, both on the catalog `kotlin` version (2.4.21).
- **Android SDK**: platform 36 (`sdk.dir` in `local.properties`, gitignored). compileSdk/targetSdk 36, minSdk 29.
- Dependency resolution: `google()` + `mavenCentral()`, `FAIL_ON_PROJECT_REPOS`. Versions via the catalog, not inline.

## Testing & QA

- No test frameworks, dependencies, or sources exist yet (`app/src/test` and `app/src/androidTest` are absent; no `testImplementation`/`androidTestImplementation`, no `testInstrumentationRunner`).
- When tests are added: local unit tests `gradle :app:testDebugUnitTest`; instrumented `gradle :app:connectedDebugAndroidTest` (requires device/emulator via adb). Neither runs anything today.
- Current verification: `gradle :app:assembleDebug :app:lintDebug :app:detektMain` green (lint "No issues found"), plus manual testing on an emulator or device (adb grant commands are in `HANDOFF.md`).
