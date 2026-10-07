# Bus Map "Tiempo Real" Implementation Plan (2026-10-08)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Mapa" screen showing the live bus map (route, stops, bus positions) with fresher updates than the official app (poll 5 s vs their 15 s), freshness transparency, interpolated marker movement, and a self-computed per-stop ETA (the official ETA endpoint is disabled in Salta).

**Architecture:** New `ui/map/` screen + `BusMapViewModel` + `BusMapRepository` + `BusMapApiService`, wired into `AppContainer` and the existing sealed-`Screen` navigation. All endpoints are anonymous GETs on the same host the app already uses — no session, no Turnstile, no captcha. No Room changes. One new dependency: osmdroid.

**Tech Stack:** Kotlin 2.0.20, AGP 8.7.0, Compose BOM 2024.09.02, Retrofit/Gson/OkHttp (already present), osmdroid-android (new), Robolectric 4.13, MockK, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/research/2026-10-07-mapa-buses-tiempo-real.md` (endpoint contracts, freshness measurements, official UX reference) — read it first.

## Verified API contract (live, anonymous, `cache-control: no-store`)

```
GET /rest/gruposLineas
  → {error:0, grupos:{codGrupo, subGrupos:[{codGrupo:"URBANO", subGrupos:[{codGrupo:"Corredor 1", subGrupos:null, lineas:[{codLinea:"100",descripcion:"1B"},…]}], lineas:[…]}], lineas:[]}}
  Groups nest arbitrarily deep; a group may carry `lineas` directly. codLinea is a STRING.

GET /rest/rutaLinea/{codLinea}
  → {error:0, nodos:[{latitud,longitud,parada:bool,codigoParada:"p001-1b",descripcionParada:"…"},…]}
  nodos in order = route polyline; parada:true nodes are stops.

GET /rest/posicionesBuses/{codLinea}
  → {error:0, posiciones:[{interno:"132",latitud,longitud,orientacion:0-360,
       proximaParada:"Nombre Parada"|null, vehiculoRampa:bool, vehiculoNoVisibles:bool},…]}
  No timestamps. Invalid codLinea → {error:0, posiciones:[]}.
  Source freshness: ~30-60 s per bus (measured). Official app polls 15 s.

GET /rest/novedadesLineas?codLineas={codLinea}
  → {error:0, novedadLineas:[{codLinea, novedades:["…texto con emojis…"]}]}

GET /rest/getConfiguracion
  → {error:0, habilitarMapaBuses:true, habilitarCuandoViene:false,
       urlPrincipalMapas:"https://mapmoblrj.red-bus.com.ar/tiles/{z}/{x}/{y}.png",
       urlSecundariaMapas:"https://b.tile.openstreetmap.org/{z}/{x}/{y}.png",
       latitud:-24.7899943, longitud:-65.4130054, …}
