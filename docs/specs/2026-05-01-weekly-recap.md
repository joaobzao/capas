# Weekly Recap

## Context

The app today shows daily Portuguese newspaper front-page images and nothing else — there is no headline, article, or editorial content anywhere in the data model. The user wants to give readers a sense of "the week that was" without changing what the app fundamentally is.

The chosen shape is a **manually curated, text-only weekly post**: the user writes a short title + body each week as a markdown file in this repo, a small build step turns it into JSON, and the mobile app surfaces it as a banner on the existing Capas screen plus a dedicated Recap screen with the last 8 weeks of archive. Publication is on-demand (manual workflow_dispatch) and reuses the existing `updates` FCM topic to push a notification on publish.

This design intentionally adds no scraping, no LLM, no CMS, and no new push topic. It mirrors the existing Capas data flow (Rust → gh-pages JSON → KMP repository → ViewModel → screen) so it fits the codebase's current patterns.

## Decisions (locked in via brainstorm)

- **Source**: Manually authored, no scraping, no LLM.
- **Content**: Title + plain-text body. No covers, headlines, or links.
- **Authoring**: Markdown files in `recaps/` with YAML frontmatter.
- **Languages**: PT required; EN/ES optional with PT fallback at runtime.
- **Surface**: Banner card on Capas screen → new Recap screen showing latest + up to 7 archived weeks.
- **Push**: Reuse existing FCM topic `updates`; deep-link via `data.kind=recap`.
- **Trigger**: `workflow_dispatch` only.
- **History size**: Latest + last 7 (8 total) on the published JSON.

## Architecture

Three additions, each parallel to (not entangled with) the daily covers pipeline:

1. `recaps/` directory — markdown sources committed to `main`.
2. `recap-build` — second Rust binary in the existing `capas-api/` crate; converts markdown → `public/recaps.json`.
3. `.github/workflows/publish-recap.yml` — `workflow_dispatch` workflow that builds the JSON, deploys to `gh-pages`, and fires the FCM push.

The existing `update-capas.yml` and `capas.json` remain untouched. Recap data lives at a separate URL (`https://joaobzao.github.io/capas/recaps.json`).

## Authoring format

One markdown file per language per week, named `recaps/YYYY-Www.<lang>.md`:

```
recaps/2026-W18.pt.md   ← required
recaps/2026-W18.en.md   ← optional
recaps/2026-W18.es.md   ← optional
```

Frontmatter + plain-text body (no markdown rendered on device — paragraph breaks only):

```markdown
---
id: 2026-W18
publishedAt: 2026-05-03T18:00:00+01:00
lang: pt
title: A semana em manchetes
---

Body text. One or more paragraphs. Plain text — no bold, no links.
```

`recap-build` enforces: every `id` must have a `pt` file; if not, the build fails fast.

## Published JSON shape

`gh-pages/recaps.json`:

```json
{
  "recaps": [
    {
      "id": "2026-W18",
      "publishedAt": "2026-05-03T18:00:00+01:00",
      "translations": {
        "pt": { "title": "...", "body": "..." },
        "en": { "title": "...", "body": "..." }
      }
    }
  ]
}
```

Sorted by `publishedAt` descending; capped at 8 entries.

## Files to create / modify

### Rust (`capas-api/`)

- `capas-api/Cargo.toml` — declare a second `[[bin]]` for `recap-build`; add `gray_matter` (frontmatter parser) and `chrono` (date parsing/sorting).
- `capas-api/src/bin/recap_build.rs` — new binary. Reads `recaps/*.md` from repo root, parses frontmatter, groups by `id`, sorts, takes top 8, writes `capas-api/public/recaps.json`. Reuses `serde`/`serde_json` already in the crate.
- (Existing `capas-api/src/main.rs` is unchanged.)

### Workflow

