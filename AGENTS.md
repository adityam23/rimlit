# Repository Guidelines

> This project is focused on reusability and modularity of highly secure code. Simple, succinct code is better than complicated spaghetti code that has five different helpers for five different things that all kind of do the same thing. Always prefer simple, modular, and highly secure code. Do not assume you know the latest best practices — always confirm, when implementing a core feature that needs security (e.g. auth), the latest OWASP and other recommendations. Iterating to the correct solution beats overthinking in an attempt to one-shot.

## Working Agreement

- **Ask before assuming major decisions.** If any part of a task is ambiguous, ask the user to clarify before proceeding. Never assume a major decision — always confirm first.
- **Record decisions before implementing.** Read `TIMELINE.md` before designing a change, and record any new non-obvious decision there, with its reason, before starting the implementation. Update or prune entries when a decision changes or the maintainer asks.

## Project Overview

Rimlit (`app.rimlit`) — an Android app that lights up the screen edges when any app posts a notification, Samsung-style, on all devices. Material 3 + Jetpack Compose, Kotlin. Sideloaded only (never published on Play). Feature-complete: notification listener, on-screen overlay glow, AOD-style lock-screen glow, three animated styles, solid/gradient/rainbow/auto palettes, per-app palettes, tuning sliders and instant previews. Every glow's lifecycle runs on one exhaustively model-checked state machine in `engine`, with palette extraction off the main thread. Maintainers keep a gitignored `HANDOFF.md` with the architecture, contracts, gotchas and open questions. It may be absent in a fresh clone; never commit it.

## Architecture & Data Flow

Single Gradle module `:app`. Packages under `app.rimlit`: `model` → `data` → `engine` → `notify` → `overlay` → `runtime` → `ui/glow` → `ui/components` → `ui` (+ `ui/theme`), plus `permissions`. No DI framework; `navigation-compose` for the four screens. The full data-flow diagram lives in `HANDOFF.md`.

State flows one way: repository emits `Flow<GlowSettings>` → ViewModel `stateIn(...)` → UI `collectAsStateWithLifecycle()`. Every glow's lifecycle goes through one `GlowCoordinator`, owned by `RimlitApp`: the listener, the screen receiver, the accessibility service and the previews send it events, and the overlay windows and the lock-screen activity draw `coordinator.state.frameFor(surface)`. Every glow — notification, overlay, lock screen, preview — renders from one `GlowSpec` built by `GlowSettings.toGlowSpec(palette)`, with the palette resolved by `GlowSettings.paletteFor(packageName)`.

## Key Directories

