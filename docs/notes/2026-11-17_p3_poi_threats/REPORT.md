# Phase 3 part 2: POI threat profile, hotspot, guaranteed encounters (hand-over)

Branch `claude/p3-poi-threats-b741f8`. Not merged, not pushed. `gradlew build` green: 108 unit tests (was 79) and 47 game
tests (all required tests pass).

**In short:** the external POI now owns its threat meaning through a data file. Only zombies, skeletons and cave
spiders appear there on their own, also in lamp-lit tunnels where the wilderness spawns nothing, up to a local cap of 12.
One controlled hotspot keeps 4 of them near the site's centre while a player is close. A placeholder "Foreman" zombie is
a guaranteed encounter whose state (not spawned / active / defeated) lives in its own store and survives save/reload
and chunk unload/reload without duplicating.

## 1. Design

### Threat profile (data)
- `data/<ns>/threat_profile/<id>.json` (`threat/ThreatProfile`):
  - `ambient_hostiles`: `vanilla` | `suppressed` | `listed`
  - `hostiles`: type, weight, group size; `listed` only, monster category only
  - `blocked`: types refused in any mode
  - `local_cap`
  - `conditions`: `max_block_light`, `max_brightness`, optional `min_y`/`max_y`
  - `hotspots`
- Shipped: `placeholder_abandoned_mine`: listed zombie 50 / skeleton 30 / cave spider 20, local cap 12, block light up to 7.
- The two engine profiles (wilderness = vanilla, protected settlement = suppressed) are code, behind the existing saved
  `SpawnProfile` IDs.
- **POI → profile:** `data/<ns>/poi_threats/<catalog path>.json` maps the site's catalog ID (already in the WorldPlan) to
  `{threat_profile, encounters}`. Nothing was added to the WorldPlan; its saved `poi_placeholder` ID now means "look the
  POI up by catalog". Binding happens in `LevelThreats.install`, called from `Regions.install` (world creation, world
  opening, and now also after `/reload`).
- **Hot path:** `RegionIndex.threatAt` returns the region's `RegionThreat` (profile + live ambient count) from a parallel
  array; same lookup as `regionAt`. `SpawnPolicy.decide(rpg, humanLike, hostile, type, reason, profile, population)` is
  still one pure call.
  - New decisions: `REFUSE_BLOCKED_HOSTILE`, `REFUSE_UNLISTED_HOSTILE`, `REFUSE_LOCAL_CAP`, `ALLOW_BY_PROFILE`.
  - `ALLOW_BY_PROFILE` (natural spawn of a listed type) makes `SpawnPlacementsMixin` use the profile's light conditions
    plus vanilla's ground rule instead of vanilla's light rule.
  - New `NaturalSpawnerMixin` on `mobsAt`: in a listed region, natural spawning picks monsters from the profile's
    weighted list (so cave spiders spawn at all). Other categories keep the biome's lists.
  - Local cap: `LevelThreats` counts non-persistent monsters in each capped region once a second; the decision reads it.
  - Only AMBIENT origins are affected; command, egg, spawner, derived spawns keep the P2B rules.

### Hotspot (`threat/Hotspot`, defined in the profile)
- Box around an anchor; while a player is within `activation_radius` (40), every `interval_ticks` (40) it spawns up to
  `spawns_per_run` (1) listed hostiles by weight while the box holds fewer than `target` (4) of them and fewer than `cap` (6)
  of its own tagged mobs are alive anywhere near the region.
- Own spot search (`SpawnSpots`): random columns in the box, each scanned top to bottom for every standable spot that
  meets the profile's conditions and keeps `min_player_distance` (8). Only loaded, entity-ticking columns.
- Mobs are ordinary, despawnable (reason SPAWNER, still passes the governance decision), tagged
  `inhabited_world.hotspot.<region>.<id>`. Respects peaceful, `spawn_mobs`, `spawn_monsters`.
- **Anchor:** `SiteAnchor` = offset from the site centre, vertical `surface` or `ground` (predicted median ground). It
  sits behind a `SiteAnchor.Resolver` interface: a semantic marker resolver (part 3) replaces it without touching the
  hotspot or encounter code.

### Guaranteed encounters (`encounter/`)
- Definition `data/<ns>/encounter/<id>.json`: entity, name, equipment, anchor, trigger radius, home radius, respawns.
  Listed per POI in its `poi_threats` file. Shipped: `placeholder_mine_foreman` (zombie, "Foreman (placeholder)", iron
  helmet + iron pickaxe, trigger 32, never respawns).