- `.github/workflows/publish-recap.yml` — new file. `on: workflow_dispatch`.
  - Steps: checkout → `cargo run --release --bin recap-build` (cwd `capas-api/`) → deploy `recaps.json` only into the `gh-pages` branch (mirror the deploy step in `update-capas.yml` lines 68–75 but copy a single file instead of the whole `public/`) → read PT title from the built JSON → send FCM to topic `updates` with payload `{ title: <pt title>, body: "Resumo da semana", data: { kind: "recap" } }` (mirrors `update-capas.yml` lines 83–100).
  - Reuse the same FCM secrets/Project ID logic already in `update-capas.yml`.

### Shared KMP (`capas-app/shared/src/commonMain/kotlin/com/joaobzao/capas/recap/`)

All new files:

- `Recap.kt` — `Recap`, `RecapTranslation`, `RecapsResponse` (`@Serializable`).
- `RecapApi.kt` — Ktor client fetching `recaps.json`. Reuse the same `HttpClient` config pattern as `CapasApi` (20s timeout, JSON content negotiation).
- `RecapRepository.kt` (interface + `RecapRepositoryImpl`) — fetch + return `latest` and `archive`. Resolves a `Recap` for the device language: prefer `translations[lang]`, fall back to `translations["pt"]`. Mirror style of `CapasRepository.kt` / `CapasRepositoryImpl.kt`.
- `RecapViewState.kt` — `latest: Recap?`, `archive: List<Recap>`, `loading: Boolean`, `error: String?`. Mirrors `CapasViewState`.
- `RecapViewModel.kt` (`expect`) + `androidMain` / `iosMain` actuals — same `expect/actual` pattern as `CapasViewModel.kt`.

Koin wiring:

- `shared/src/commonMain/kotlin/com/joaobzao/capas/di/Koin.kt` (or wherever the shared module declarations live) — register `RecapApi`, `RecapRepository`, `RecapViewModel`.
- `shared/src/androidMain/kotlin/.../Koin.android.kt` and `iosMain/kotlin/.../KoinHelper.kt` — platform actuals if any (likely none beyond what Capas already does).

Reuse:

- Ktor client engine config (OkHttp on Android, Darwin on iOS) — already configured for `CapasApi`.
- `multiplatform-settings` — for `last_seen_recap_id` (banner dismissal). Same `Settings` instance already injected via Koin.
- `RelativeDateFormatter.formatRelativeDate()` in `shared/src/commonMain/kotlin/com/joaobzao/capas/capas/DateFormatter.kt` — reusable for showing "há 2 dias" / "2 days ago" on each archived recap.

### Android (`capas-app/composeApp/`)

- `composeApp/src/androidMain/kotlin/com/joaobzao/capas/recap/RecapScreen.kt` — new composable. Latest recap (title + body) at top; below, a list of up to 7 archived recaps, each row expandable inline or opening the same screen with that recap as latest. Uses Material3 styles consistent with `CapasScreen`.
- `composeApp/src/androidMain/kotlin/com/joaobzao/capas/navigation/CapasNavHost.kt` — add a `recap` route alongside `welcome`, `capas`, `detail/{id}` (current routes at lines 61–64).
- `composeApp/src/androidMain/kotlin/com/joaobzao/capas/CapasScreen.kt` (or wherever the Capas screen is composed) — add a small banner card at the top showing the latest recap title, dismissible per-recap-id via `multiplatform-settings` key `last_seen_recap_id`. Hidden if no recap, or if the user already dismissed the current one. Tap → `navController.navigate("recap")`.
- `composeApp/src/androidMain/kotlin/com/joaobzao/capas/MyFirebaseMessagingService.kt` — extend `onMessageReceived` (currently around line 24): if `remoteMessage.data["kind"] == "recap"`, build the tap-Intent with deep-link to the `recap` route. Otherwise unchanged.
- `composeApp/src/androidMain/res/values/strings.xml` (PT, default) — add: `recap_title`, `recap_banner_label`, `recap_empty`, `recap_archive_header`.
- `composeApp/src/androidMain/res/values-en/strings.xml` — same keys in English. (Add `values-es/` if not already present and you want Spanish parity on Android — currently only PT/EN exist; the brainstorm answered "PT, with optional EN/ES" so Spanish is acceptable to leave for later.)