```

## Global Constraints

- New dependency allowed: `org.osmdroid:osmdroid-android` (pin a stable release ≥7 days old, add to `gradle/libs.versions.toml`). NO Google Maps (API key/cost), NO MapLibre (native size for marginal gain — raster tiles + marker animation don't need GL).
- No Room schema changes, no new permissions until Task 6 (location button).
- Poll positions ONLY while `BusMapScreen` is active AND a line is selected. Default interval **5 s**; exponential backoff on failure (5→10→20→40→60 s cap), reset on success. Never poll catalog/route in a loop — cache in repository (in-memory only).
- User-facing copy in Spanish, matching existing tone.
- `Main.immediate` trap (F2 lesson): declare all StateFlows before `init`/collectors that reference them.
- The implementer does not commit; the lead commits after review.
- Keep the app working when endpoints return `error != 0` or empty lists: show "Sin buses en circulación" / "No se pudieron cargar los datos" states, never crash on null fields (`proximaParada` is often null).

## Review Focus

1. Poll loop runs only while the map screen is shown with a selected line; leaving the screen or clearing selection cancels it. → Task 2 test.
2. Positions marker shows "hace Ns" age; buses unseen >120 s render dimmed. → Tasks 2-3.
3. Fresh-install + rotate on the map keeps selected line and route (VM survives via `viewModel()`; route/positions re-fetch quickly). → Task 3.
4. ETA math: projection onto polyline + distance-to-stop gives sane minutes on the real 1B fixture. → Task 5 test.
5. Manifest gains `ACCESS_COARSE_LOCATION` only in Task 6, and privacy docs mention it + the live-positions source. → Task 6 test.

---

### Task 1: Network layer — DTOs, service, repository

**Files:**
- Create: `android/app/src/main/java/com/saetasaldo/app/data/remote/api/BusMapApiService.kt`
- Create: `android/app/src/main/java/com/saetasaldo/app/data/remote/dto/BusMapDtos.kt`
- Create: `android/app/src/main/java/com/saetasaldo/app/domain/model/BusLine.kt` (`data class BusLine(codLinea: String, descripcion: String)`, `data class LineGroup(val codGrupo: String, val subGroups: List<LineGroup>, val lineas: List<BusLine>)`)
- Create: `android/app/src/main/java/com/saetasaldo/app/domain/model/LineRoute.kt` (`RouteNode(latitud,longitud,parada,codigoParada,descripcionParada)`, `data class LineRoute(nodes)`, plus `stops` derived list)
- Create: `android/app/src/main/java/com/saetasaldo/app/domain/model/BusPosition.kt` (`interno, latitud, longitud, orientacion, proximaParada: String?, vehiculoRampa: Boolean`)
- Create: `android/app/src/main/java/com/saetasaldo/app/domain/repository/BusMapRepository.kt`
- Create: `android/app/src/main/java/com/saetasaldo/app/data/repository/BusMapRepositoryImpl.kt`
- Create: `android/app/src/test/java/com/saetasaldo/app/data/remote/dto/BusMapDtosTest.kt`
- Create: `android/app/src/test/java/com/saetasaldo/app/data/repository/BusMapRepositoryTest.kt`
- Create fixtures under `android/app/src/test/resources/busmap/`: `grupos_lineas.json`, `ruta_linea_100.json`, `posiciones_100.json`, `novedades_100.json` — capture live with `curl -s https://salta.miredbus.com.ar/rest/gruposLineas` etc. (the server is public; refresh fixtures from live data, don't hand-write them)
- Modify: `android/app/src/main/java/com/saetasaldo/app/data/remote/NetworkClient.kt` (add `busMapApiService` lazy, same Retrofit instance/client)

- [x] `BusMapApiService` (Retrofit, suspend): `getGruposLineas()`, `getRutaLinea(@Path codLinea)`, `getPosicionesBuses(@Path codLinea)`, `getNovedadesLineas(@Query codLineas)`, `getConfiguracion()`. DTOs mirror the JSON exactly (camelCase fields, `proximaParada` nullable).
- [x] `BusMapRepository` interface: `suspend fun lineTree(): Result<LineGroup>`, `suspend fun route(codLinea): Result<LineRoute>`, `suspend fun positions(codLinea): Result<List<BusPosition>>`, `suspend fun lineNews(codLinea): Result<List<String>>`, `suspend fun config(): Result<MapConfig>`.
- [x] `BusMapRepositoryImpl`: in-memory caches — `lineTree` fetched once per process, `route` and `lineNews` cached per codLinea (`MutableMap`), `positions` NEVER cached. Map DTO→domain; `vehiculoNoVisibles=true` filtered out in domain mapping.
- [x] `MapConfig` domain model: `habilitarMapaBuses`, `urlPrincipalMapas`, `urlSecundariaMapas`, `latitud`, `longitud` (+ `habilitarCuandoViene` recorded for completeness).
- [x] DTO tests: parse each fixture; assert tree shape (URBANO group → Corredor 1 → line 1B cod 100), route node/parada counts > 0, positions fields incl. null `proximaParada` accepted, news list.
- [x] Repository tests (MockK on `BusMapApiService`): positions never cached (two calls → two service calls); route cached per codLinea; `vehiculoNoVisibles` filtered; error propagates as Result.failure.
- [x] Verify: `./gradlew :app:testDebugUnitTest` — all green.

### Task 2: BusMapViewModel — selection + poll loop + freshness

**Files:**
- Create: `android/app/src/main/java/com/saetasaldo/app/ui/map/BusMapViewModel.kt`
- Create: `android/app/src/test/java/com/saetasaldo/app/ui/map/BusMapViewModelTest.kt`
- Modify: `android/app/src/main/java/com/saetasaldo/app/SaetaApp.kt` (AppContainer: `busMapRepository` + factory for the VM — detail-VM pattern already shows how)

