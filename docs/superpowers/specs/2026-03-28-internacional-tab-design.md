# Internacional Tab — Design Spec

## Context

Capas currently displays only Portuguese newspaper front pages, organized into 4 categories (Nacional, Desporto, Economia, Regional). Users have expressed interest in seeing international covers without losing the app's clean, simple experience.

Rather than building a full multi-country navigation system, the approach is to add a single new **"Internacional"** tab with a curated list of ~15 notable international newspapers. This preserves the existing UX for Portuguese users while expanding the app's scope with minimal complexity.

## UX Design

### Navigation

A 5th tab is added after "Regional":

```
Nacional  Desporto  Economia  Regional  Internacional
                                        ─────────────
```

Tab label is localized:
- PT: "Internacional"
- EN: "International"
- ES: "Internacional"

The tab has no icon — text only, consistent with the other tabs.

### Grid Behavior

The Internacional tab uses the same grid layout as all other tabs:
- Same card style (cover image + newspaper name + relative date)
- Same drag-to-reorder functionality
- Same drag-to-remove (trash drop zone) functionality
- Same card aspect ratio, spacing, and gradient overlay
- No country flags or badges on cards — just the newspaper name

### Curated Newspaper List

10 notable papers with good geographic spread (verified active on kiosko.net):

**Europe:**
- El País (Spain)
- The Times (UK)
- The Guardian (UK)
- Financial Times (UK)
- Corriere della Sera (Italy)
- La Gazzetta dello Sport (Italy)

**Americas:**
- The New York Times (USA)
- Washington Post (USA)
- The Wall Street Journal (USA)
- Folha de S.Paulo (Brazil)

*Note: Le Monde, Le Figaro, Bild, Frankfurter Allgemeine, and The Japan Times were removed because kiosko.net stopped updating their covers.*

This list is maintained in the scraper configuration and can be adjusted without app changes.

## Data Model

### JSON Output (scraper)

New `"Internacional"` key in `capas.json`:

```json
{
  "Jornais Nacionais": [...],
  "Desporto": [...],
  "Economia e Gestão": [...],
  "Regionais": [...],
  "Internacional": [
    {
      "id": "el-pais",
      "nome": "El País",
      "url": "https://...",
      "lastUpdated": "2026-03-28"
    },
    ...
  ]
}
```

Each `Capa` object has the same fields as Portuguese covers: `id`, `nome`, `url`, `lastUpdated`.

### Kotlin Model (`CapasResponse.kt`)

Add one new field:

```kotlin
@SerialName("Internacional")
val internationalNewspapers: List<Capa> = emptyList()
```

Default to `emptyList()` for backward compatibility — existing JSON without the field still deserializes.

### Category Enum

Add `INTERNATIONAL` to `CapasCategory` on both Android and iOS.

## Files to Modify

### Scraper (`capas-api/`)
- `src/main.rs` — Add international cover fetching from kiosko.net (HEAD requests to verify cover availability, construct URLs from hardcoded paper config list).

### Shared (`capas-app/shared/`)
- `src/commonMain/.../CapasResponse.kt` — Add `internationalNewspapers` field
- `src/commonMain/.../CapasRepository.kt` — Handle new category in filtering/ordering/persistence. Include migration logic to add international newspaper IDs to the allowlist (same pattern as the `REGIONAIS_INIT_KEY` migration).

### Android (`capas-app/composeApp/`)
- `src/androidMain/.../CapasScreen.kt` — Add `INTERNATIONAL` to `CapasCategory` enum, map to `internationalNewspapers`
- `src/androidMain/res/values/strings.xml` — Add `category_international` string (PT)
- `src/androidMain/res/values-en/strings.xml` — Add `category_international` string (EN)

### iOS (`capas-app/iosApp/`)
- `iosApp/CapasScreen.swift` — Add `international` case to `CapasCategory`, map to `internationalNewspapers`
- `iosApp/Localization.swift` — Add `categoryInternational` accessor
- `iosApp/pt.lproj/Localizable.strings` — Add `category_international` (PT)
- `iosApp/en.lproj/Localizable.strings` — Add `category_international` (EN)
- `iosApp/es.lproj/Localizable.strings` — Add `category_international` (ES)

## Data Flow

No changes to the data flow architecture:

```
Data source(s) for international covers
       |
[Rust Scraper] --> adds "Internacional" section to capas.json
       |
capas.json deployed to GitHub Pages
       |
[Ktor HTTP Client] --> GET /capas.json (same endpoint)
       |
CapasResponse (now with internationalNewspapers)
       |
CapasRepositoryImpl (filters, orders, persists — same patterns)
       |
CapasViewModel --> StateFlow<CapasViewState>
       |
UI: 5th tab in CapasScreen (Android + iOS)
```

## Data Source: Kiosko.net

International covers are sourced from **kiosko.net**, which provides a predictable URL pattern — no HTML scraping needed:

```
https://img.kiosko.net/{YYYY}/{MM}/{DD}/{country_code}/{paper_id}.750.jpg
```

Two image sizes: 200px thumbnails (`.200.jpg`) and 750px full-size (`.750.jpg`). The site covers 60+ countries and has been a stable source used by open-source projects for years.

### Paper IDs

| Paper | Country Code | Kiosko ID | Display Name |
|-------|-------------|-----------|--------------|
| El País | `es` | `elpais` | El País |
| The Times | `uk` | `the_times` | The Times |
| The Guardian | `uk` | `guardian` | The Guardian |
| Financial Times | `uk` | `ft_uk` | Financial Times |
| Corriere della Sera | `it` | `corriere_della_sera` | Corriere della Sera |
| La Gazzetta dello Sport | `it` | `gazzetta_sport` | La Gazzetta dello Sport |
| The New York Times | `us` | `newyork_times` | The New York Times |
| Washington Post | `us` | `washington_post` | Washington Post |
| The Wall Street Journal | `us` | `wsj` | The Wall Street Journal |
| Folha de S.Paulo | `br` | `br_folha_spaulo` | Folha de S.Paulo |

### Scraper Logic

1. Construct URL for each paper using today's date
2. Send HTTP HEAD request to verify the image exists (200 OK)
3. If 404, try yesterday's date as fallback
4. `lastUpdated` is set to the date that returned 200
5. Papers with no available cover (both days 404) are skipped
6. Output all successful papers in the `"Internacional"` JSON section

This list is maintained as a hardcoded config array in the scraper — adding/removing papers requires only editing the array, no logic changes.

## Verification

- `cargo run` in `capas-api/` produces `capas.json` with `"Internacional"` section
- `./gradlew :composeApp:assembleDebug` builds successfully
- Android: 5th tab "Internacional" appears, shows international covers in grid
- iOS: matching 5th tab appears with same covers
- Drag-to-reorder and drag-to-remove work in the new tab
- Existing 4 tabs are unaffected
- App handles missing `"Internacional"` key gracefully (empty tab, no crash)