- `app/src/main/java/app/rimlit/` — `RimlitApp` (owns the coordinator; `Context.glowCoordinator`), `MainActivity`, `MainViewModel`, `GlowListenerService` (builds `Post`s and forwards events), `LockScreenGlowActivity` (the AOD lock-screen surface)
- `.../model/` — `GlowSettings.kt` (settings, enums, `Ranges`, `paletteFor`, `toGlowSpec`, `previewSpec`), `GlowSpec.kt` (render input, `GlowPalette`)
- `.../engine/` — the notification → glow state machine: `GlowMachine.kt` (pure `GlowState.on(event)` → next state + effects; states, events, effects, executor contracts, transition table), `Transition.kt` (the reducer), `Route.kt` (the one placement policy), `Post.kt` (notification facts and the filter), `GlowCoordinator.kt` (serial queue gated on settings, environment sampling), `GlowExecutor.kt` + `SurfaceSlot.kt` + `LatestHandle.kt` (run effects and hold the executor contracts), `GlowDriver.kt` (the platform port)
- `.../data/` — `SettingsRepository.kt` (DataStore, single `update {}`), `AppListRepository.kt`
- `.../notify/` — `GlowNotifications.kt` (channels, test notification, full-screen-intent trampoline), `NotificationColorExtractor.kt` (1–3 color palette from notification imagery)
- `.../runtime/` — `AndroidGlowDriver.kt` (the `GlowDriver`: system state, surfaces, main-thread timers, palette extraction by key on one `Dispatchers.IO` lane), `ScreenStateReceiver.kt` (screen on/off, unlock)
- `.../overlay/` — `EdgeOverlayController.kt` (opens the overlay window a surface draws in, app or accessibility type), `GlowAccessibilityService.kt` (optional, inert: hosts the accessibility overlay that draws above the lock screen)
- `.../ui/glow/` — `EdgeGlow` (renderer), `GlowHost` (draws a surface's frame, animating its fades), `GlowGeometry`, `GlowLayout`, `BloomMask`, `GlowColors`, `WindowCutout`
- `.../ui/components/` — `SettingsComponents.kt` (scaffold, list, `SettingCard`, `SwitchCard`, `SettingIcon`, `CommitSlider`, `SettingSlider`, `PreviewButtons`), `PaletteEditor.kt` (+ `paletteBrush`)
- `.../ui/` — `MainScreen`, `StyleScreen`, `AppsScreen`, `BehaviorScreen`, `AppNavHost` (nav); `.../ui/theme/`
- `.../permissions/` — `Permissions.kt` (`PermissionKind` with its grant check and settings intent; `Permissions.statuses`/`open`)
- `app/src/main/res/` — `values/` (strings, themes, launcher background), `values-night/` (dark window theme), `drawable/` + `mipmap-anydpi/` (adaptive icon), `xml/data_extraction_rules.xml`, `xml/glow_accessibility_service.xml`
- `gradle/` — `libs.versions.toml` (version catalog)

## Development Commands

```bash
gradle :app:assembleDebug                 # build debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
gradle :app:testDebugUnitTest            # unit tests, including the state machine's exhaustive model check (~15 s on 16 cores, 1 GB test heap)
gradle :app:lintDebug                     # Android Lint
gradle :app:detektMain                    # detekt: smells, Compose rules, official-style formatting
gradle :app:detektMain -PdetektAutoCorrect  # fix formatting findings in place
gradle :app:assembleRelease               # minified release APK (unsigned unless RELEASE_* env vars are set)
gradle clean                              # clean
```

No wrapper — `gradle` is the entrypoint (system Gradle 9.8.1). Unit tests run with `gradle :app:testDebugUnitTest` (see Testing & QA). CI (`.github/workflows/ci.yml`) runs the same checks on every PR and push, and publishes a signed release to GitHub Releases on merge to `main`; the secret names are in the workflow, and the full signing setup is in `docs/RELEASING.md`.

## Code Conventions & Common Patterns

- **Style**: Kotlin official code style (`kotlin.code.style=official`). One responsibility per file.
- **State management**: ViewModel exposes `StateFlow`; long-lived settings via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue)`; transient state via `MutableStateFlow` + `asStateFlow()`. UI collects with `collectAsStateWithLifecycle()`.
- **Persistence**: Preferences DataStore (`preferencesDataStore(name = "settings")` delegate). Repository exposes `Flow<GlowSettings>` and one `suspend fun update(transform)` (atomic `edit {}`); never add per-field setters. Keys live in a private `object Keys`; defaults come from `GlowSettings()`, never restated literals.
- **Enum persistence**: serialize as `name`, parse with the private `toEnumOr(default)` helper — tolerate unknown values. Numbers are clamped to `GlowSettings.Ranges`.
- **Dependency injection**: none. `MainViewModel` is constructed via a companion `Factory = viewModelFactory { initializer { this[APPLICATION_KEY]... } }`.
- **UI**: Material 3. Build screens from `ui/components` (`SubScreenScaffold`, `SettingsList`, `SectionTitle`, `SettingCard`, `SwitchCard`, `SettingIcon`, `SettingSlider`/`CommitSlider`, `PreviewButtons`, `PaletteEditor`, `paletteBrush`) — do not hand-roll card rows, switch rows, sliders or palette brushes. Public composables take a `modifier: Modifier = Modifier`. All user-facing text via `stringResource(R.string.*)`. Enum→text mapping as private extensions. Editable screens render from an in-memory draft for instant previews and persist once per finished edit (tap or slider release).
- **Version gating**: runtime API checks use `Build.VERSION.SDK_INT >= Build.VERSION_CODES.X` guards — never assume the latest SDK at runtime.
- **Error handling**: safe fallbacks (see enum persistence); no silently-swallowing catch blocks elsewhere.
- **Colors**: vivid hues come from `model/Hues.kt` (`vividColor`, `hueOf`); never restate the HSL saturation/lightness.
- **Static analysis**: detekt must pass. Fix findings rather than suppressing them; a project-wide rule change goes in `config/detekt/detekt.yml` with its reason.

## Important Files

- `app/src/main/AndroidManifest.xml` — permissions (`POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `SYSTEM_ALERT_WINDOW`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and components (application `RimlitApp`, `MainActivity`, `LockScreenGlowActivity`, `GlowListenerService`, `GlowAccessibilityService` + `res/xml/glow_accessibility_service.xml`).
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
- **Compose compiler**: `org.jetbrains.kotlin.plugin.compose`, on the catalog `kotlin` version (2.4.21).
- **Android SDK**: platform 36 (`sdk.dir` in `local.properties`, gitignored). compileSdk/targetSdk 36, minSdk 29.
- Dependency resolution: `google()` + `mavenCentral()`, `FAIL_ON_PROJECT_REPOS`. Versions via the catalog, not inline.

## Testing & QA

- Local unit tests: JUnit 4 + `kotlin-test` in `app/src/test`, run with `gradle :app:testDebugUnitTest` (also in CI). No instrumented tests (`app/src/androidTest` is absent, no `testInstrumentationRunner`).
- `engine/GlowMachineModelCheckTest` explores every reachable state of the state machine over a small adversarial event alphabet (about 4.2 M states, 162 M transitions, ~15 s on 16 cores; it searches one breadth-first level at a time in parallel and stores visited states as exact packed codes, and the test JVM gets a 1 GB heap, overridable with `-PtestMaxHeap=`) and asserts the invariants listed in it on every state and transition; a violation fails with the shortest event trace. Never trim the alphabet or fixtures to make it fit: each variant multiplies the state space, so make the search cheaper or raise the heap instead. `GlowMachineScenarioTest` pins the intended behaviour, one scenario per decision; `GlowCoordinatorTest` covers the settings gate, environment sampling and the executor contracts with a fake driver; `PostFilterTest` covers the notification filter.
- When the machine changes: keep the model check green, add a scenario for any new behaviour, and plant the bug the new invariant guards against once to see the check fail.
- Current verification: `gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:detektMain` green (lint "No issues found"), plus manual testing on an emulator or device (adb grant commands are in `HANDOFF.md`).