- Store: `EncounterData` SavedData, `data/inhabited_world/encounters.dat`, `data_version` 1, `world_id` = the WorldPlan's
  world ID (a store with another world's ID is emptied and logged). Per encounter: state, mob UUID, last position,
  spawn/defeat counts, last cause.
- State machine `EncounterRecord` (pure, unit-tested): NOT_SPAWNED → ACTIVE (spawned) → DEFEATED (its own mob died);
  ACTIVE → NOT_SPAWNED (lost: left the world without dying); DEFEATED → NOT_SPAWNED only if `respawns`; `reset` for dev.
- Runtime `EncounterRuntime` (once a second, plus Fabric entity load/unload/death events):
  - spawns lazily when a player is within the trigger radius and the anchor chunk is ticking (not in peaceful);
    persistence-required, tagged `inhabited_world.encounter` + `inhabited_world.encounter.<ns>.<path>`, home-restricted.
    No vanilla random extras (no baby, jockey or random gear): the definition is the whole mob.
  - record turns ACTIVE before the mob enters the world, so its own load event already sees it as legitimate
  - chunk unload: position recorded, state stays ACTIVE; the mob reloads with its chunk and is reconciled
  - any tagged mob that loads and isn't the current one (defeated, not spawned, other UUID, other dimension) is removed
  - death (player, `/kill`, lava) → DEFEATED at once, while the mob is still dying, so a mob dying as its chunk unloads is
    defeated, and its saved corpse is removed when it loads
  - removed without dying (discard, peaceful, portal) → lost → spawns again on the next approach
  - missing from fully loaded chunks around its last position for 5 checks → lost (lost save, edited world)
  - left its POI region with no target for 5 checks → teleported back to its spawn spot
- Query API for Phase 4 (`Encounters`): `state(server, id)`, `record(server, id)`, `entity(server, id)`, `ids(server)`.

## 2. Numbers
- Decision cost, `RegionLookupBenchmarkTest` (5 M calls, best of 5), `main` and this branch run back to back twice on
  the same machine and load (P2B's 5.5-9.5 / 8-15.5 ns were measured under other load):

| | main | this branch |
|---|---|---|
| lookup, wilderness fast path | 3.0 | 3.0-3.1 |
| full decision, anywhere on the map | 8.4-8.7 | 8.4 (one outlier run 13.3) |
| full decision, in town (refused) | 5.9-6.0 | 7.8-8.2 |
| full decision, in the mine, listed zombie | n/a | 9.6-16.4 |
| full decision, in the mine, unlisted creeper (refused) | n/a | 12.2-12.4 |
| bytes allocated by 5 M decisions | 24 | 24 (0 inside the mine) |

  Live level (`SpawnGovernance.decide`, incl. tag check and RPG identity): 19.0 ns per call in the game test (P2B: 15.5).
- Game tests, vanilla's natural spawner, 300 attempts per one-chunk chamber (buried 12 blocks, one run):

| chamber | spawns | types |
|---|---|---|
| POI, dark | 938 | zombie 447, skeleton 282, cave spider 209 |
| POI, lit (block light 2-4) | 951 | zombie 482, skeleton 258, cave spider 211 |
| POI, at its local cap | 0 | |
| wilderness, dark | 983 | zombie 262, skeleton 280, creeper 216, spider 190, enderman 19, slime 16 |
| wilderness, lit | 0 | |
| P2B surface chambers: town / wilderness / POI / town without regions | 0 / 962 / 923 / 1001 | POI: only the three |

- Hotspot game test: filled its box to target 4, refilled 2 to its cap of 6 after two left, then 0 more beyond the cap,
  0 without a player near.
- Encounter game test: spawned once on approach; one mob through a store save/load and a chunk unload/reload (same
  UUID, spawned 1x); `/kill` recorded DEFEATED (`minecraft:generic_kill`) while the mob was still dying; its chunk
  unloaded straight after; on reload the saved corpse was removed as a stale copy; with a player 3 blocks from the
  anchor it stayed defeated.

## 3. Mob caps (open plan item): answered
Persistence-required mobs do **not** count toward vanilla's mob caps. In 26.3 `NaturalSpawner.createState` skips every
`Mob` with `isPersistenceRequired() || requiresCustomPersistence()` before counting it for the global cap or the
per-player local cap (`LocalMobCapCalculator`). So encounter mobs can't starve wilderness or POI spawning. Hotspot mobs
are not persistent: they count like any natural hostile, at most 6 per hotspot. The POI's own local cap also counts only
non-persistent monsters, so the foreman doesn't use up the mine's 12 either.

## 4. Known issues
- Until part 3 builds the mine, the POI is natural terrain: the hotspot and natural spawning only find dark spots in caves
  under the site or on the surface at night. By day on the surface nothing spawns (correct, but quiet).
- Phantoms over the POI's surface part (up to 32 above its ground) are refused: they are not in the mine's list.
- Changed P2B behaviour (intended): the POI now spawns only the mine's three types, also in lit tunnels; the P2B surface
  chamber still gets ~920 spawns from 300 attempts (was ~990).
- A foreman led out of its region comes back by teleport after ~5 s without a target, possibly in view of the player.
- Encounter IDs are per definition, not per site: a catalog entry used by two sites would share one state (v0.1 has one).
- A store saved by a newer mod version (data_version > 1) is refused; the world then starts a fresh store and would
  overwrite the newer file at the next save (no downgrade support; same as elsewhere).
- Infrastructure: another session's `gradlew --stop` killed this session's Gradle daemon mid-run once (daemons are shared
  per user). Later runs used their own registry, `-Dorg.gradle.daemon.registry.base=<scratchpad>\daemon-registry`, passed
  to both the build and its `--stop`. Worth adding to the build brief's script.
- Game-test gotchas: mock players report creative but are survival (mobs target them; `setGameMode(CREATIVE)` fixes it),
  and they don't carry chunk tickets as they move (force-load chunks a test needs).

## 5. Open questions (with recommendations)
1. Should a `/kill` (or any non-player death) count as the quest enemy being defeated? Recommended: yes, as built; the
   record keeps the cause (`player` or the damage type) so Phase 4 can decide otherwise.
2. Should the foreman respawn if the quest is failed or reset? Recommended: never in v0.1 (as built); `respawns` exists for later POIs.
3. Block light up to 7 in the mine: keep it? Recommended: yes; it is what makes the mine differ from the wilderness in a way
   a player notices (torches don't make it safe). Part 3 can tune it to the build's lamps.
4. Phantoms above the mine: refuse (as built) or allow? Recommended: refuse; the region's sky part is small.

## 6. In-game checklist (`test-launch.bat`)
1. `/inhabited plan tp external_poi`. Within a second a zombie **"Foreman (placeholder)"** with an iron helmet and iron
   pickaxe appears near you (you're within 32 blocks of the POI's centre).
2. `/inhabited encounter list` → `inhabited_world:placeholder_mine_foreman: active, mob <uuid>, spawned 1x`.
3. `/inhabited spawnpolicy minecraft:creeper` → REFUSED, "allows only the hostiles it lists", threat profile
   `placeholder_abandoned_mine ... chosen by POI catalog entry inhabited_world:placeholder_external_poi`.
   `/inhabited spawnpolicy minecraft:cave_spider` → ALLOWED by the profile.
4. `/time set midnight`, `/inhabited hotspot` → `external_poi/workings: ACTIVE, population n of target 4, own mobs n of cap 6`.
   `/inhabited hotspot show` → soul-fire outline of its box. Within a minute up to 4 zombies/skeletons/cave spiders stand in it.
5. Torch test: in a cave under the POI place torches, wait at night: zombies, skeletons, cave spiders still appear. Same in
   a wilderness cave: nothing appears near torches. (Or compare `/inhabited spawnpolicy minecraft:zombie` in both.)
6. `/gamemode survival` and kill the foreman (or `/kill @e[tag=inhabited_world.encounter]`). `/inhabited encounter info
   inhabited_world:placeholder_mine_foreman` → `defeated ... (last by player)`.
7. Save and Quit, reopen the world **from the world list** (test-launch.bat would recreate it), go back to the POI: no
   foreman; info still says defeated.
8. `/inhabited encounter reset inhabited_world:placeholder_mine_foreman`, fly 300+ blocks away and back: one foreman
   appears; fly away again and back: still the same single foreman (info: same UUID, spawned count unchanged).
