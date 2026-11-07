# Conference-styled Confetti, with Livery

An exploration of adopting [Livery](https://github.com/yschimke/livery) (server-driven Remote
Compose templates) in Confetti: each conference gets its own look for headers, sessions,
speakers and promotions, across form factors, and the look can change without an app release.

Two conferences are designed here: **droidcon** (dark, terminal-flavoured, Android green) and
**KotlinConf** (the Kotlin gradient, soft corners, a gradient that drifts on the player's clock).

| | droidcon | KotlinConf |
| --- | --- | --- |
| Header | ![](renders/droidcon-header-in-app.png) | ![](renders/kotlinconf-header-in-app.png) |
| Session | ![](renders/droidcon-session-in-app.png) | ![](renders/kotlinconf-session-in-app.png) |
| Speaker | ![](renders/droidcon-speaker-in-app.png) | ![](renders/kotlinconf-speaker-in-app.png) |
| Promotion | ![](renders/droidcon-promo-in-app.png) | ![](renders/kotlinconf-promo-in-app.png) |
| Launcher widget | ![](renders/droidcon-session-launcher.png) | ![](renders/kotlinconf-session-launcher.png) |
| Wear widget | ![](renders/droidcon-session-wear-large.png) | ![](renders/kotlinconf-session-wear-large.png) |

### Light, dark and Material 3 dynamic colour

Every colour in a design is a `theme.*` role, so a mode or a palette is data pushed beside the
slot's data, never a new document:

- **Light and dark.** Each conference has a theme pack per mode (`droidcon-light`,
  `droidcon-dark`, `kotlinconf-light`, `kotlinconf-dark` in `catalogs/<series>/themes/`). A slot picks the
  one matching the app around it, judged from its `MaterialTheme`, so Confetti's own light or dark
  setting wins over the system's.
- **Material 3 dynamic colour.** When the user has dynamic colour on (`ConferenceMaterialTheme`
  sets `LocalDynamicColorInUse`), a slot pushes their `MaterialTheme.colorScheme` instead
  (`materialThemeValues` in `Contracts.kt`, one mapping for the app and these renders). Each
  conference keeps its layout and takes the user's wallpaper colours.

Changing the mode or the wallpaper recolours a slot live, without restarting its document.

![](renders/light-dark-dynamic.png)

The same in the app (`LiverySlotsTest`): light, dark, and dynamic colour from a teal wallpaper.

![](renders/android-agenda-modes.png)

### In the app

The real Android path (`LiverySlotsTest`): the catalog bundled into the APK by Livery's Gradle
plugin, picked by conference series, filled through the contract and played inside Confetti's
agenda. A conference with no designs (`devfestnantes2025`) keeps Confetti's own row, and the
droidcon party promotion comes from a placement rule in the catalog.

![](renders/android-agenda-slots.png)

### An admin's change, as data

The same droidcon documents with a different theme pack (a venue blue for droidcon Berlin): no
new capture, no app release, just values.

| droidcon (catalog theme) | droidcon, Berlin theme pack |
| --- | --- |
| ![](renders/droidcon-header-in-app.png) | ![](renders/droidcon-header-in-app-berlin-theme.png) |
| ![](renders/droidcon-session-in-app.png) | ![](renders/droidcon-session-in-app-berlin-theme.png) |
| ![](renders/droidcon-speaker-in-app.png) | ![](renders/droidcon-speaker-in-app-berlin-theme.png) |

## How it fits together

```
livery-contracts/   Contracts.kt: the four contracts, typed (KMP: Android, iOS, JVM, wasm)
        │  used by both sides
        ├── livery-catalog/   designs as @RemoteComposable functions (rc-players' CMP fork of
        │                     remote-creation-compose), captured to .rc on the JVM, plus
        │                     plus themes, placements, experiments and fonts (data)
        │       └── catalogs/<series>/  one Livery catalog source tree per conference series:
        │                     what `livery build` publishes to the livery-templates branch
        │
        └── shared/ui/livery  ConferenceSlot / PromoSlot: contract-typed slots in the shared
                              Compose UI, each with Confetti's own UI as the fallback
androidApp/livery/            the repository (catalog bundled into assets by Livery's Gradle
                              plugin) provided as LocalLiveryStyling
```

```bash
./gradlew :livery-catalog:generateLiveryCatalog   # capture designs, write catalogs/ and renders/
./gradlew :livery-catalog:test                    # contract checks; catalogs/ match the sources
./gradlew :livery-catalog:buildLiveryChannels     # livery build --inspect per series -> build/livery-channels/
./gradlew :livery-contracts:jvmTest
```

## The contract

The contract between the app and a design is the one thing both sides must agree on, so it's
enforced at every step. `Contracts.kt` is the source of truth:

1. **Types.** Designs read `input(SessionContract.title)` and emit `emit(SessionContract.open)`,
   not strings. The app builds data with `SessionContract.data(mapOf(SessionContract.title to …))`.
   Renaming an input breaks both sides at compile time.
2. **Capture.** A template captured for one contract that reads another contract's input fails
   the capture. `reads` and `emits` in each `<template>.json` are recorded while capturing, so
   they can't drift from the document.
3. **Generation checks** (`CatalogTest`): every example binds through Livery's `ContractBinder`
   (the gate devices use), every design emits each of its contract's actions and shows every
   required input, theme packs only set theme roles, and placements carry data their design's
   contract accepts. The checked-in `catalogs/` must equal what the sources generate.
4. **Livery's publisher**: `livery build --inspect` decodes every `.rc` and checks it against its
   sidecar (the Android build runs this through the Gradle plugin).
5. **Runtime**: binding refuses unknown or over-long values, so the app trims first
   (`Input.fit`). An action the contract doesn't declare, or a URL outside the allowlist, is
   dropped.

Contracts evolve per Livery's rules: within a major, only add optional inputs and actions.

| Contract | Data from | Inputs | Actions |
| --- | --- | --- | --- |
| `confetti/conference@1` | app | name, dates, location, tagline, live, ticketsUrl | tickets (open-url) |
| `confetti/session@1` | app | title, speakers, room, time, tag, live, bookmarked | open, bookmark |
| `confetti/speaker@1` | app | name, tagline, photo, sessions | open |
| `confetti/promo@1` | decision | eyebrow, headline, body, cta, url | cta (open-url), dismiss |

Every contract also takes the conference palette as theme roles (`theme.primary`, …).

## What each of the four goals looks like

1. **Conference-styled components.** Designs carry a `series` tag (`droidcon`, `kotlinconf`);
   the app maps a conference id to its series (`ConferenceSeries`) and asks for that tag. A
   conference with no designs keeps Confetti's UI, as does any platform that hasn't provided
   `LocalLiveryStyling`.
2. **Conference assets.** The header shows the conference's own branding and copy, and a theme
   pack per series (`catalogs/<series>/themes/`) holds the palette as data: change a colour there and every
   template follows, with no new document.
3. **Promotions.** `catalogs/<series>/placements/` holds rules for `home.feed`, `session.footer` and
   `speaker.footer`: schedules, conference segments (`conference:<id>`), sticky rollouts and
   frequency caps, decided on the device. A promotion server could answer the same placements
   later (`FirstDecider`).
4. **Animation and polish.** Animations are remote expressions on the player's clock, not
   recomposition: the "live" pills breathe, the KotlinConf gradient drifts and its diamond
   turns. They cost nothing on the app side and work in a widget host that can play them.

## Form factors

| Surface | Status here |
| --- | --- |
| Phone and tablet (Android) | Wired: agenda header, promotion and session cards, promotions under sessions and on speaker pages. Only in the `sduiDebug` build type (see below); every other variant draws Confetti's own UI |
| iOS, desktop, web | The same shared slots compile for every target; each platform needs only to provide `LocalLiveryStyling` (a repository and a bundled or hosted catalog) |
| Launcher widget | Templates authored (`launcher` target); next: a `LiveryLauncherWidgetProvider` for "next session" |
| Wear | Templates authored (`wear-widget`, `wear:large`); next: a `LiveryWearWidget` beside the existing tiles |

## An admin changing a design from the app

Livery already has the pieces for an agent to change a design safely; the flow for a
conference admin would be:

1. In the app (admin role), long-press a styled slot and write a request ("make the droidcon
   header say Berlin and use the blue from the venue"). The app sends it with the slot's
   design id, contract and current data to an agent session.
2. The agent works on this source tree through `livery-mcp` (`livery-mcp livery-catalog/catalogs/droidcon
   --write --preview-out …`): `get_contract` for what it may use; data-only changes (copy, a theme
   pack's colours, a placement's schedule) are JSON edits checked by `validate_data`; a layout
   change edits the design's Kotlin and is re-captured. `render_preview` shows the result.
3. `build_preview` / `publish_preview` publish an unsigned preview channel; the admin's debug
   build plays it in place, with their real data.
4. Approved, it becomes a PR here: CI runs `CatalogTest` and `livery build --inspect`, then
   `livery check` against main marks any contract change that isn't compatible. Merging signs
   and publishes the catalog; apps pick it up on their next refresh.

The strong contract is what makes this safe: an agent can change everything about a design
except what it reads and emits, and anything that would break the app fails before review.
Most admin requests are data (copy, colours, schedules), which is why the palette lives in theme
packs and promotions in placements, not in the documents.

## The `sduiDebug` variant: server templates, baked-in fallback, an A/B test

`sduiDebug` is `debug` plus Livery. Every other variant (`debug`, `release`, `githubRelease`)
draws Confetti's own UI and ships no catalog.

```bash
./gradlew :androidApp:installSduiDebug
./gradlew :androidApp:installSduiDebug -Pconfetti.livery.arm=native     # pin an arm (QA, screenshots)
./gradlew :androidApp:installSduiDebug -Pconfetti.livery.templates=https://host/catalogs   # another channel
```

**One catalog per conference.** `catalogs/droidcon` and `catalogs/kotlinconf` are separate Livery
catalogs (`confetti-droidcon`, `confetti-kotlinconf`), each with its own designs, theme packs,
placements, experiments and fonts. They publish, cache, expire and fail independently: a broken
droidcon publish can't blank KotlinConf's agenda.

**Where a slot's design comes from, in order:**

1. **The server.** `livery-templates` on this repository, one static channel per series
   (`catalogs/<series>/manifest.json`, read from raw.githubusercontent.com). The *Livery templates*
   workflow publishes it on every merge to main that touches the catalogs, and weekly so the
   manifest doesn't expire. Slots ask for a refresh at most every five minutes; a downloaded
   catalog has a higher serial than the bundled one, so it wins, and open slots replan on the
   repository's revision.
2. **Baked in.** The same source trees, built and checked (`--inspect`) into the APK's assets at
   `livery/<series>/` by `BuildLiveryCatalog` tasks, sduiDebug only. First launch and offline
   draw these.
3. **Confetti's own composable**, when neither has a template for the slot, the conference has no
   series, or the user is in the native arm. So the old composables can be deleted only once
   every slot has a template in the bundled catalog.

Later the channel can move to `joreilly/confetti-templates` (a repository of its own fits
`GitHubReleaseSource`'s `releases/latest`), and a release build needs a signed manifest (below).

**Different designs and fonts.** droidcon is set in **Space Mono** (terminal-flavoured, green on
black) and KotlinConf in **Poppins** (round, on the Kotlin gradient), both OFL-1.1, shipped in
each catalog's `fonts/` and downloaded with the templates that name them. `Label` sets every
text in its brand's face (`RemoteFontFamily.Named`), capture records it in the sidecar's
`fonts`, and `CatalogTest` checks every template names its brand's face and that the catalog
ships it. The renders use the same files.

| droidcon (Space Mono) | KotlinConf (Poppins) |
| --- | --- |
| ![](renders/droidcon-session-in-app.png) | ![](renders/kotlinconf-session-in-app.png) |

**The A/B test: Livery against the composables.** `experiments/sdui.json` in each catalog splits
users between `native` (Confetti's composables) and `livery` (the conference's designs): 50/50
for droidcon, 25/75 for KotlinConf. It's data: a conference changes its split or ends the test
with a PR to its catalog. Arms are assigned on the device (`LocalBucketing`, sticky per install's
bucketing id, the same on every platform). Users who aren't enrolled get `livery`.

**Interaction tracking.** Every event goes to Confetti's own `AnalyticsLogger` as Livery's flat
analytics events (logcat in debug, Firebase in release): `livery_impression`, `livery_viewable`,
`livery_action` with `action` = the contract action (`open`, `bookmark`, `tickets`, `cta`),
`livery_leave` with `duration_ms`, and `livery_exposure` once per arm. Every event carries
`exp_sdui`. In the native arm the composable gets a `SlotTracker`, and Confetti's own session
row reports its impression and its taps under the same action names, with `design_id=native`,
so tap-through compares across arms. `LiverySlotsTest.nativeArm` checks this.

## Findings

### What works well

- **The contract as Kotlin objects.** Designs and app both use `SessionContract.title`, so a rename
  breaks both at compile time, and capture refuses another contract's input. Nothing about the
  per-conference split or the experiment needed a contract change.
- **Data-only changes.** Colours (theme packs per mode), promotions (placements), the experiment
  split and the fonts are files in the catalog. Only layout needs a re-capture.
- **Fallback by construction.** `LiverySlot` draws the fallback while planning and whenever there's
  no template, so the composables were the native arm for free.
- **Publishing as static files.** `livery build` output is a plain directory. A branch plus
  raw.githubusercontent.com was enough, and the bundled copy uses the same builder, so "baked in"
  and "from the server" can't drift apart.
- **Analytics shape.** `AnalyticsEventSink` fit Confetti's existing `AnalyticsLogger` with a
  three-line adapter, so iOS and web get the events through their own loggers too.

### Friction and boilerplate (candidates for Livery)

- **No "native arm".** An experiment arm can pick a design or nothing (the usual choice). An arm
  that means "the app's own composable" has to be read by the app (`Experiments.arm`). The app
  then emits the impression, the exposure and the taps itself, and tags every event with the arm
  through a wrapped sink (`SeriesCatalog.events`, `NativeSlot`, `nativeTracker`, about 60
  lines). **Livery should provide this pattern:** an arm flag (`"native": true`) that makes
  `SlotPlanner` return `PlanResult.Native(arm)`, plus a `LiveryNativeSlot { tracker -> }` that
  reports `Impression`, `Viewable`, `Leave` and `Action` through the same `ActionGate` rules. Then
  "Livery vs what we had" is one JSON file and no app code.
- **Arms ignore slot context.** `Experiments` takes one `DecisionContext` provider, but arms here
  depend on the conference (segments), so the app builds a second `Experiments` per call.
  `arm(id, context)` would fix it.
- **One catalog per Gradle plugin.** The plugin bundles one source tree into every variant. Per
  conference and per build type meant registering `BuildLiveryCatalog` tasks by hand (the class is
  public, which saved it). A `livery { catalogs { register("droidcon") { … } }; variants = … }`
  DSL would remove that.
- **A repository per catalog is a lot of wiring.** Each series needs its own `TemplateRepository`
  with separate blob and key-value stores, its own `Experiments`, `SlotPlanner`, `Decider` and
  `LiveryHost`. A `LiveryCatalogs(sources, bundled = { "livery/$it" })` would keep apps from getting
  the store separation wrong.
- **Refresh is the app's job.** Nothing calls `TemplateRepository.refresh()`. Slots here call it
  throttled. Livery could offer `repository.refreshWhileObserved()` or a WorkManager helper.
- **Expiry needs a scheduled republish.** `expiresInDays` applies to downloaded manifests, so an
  idle catalog must be rebuilt (the weekly cron). That's correct for signed catalogs, but it's
  easy to miss: the app silently drops back to the bundled copy.
- **Font names are case-folded by the player.** A custom `RcTypefaceLoader` (the renders here) must
  match families case-insensitively. `LiveryTypefaceLoader` does, and `livery-render` could take
  a catalog's `fonts/` directly.
- **Unchanged from 0.2.0:** slot height is the capture height, so wider faces (Space Mono) clip
  sooner. `open-url` builds `{"url":"…"}` in the document. Sidecars and typed contracts are still
  hand-rolled (`Authoring.kt`; `livery-capture`'s `LiveryValues` isn't published to Maven yet).

### Contracts and the app's model (DAO objects)

Options weighed for getting Apollo's `SessionDetails` into a slot:

- **Interfaces the model implements.** Not possible: the models are generated, and they carry
  fields a design must not see.
- **`Map<Input, Any>` built at each call site** (0.2.0 branch). Type-safe keys, but not values or
  completeness. It's easy to forget an input or to skip `fit`.
- **Copy into a data class per contract (chosen).** `SessionCard`, `SpeakerCard` and
  `ConferenceCard` in `:livery-contracts` (`ContractData.kt`):
  - one mapping function per model (`SessionDetails.toSessionCard`);
  - `values()` fits every string, drops blanks and refuses non-https images;
  - `ContractDataTest` fails when a contract gains an input its class doesn't fill.

  The native composable can take the same class. Then both arms draw the same data, and deleting
  an old composable touches nothing around it. That's the next step here: `SessionItemView`
  still takes `SessionDetails`.

What Livery could add: generate these classes, the JSON and the checks from the contract (KSP
or a Gradle task over `contracts/*.json`), validated at the same `ContractBinder` gate.

### Does Livery need a way to publish themes?

Partly. Theme packs already publish as catalog data: they ship in the manifest, an admin changes
them as JSON, and `repository.theme(id)` reads them. What's missing:

- **Theme-only publishing.** A colour change rebuilds and republishes the whole catalog. A
  `themes` channel, or a manifest that can reference a theme document by hash, would make a
  recolour a tiny, fast update.
- **Typography in themes.** Fonts sit in `fonts/` and are named per template. A theme pack that
  could map `theme.font.display`/`theme.font.body` roles to families would let a conference change
  its face without a re-capture, the way colours already work.
- **One theme for the whole app.** Confetti's native UI has its own per-conference Material
  schemes. Exporting a pack as a Material 3 `ColorScheme` (the inverse of `materialThemeValues`)
  would give native and Livery slots one source of truth.

## Friction found in 0.1.1, fixed in Livery 0.2.0

Found doing this, fixed in [yschimke/livery#14](https://github.com/yschimke/livery/pull/14) and
released in Livery 0.2.0 (this branch is on 0.4.0):

- **Compose version.** Livery 0.1.1 is built against Compose Multiplatform 1.13.0-beta02. Confetti
  pins the stable androidx Compose BOM (runtime 1.12.1), so the app compiles and then every slot
  crashes with `NoSuchMethodError`. It also pulls a beta CMP into `shared`'s other targets. 0.2.0
  builds on stable 1.12.2.
- **Bundled catalog without placements or themes.** `manifest()` is only the downloaded catalog,
  so a fresh install never decided a placement. 0.2.0 adds `current()` and `theme(id)`, which
  `LiveryStyling` uses.
- **Opaque agent errors.** `livery-mcp` said "internal error" when Skia failed to load on a
  headless host. It now says why and how to fix it.
- **Stale getting-started step**, pointing at a snapshot composite build.

The ones still open are under **Findings** above.