- [x] UI state `BusMapUiState`: `lineTree: LineGroup?`, `selectionPath: List<String>` (group codGrupos), `selectedLine: BusLine?`, `route: LineRoute?`, `buses: List<BusMarkerState>`, `news: List<String>`, `loading`, `error: String?`, `lastPollAt: Long?`.
- [x] `BusMarkerState`: `position: BusPosition`, `displayedLat`, `displayedLng`, `lastChangeAtMs`, `ageSeconds` derived.
- [x] Selection API: `selectGroup(index, codGrupo)` walks the tree (groups may have direct `lineas` — surface them at the last level); `selectLine(codLinea)` fetches route+news once, starts the poll loop.
- [x] Poll loop: `viewModelScope.launch { while(isActive && selectedLine!=null) { positions; delay(pollIntervalMs) } }` — **declare all StateFlows before any `init` collector** (F2 regression). Inject `pollIntervalMs=5000` and a `Dispatcher`/`CoroutineScope` for testability (default Main). Backoff on failure ×2 capped at 60 s, reset on success.
- [x] Freshness: per `interno`, keep `lastChangeAtMs = now` when lat/lng/orientacion changed vs previous fix; compute `ageSeconds` at render time from `now - lastChangeAtMs`.
- [x] `onCleared`/`clearSelection()`: cancel loop, keep tree. Screen disposal must not leak the coroutine.
- [x] VM tests (kotlinx-coroutines-test + MockK): poll fires N times over virtual time; cancels on clearSelection/onCleared; backoff doubles then caps; lastChangeAt advances only when the fix actually changes; selection walks a nested tree incl. group-level `lineas`.
- [x] Verify: `./gradlew :app:testDebugUnitTest` green.

### Task 3: BusMapScreen — osmdroid map + selectors + nav

**Files:**
- Modify: `android/gradle/libs.versions.toml` + `android/app/build.gradle.kts` (osmdroid)
- Create: `android/app/src/main/java/com/saetasaldo/app/ui/map/BusMapScreen.kt`
- Create: `android/app/src/main/java/com/saetasaldo/app/ui/map/OsmdroidMapView.kt` (AndroidView wrapper around `org.osmdroid.views.MapView`; lifecycle-aware resume/pause; exposes `setRoute(polyline, stops)`, `setBuses(list)`, `setCenter(lat,lng,zoom)`)
- Modify: `android/app/src/main/java/com/saetasaldo/app/MainActivity.kt` (`Screen.BusMap` in sealed interface + `when` branch; entry point below)
- Modify: `android/app/src/main/java/com/saetasaldo/app/ui/cards/CardsScreen.kt` (top-bar `Icons.Outlined.Map` IconButton → `onOpenMap`)
- Create: `android/app/src/test/java/com/saetasaldo/app/ui/map/BusMapScreenSmokeTest.kt` (Robolectric: screen composes, map view attaches, no crash)