### iOS (`capas-app/iosApp/`)

- `iosApp/iosApp/RecapViewModelWrapper.swift` — mirror `CapasViewModelWrapper.swift`. Wraps the Kotlin `RecapViewModel`, uses the existing `Collector<T>` to bridge `StateFlow` → `@Published`.
- `iosApp/iosApp/RecapScreen.swift` — SwiftUI view: latest recap up top, archive list below.
- Banner integration on the existing Capas screen: small card at top, tap navigates to `RecapScreen`.
- `iosApp/iosApp/{pt,en,es}.lproj/Localizable.strings` — add the same keys.
- (No iOS push wiring — current app has none. iOS users discover via the banner on next app open.)

## Error handling

- Network/404 on `recaps.json` (e.g. before any recap has ever been published): `RecapRepository` returns an empty `RecapsResponse`; banner is hidden; Recap screen shows an empty state ("No recap yet").
- Missing PT translation in source markdown: `recap-build` exits non-zero; workflow fails before deploying or pushing.
- Push tap arrives before `recaps.json` cache is fresh: `RecapScreen` does its own fetch on entry, brief loading state.
- Banner dismissal is stored as `last_seen_recap_id`; when a newer `id` arrives, the banner re-appears for that one.

## Verification

End-to-end:

1. **Author**: create `recaps/2026-W18.pt.md` with valid frontmatter and short body.
2. **Build locally**: `cd capas-api && cargo run --release --bin recap-build`. Inspect `capas-api/public/recaps.json` — should contain one entry with PT translation only.
3. **Add EN**: create `recaps/2026-W18.en.md`. Re-run; JSON now has both translations under `2026-W18`.
4. **Failure case**: create `recaps/2026-W19.en.md` with no PT counterpart. Re-run; `recap-build` should exit non-zero with a clear error.
5. **Cap test**: drop in 10 fixture markdown files spanning 10 weeks. Re-run; JSON contains exactly the latest 8, sorted by `publishedAt` desc.

App:

6. Run shared tests: `./gradlew :shared:allTests` — covers JSON parsing, language fallback (`device=pt` → PT, `device=en` with no EN translation → falls back to PT, `device=en` with EN translation → EN), and archive ordering.
7. Run Android unit tests: `./gradlew :composeApp:testDebugUnitTest`.
8. Build & run Android debug pointing at a published `recaps.json` (push fixture data to a personal branch's `gh-pages` first, or temporarily override the URL via a debug-only Koin module). Verify: banner appears on Capas; tap → Recap screen; archive list scrolls; banner stays dismissed until next id arrives.
9. iOS: open `iosApp/iosApp.xcodeproj`, run on simulator, verify the same flow without push.

CI:

10. **Real publish dry-run**: trigger `publish-recap.yml` manually from the Actions UI on a non-`main` branch (or against a personal fork). Verify `recaps.json` lands on `gh-pages` without disturbing `capas.json`, and the FCM call returns 200.
11. **Push deep-link**: with the Android app installed and subscribed to `updates`, trigger a real publish; tapping the notification should open the app directly on the Recap screen (verified via `MyFirebaseMessagingService` `data.kind` branch).

## Out of scope

- Scraping article text or headlines from vercapas.com.
- LLM-generated summaries.
- Featured covers attached to a recap (the answer was explicitly "title + short body text").
- iOS push notifications (no FCM/APNs on iOS today; out of scope for this feature).
- Spanish strings on Android beyond what's already there (defer until user adds `values-es/`).
- A separate FCM topic for recaps (reusing `updates` was the chosen answer).
