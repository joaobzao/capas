# In-App Rating for Capas — Design

**Date:** 2026-07-15
**Status:** Approved (design), pending implementation plan

## Overview

Let users rate the Capas app through the platforms' native review APIs, via two entry points:

- **Automatic** — after the user's **5th app launch**, request the native in-app review flow (Google Play In-App Review on Android, StoreKit `requestReview` on iOS). Fires **once, ever**.
- **Manual** — a "Rate this app" row in the About screen's Support section, on both Android and iOS.

The automatic and manual paths behave differently, per Apple/Google guidance:

| Path | Android | iOS |
| --- | --- | --- |
| Automatic | Native in-app review dialog (OS decides whether to actually show it; cannot be forced) | StoreKit `requestReview` (OS-throttled) |
| Manual (button) | Opens Play Store listing directly: `market://details?id=com.joaobzao.capas`, https fallback | App not published yet → `APP_STORE_ID` placeholder + `requestReview` fallback, with commented `apps.apple.com/app/id<ID>?action=write-review` ready to enable |

**Constraint:** iOS is not yet published to the App Store. The numeric App Store ID is unknown, so the iOS manual button uses a clearly-marked placeholder constant and a StoreKit fallback until the app is live.

## Architecture

Data flow mirrors the existing app: shared decision logic in `commonMain`, platform-specific presentation in `composeApp` (Android) and `iosApp` (iOS). The shared unit is pure and testable; only the native dialog presentation lives in the platform layers (it needs an `Activity` / `UIViewController`).

## Section 1 — Shared logic (`commonMain`)

New unit `RatingManager` (interface) + `RatingManagerImpl`, placed in `shared/src/commonMain/kotlin/com/joaobzao/capas/rating/`.

**Responsibility:** decision + persistence only. No UI, no platform APIs.

- Reuses the existing injected `com.russhwolf.settings.Settings` (already provided in both platform Koin modules).
- Persistence keys:
  - `rating_launch_count` (Int) — number of app launches counted.
  - `rating_review_requested` (Boolean) — whether the automatic review has already been requested.
- Threshold constant: `LAUNCH_THRESHOLD = 5` (private const in the class).
- API:
  - `fun registerAppOpenAndCheck(): Boolean`
    - Increments `rating_launch_count` on every call (once per app start).
    - Returns `true` **exactly once**: when the incremented count reaches `LAUNCH_THRESHOLD` **and** `rating_review_requested` is still `false`.
    - When it returns `true`, it sets `rating_review_requested = true` so it never fires again.
    - Returns `false` otherwise.

**Koin wiring:** add `single<RatingManager> { RatingManagerImpl(get()) }` to `coreModule` in `shared/src/commonMain/kotlin/com/joaobzao/capas/Koin.kt` (`get()` resolves `Settings`).

**Tests** (`shared/src/commonTest/`), using an in-memory `Settings` (e.g. `MapSettings`):

- Count increments by one per call.
- Returns `false` for launches 1–4.
- Returns `true` exactly on launch 5.
- Returns `false` on launch 6 and every subsequent launch.
- Once requested, never returns `true` again even across many further calls.

## Section 2 — Android (`composeApp`)

**Dependencies** (`gradle/libs.versions.toml` + `composeApp/build.gradle.kts`):

- `com.google.android.play:review`
- `com.google.android.play:review-ktx`

**Automatic trigger** (`MainActivity.onCreate`, after Koin is initialized):

- Obtain `RatingManager` from Koin (`get()`), call `registerAppOpenAndCheck()`.
- If `true`: run `ReviewManagerFactory.create(this).requestReviewFlow()`, then `launchReviewFlow(activity, reviewInfo)`. Fire-and-forget; never blocks UI; silently no-ops on failure.
- On success, log analytics `trackRatePromptShown()`.

**Manual button** (`AboutScreen.kt`):

- Add a "Rate this app" `ContactItem` (star icon) to the **Support** section (near "Buy a coffee").
- On tap: open `market://details?id=com.joaobzao.capas`; if no Play app is available, fall back to `https://play.google.com/store/apps/details?id=com.joaobzao.capas`.
- Log analytics `trackRateButtonClicked()`.

**Analytics** (`CapasAnalytics.kt`):

- `fun trackRatePromptShown()` — automatic in-app review flow launched.
- `fun trackRateButtonClicked()` — manual "Rate this app" row tapped.

**Localized strings** (PT / EN / ES resources): `rate_app` title + subtitle, matching the style of existing About entries.

## Section 3 — iOS (`iosApp`)

**Automatic trigger** (`iOSApp.swift` init or `ContentView.onAppear`):

- Reach `RatingManager` through the shared Koin graph, following the existing `KoinHelper` / `CapasViewModelWrapper` pattern.
- Call `registerAppOpenAndCheck()`; if `true`, call StoreKit `SKStoreReviewController.requestReview(in: scene)` (or SwiftUI `@Environment(\.requestReview)`).

**Manual button** (`AboutScreen.swift`):

- Add a "Rate this app" row in the Support section.
- Since iOS is not published: declare `let APP_STORE_ID = "TODO_APP_STORE_ID"` (clearly marked). The button calls `requestReview` for now; include a commented-out path opening `https://apps.apple.com/app/id<APP_STORE_ID>?action=write-review`, ready to enable once the app is live.

**Localized strings** (`Localization.swift`, PT / EN / ES): "Rate this app" title + subtitle matching the Android strings.

## Out of Scope (YAGNI)

- Re-prompting after a larger threshold or on a schedule (fires once, ever).
- Custom pre-prompt / "do you like the app?" gating dialog before the native flow.
- Rating analytics beyond the two events above.
- Backfilling the real iOS App Store ID (deferred until the app is published).