- [x] Layout (M3, Spanish): top bar "Mapa de buses" + back arrow; under it two `ExposedDropdownMenuBox` (or M3 DropdownMenu) rows: Grupo → Corredor → Línea (hide empty levels); amber `Surface` banner listing `news` when non-empty; `MapView` fills the rest.
- [x] Tiles: `OnlineTileSourceBase` with `urlPrincipalMapas` from config (fallback constant to `mapmoblrj.red-bus.com.ar` if config fails); on tileerror switch to `urlSecundariaMapas` (mirrors official). `osmdroid.config.Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID`.
- [x] Route: red `Polyline` (cap 6dp, ~70% alpha) over all nodes; parada nodes get a small circle/`Marker` with "P" + `descripcionParada` in the info window. Auto-zoom to route bounds on first draw.
- [x] Buses: one `Marker` per interno — bus icon + rotation = `orientacion` (use a drawable the marker can rotate; osmdroid `Marker.setRotation`), title "Interno {n}", sub-description `proximaParada` when non-null ("Próxima: X"), ♿ note when `vehiculoRampa`. Alpha 1.0 when `ageSeconds ≤ 120`, else 0.35.
- [x] Header chip: "Actualizado hace {n}s" from youngest `lastChangeAtMs`; "Sin buses en circulación en esta línea" empty state when `posiciones` empty.
- [x] VM wiring: `viewModel(factory = …)` (same pattern as CardDetailViewModel post-refactor); `DisposableEffect(Unit) { onDispose { vm.stopPolling() } }` as belt-and-braces to `onCleared`.
- [x] Entry point: map icon in `CardsScreen` top bar → `Screen.BusMap` (add to sealed interface; no nav args).
- [x] osmdroid cache dir default (osmdroid writes its own tile cache in files dir — fine).
- [x] Robolectric test: `BusMapScreen` composes without crash; `MainActivity` manifest test updated if new config needed (none expected — no permission yet).
- [x] Verify: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` green; manual emulator pass — open map, pick URBANO→Corredor 1→1B, see route+buses.

### Task 4: "Tiempo real" feel — interpolation + dead reckoning

**Files:**
- Create: `android/app/src/main/java/com/saetasaldo/app/ui/map/BusInterpolator.kt` (pure Kotlin, unit-testable)
- Modify: `BusMapViewModel.kt` (hold per-interno animation state; expose a `displayedBuses` StateFlow updated by a ticker)
- Modify: `BusMapScreen.kt`/`OsmdroidMapView.kt` (consume displayedBuses)
- Create: `android/app/src/test/java/com/saetasaldo/app/ui/map/BusInterpolatorTest.kt`

- [x] `RouteProjector` (in BusInterpolator or own file): project latlng → nearest point on route polyline, return route distance `s` (meters from route start) + segment. Haversine for distances; polyline cumulative lengths precomputed.
- [x] On each fix: snap bus to `s`; estimate speed `v = Δs/Δt` between last two fixes, clamp to [0, 40 km/h], treat `|Δs| < 10 m` as stopped.
- [x] Between fixes: `ticker` every ~1 s advances `displayedS += v·Δt` (dead reckoning along the polyline), capped so the marker never overshoots the *next* parada beyond the last fix's `proximaParada` by more than one segment — simplest acceptable cap: `s ≤ s_lastFix + v·(now - t_lastFix)` is the formula; just clamp `v` and re-snap every fix.
- [x] On fix arrival, animate displayed→observed over ~1.2 s (lerp along polyline, not straight-line geo).
- [x] Buses not seen in the latest fetch keep last displayed position but age out (dim).
- [x] Tests: projection lands on-route; speed clamp; dead-reckoned advance ≈ expected meters; lerp converges in ≤1.3 s.
- [x] Verify: unit tests green; emulator visual pass — buses glide between updates instead of teleporting.

### Task 5: ETA propio por parada

**Files:**
- Create: `android/app/src/main/java/com/saetasaldo/app/domain/usecase/EstimateArrivalsUseCase.kt`
- Modify: `BusMapScreen.kt` — tapping a parada shows a bottom sheet/alert with "Próximos buses: Interno {n} en ~{m} min" (top 2-3 buses by route distance to that stop).
- Create: `android/app/src/test/java/com/saetasaldo/app/domain/usecase/EstimateArrivalsUseCaseTest.kt`

- [x] For each visible bus: projected `s` + rolling speed (median of last ≤5 fixes, clamp min 2 km/h so stationary-ish buses still give "en ~X min" conservative estimates; if truly stopped mark "sin estimar").
- [x] ETA to stop `k`: `(s_parada − s_bus) / v` → minutes, round up; only stops *ahead* of the bus on the polyline (use `proximaParada` to sanity-check direction; skip if inconsistent).
- [x] UI: tap parada → M3 `ModalBottomSheet` "Llegadas estimadas" listing interno + "~X min" (+ "a pie" distance fallback: straight-line distance when route projection is ambiguous).
- [x] Disclaimer text under the list: "Estimación calculada en el dispositivo, puede variar."
- [x] Tests on real fixtures (1B route + positions): ETAs are positive, ordered, within sane bounds (0–60 min); stopped/retrograde buses excluded.
- [x] Verify: unit tests green; manual check on emulator vs known stop.

### Task 6: Botón "mi ubicación" + permisos + privacidad

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml` (`ACCESS_COARSE_LOCATION`)
- Modify: `BusMapScreen.kt`/`OsmdroidMapView.kt` — FAB (osmdroid `MyLocationNewOverlay` or manual `LocationManager` last-known → center map; no continuous tracking)
- Modify: `android/app/src/main/java/com/saetasaldo/app/domain/model/PrivacyPolicyContent.kt` + `PRIVACY_POLICY.md` + `docs/compliance/DATA_SAFETY.md` — add: ubicación aproximada usada solo en el mapa, nunca enviada; posiciones de buses provienen de RedBus/SAETA en tiempo real.
- Modify: `android/app/src/test/java/com/saetasaldo/app/MainActivityManifestTest.kt` — assert new permission present.

- [x] Permission requested in-context on FAB tap (`rememberLauncherForActivityResult(RequestPermission)`); denied → snackbar "Sin permiso de ubicación" and continue working.
- [x] Use COARSE only (no fine/background). Last-known + single `requestSingleUpdate` is enough; do NOT run a location loop.
- [x] Docs: update all three privacy files + PrivacyPolicyContent.kt consistent with actual behavior (location never leaves device; bus positions are public RedBus data).
- [x] Verify: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` green; manifest test passes.

## Final Gate

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :wear:assembleDebug :app:lintDebug` — all green, 0 lint errors, no new warnings beyond baseline 56.
- Emulator pass: map opens, line select works, buses move smoothly, freshness chip updates, ETA sheet shows sane values, location FAB centers map.
- Version bump to `versionCode 4` / `versionName "1.3.0"` and release are a separate decision — NOT part of this plan.
