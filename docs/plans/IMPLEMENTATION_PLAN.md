# Implementation Plan — Inhabited World

**Status:** Tier 5, NON-CANONICAL (see `docs/canon/00_DOCUMENT_AUTHORITY_AND_INDEX.md` §2). This is Claude's architecture/implementation plan for building the canon. It never overrides `docs/canon/`. If they disagree, canon wins and this file is corrected.

**Maintained by:** Claude (coding agent), with the user. Since 2026-09-28 Claude also maintains the canon itself, but changes it only with the user's explicit approval or direction (`00` §4–§7). Migrated on 2026-09-25 from Claude's private working notes when the canon moved from the monolithic v6 charter to the hierarchical corpus.

**Canon change log:**
- 2026-09-25: bootstrap (split corpus), from a ChatGPT package.
- 2026-09-28: managed local-AI provisioning (ChatGPT package; the CORRECTED one, since the first shipped a regressed `00`, which was caught and not installed). Changed `01`, `04`, `08`, `09`, `10`.
- 2026-09-28: SECTION_NUMBER_FIX (ChatGPT package). `01` heading `## 2.6` → `## 2.22`, one line.
- 2026-09-28: **governance + audit change set.** ChatGPT retired. The user approved all 23 audit findings. Changes:
  - `00`: Claude maintains canon under user approval. §4, §5 and §7 were rewritten; git is the audit trail; the `_archive` index no longer lists ad-hoc backups.
  - `01`: status paragraph (post-split role); §2.1 clarification (no non-AI mode ≠ every transition waits on inference); §2.22 defines "relevant session".
  - `02` §4: persisted RPG identity and per-dimension controls preferred over gamerules. `02` §6: new subsections on pre-generation, terrain beyond the boundary, and versioned worldgen config. `02` §8: facts from real terrain after pre-generation.
  - `04`:
    - §2.8/§2.12: no blind localhost probing.
    - Managed sections numbered §2.22.1–§2.22.8, plus §2.22.5.1 isolation and §2.22.6.1 distribution compatibility.
    - §2.22.3: v0.1 ships tier A only.
    - §2.22.5: lifecycle wording.
    - §23: output constraints in the request.
    - §24: decision first, presentation second.
    - §26: shared-first prompt order.
  - `05` §32: two monotonic clocks per event.
  - `08` §50: schema-as-transport. `08` §51: pin dev identity early.
  - `09`: Phase 2A (identity, boundary, pre-generation), Phase 10 (tier A, both provisioning paths), DoD 35–36.
  - `10`: §60/§61 stale self-references; provisioning discipline.
  - Removed: `_archive/CLAUDE_PRE_HIERARCHY_CHARTER_BACKUP.md` and two uncommitted backup folders (committed in f4423ed).
- 2026-09-28: **retitled "Minecraft: Inhabited" → "Inhabited World"** (user decision; 1d77ac7). `00` (title, workspace path, history note) and `04` §2.22.5.1. `MIGRATION_MANIFEST.md` and `_archive/` keep the old title.
- 2026-10-02: **local-inference evidence change set** (user-directed: "save these test results and your interpretation in the canon, fine-tune canon and plan"). Purely additive: 74 lines added, 0 removed. Evidence record: `docs/notes/2026-10-02_llm_stress_tests/REPORT.md`.
  - `04`:
    - new §2.14.1, the evidence index with key measurements
    - §2.22.3: tier validation is whole-system, with frame pacing; tier A sits at the ~4B Q4 class unless new evidence says otherwise
    - §28: gameplay-critical numbers are rendered by Java as literal text; verification reads number words
    - new §29.1: knowledge gating happens in Java
    - §36: "may" strengthened to "should": background yields to player-facing requests and doesn't run during active gameplay on the local path; dialogue-screen inference is the low-impact case
    - §37: frame pacing, not average FPS
    - §38: tier A sits at the low end of 3B–8B
  - `03` §33: AI-written memory summaries are non-authoritative and must be checked against source records or discarded.
  - `09` Phase 9: measure hitches, active-versus-static play, system RAM, and player latency with background work pending; record evidence.
  - `10` §60 Testing Strategy: experiments are recorded in `docs/notes/` and indexed in `04` §2.14.1.
  - **Follow-up the same day (user directive): the deferral rule is a REQUIREMENT, not a suggestion.** `04` §36 now has an "Architectural requirement" paragraph (inference scheduler; all non-player-waiting inference deferred until idle/static; bounded wait; no backend calls around the scheduler) and "must" wording, and `09` Phase 5 lists the scheduler as a deliverable. Open design item: define "idle/static" concretely (input inactivity threshold, screen types) in P5.
  - Conflict review: no conflict with `01`. Consistent with `01` §2.2/§2.3, `03` §34, `04` §24 conversation concurrency, and `07` §2.19 (the scheduling rule is scoped to the local single-player path).

**Section references** use the pre-split section numbers, which the canon files retain (see `docs/canon/MIGRATION_MANIFEST.md`). New sections get new unused numbers (`00` §7). File key:

| Sections | File |
|---|---|
| §1, §2.1–2.5, §2.22 (managed provisioning principle) | `01_PROJECT_CHARTER.md` |
| §4–§12 (incl. §6 subsections), §14, §16 | `02_WORLD_WORLDGEN_AND_POIS.md` |
| §18–19, §33–34, §39–43 | `03_NPC_SOCIAL_AND_MEMORY.md` |
| §2.6–2.14 (incl. §2.14.1 evidence), §2.22.1–§2.22.8 (incl. §2.22.5.1, §2.22.6.1), §2.20, §20–30 (incl. §29.1), §35–38 | `04_AI_ARCHITECTURE.md` |
| §31, §32, §44 | `05_QUESTS_AND_WORLD_HISTORY.md` |
| §13, §15, §17 | `06_HOSTILES_SPAWNING_AND_COMBAT.md` |
| §2.15–2.19, §58 | `07_MULTIPLAYER_AND_SERVER_ARCHITECTURE.md` |
| §2.21, §49–53 | `08_TECHNICAL_ARCHITECTURE_AND_PERSISTENCE.md` |
| §3, §54, §55, §56 | `09_V0_1_VERTICAL_SLICE.md` |
| §57, §60, §61 | `10_DEVELOPMENT_AND_AGENT_RULES.md` |
| §45–48, §59 | `11_LONG_TERM_SYSTEMS.md` |

Note: `04` §2.6 is BYOK. The managed-provisioning principle is `01` §2.22, and its details are `04` §2.22.x.

---

## Always-on checks (before any design/code handoff)

- v0.1 scope? `09` §55, `10` §60 scope priority, `10` §57 no speculative frameworks.
- AI involved?
  - `10` §60 AI discipline (facts, context, decision, output, validation, executor)
  - `01` §2.3, §2.5
  - `04` §23 plain data + output constraints; §24 lifecycle + decision-first
  - **§28 Java-rendered numbers; §29.1 knowledge gating; §36 scheduling (background yields, not during active play); §37 frame pacing**
- Spawn/hostile work? `10` §60 Hostile-System Discipline: entity type, spawn reason, region, policy, encounter state, persistence.
- MP hedge (`10` §60 Multiplayer Architectural Discipline, `08` §2.21): state lives server-side; client UI only via protocol messages; plain-data AI contracts; explicit world/request identity; validation at the authoritative boundary. Don't build networking.
- General:
  - blocking work stays off the game threads, through the injected handoff
  - nothing outside `ai/` knows a runtime
  - semantic IDs
  - everything scoped to the **persisted RPG-world identity** (`02` §4), never to the preset choice
  - outcome before presentation
- Handoff:
  - Format per `10` §60 Communication Style. The user types new code.
  - Claude may make mundane edits to EXISTING files only when the user authorizes that task. **Errors in code the user typed are answered with Before/After snippets for the user to apply.** Claude does not edit those files.
  - NEVER `git push` (`10` §57.1).
- Performance/model experiments get an evidence record in `docs/notes/` and an index entry in `04` §2.14.1 (`10` §60).

## Canon to re-read per phase

- **P1:** `08` §52, `09` §54 Phase 1, `10` §61.
- **P2A:** `02` §4–§8 (incl. §6 subsections), §11–§12, §14; `06` §13, §15; `08` §51; `09` §54 Phase 2A, DoD 1–9.
- **P2B:** `06` §17.1–17.6; `09` §54 Phase 2B, DoD 10, 13–15.
- **P3:** `02` §8–10, §16; `06` §17.3, §17.7–17.8, §17.12; `08` §49; `09` §54 Phase 3, DoD 16–19.
- **P4:** `01` §2.1 (deterministic affordances); `03` §18–19, §33–34; `05` §31–32 (two clocks); `04` §35; `08` §49, §51; `09` §54 Phase 4.
- **P5:** `01` §2.4; `07` §2.18; `04` §2.20, §20–27 (§23 output constraints, §24 decision-first), §36 (scheduling); `08` §50; `09` §54 Phase 5; `10` §60.
- **P6:** `04` §26–29 (§26 shared-first order, §28 numbers, §29.1 knowledge gating).
- **P7:** `01` §2.3, §2.5; `04` §30.
- **P8:** `05` §32; `03` §33 (incl. non-authoritative summaries), §34.
- **P9:** `04` §2.14 + §2.14.1, §36–38; `09` §54 Phase 9, DoD 37; `10` §60 Testing Strategy.
- **P10:**
  - `09` §54 Phase 10, DoD 35–36
  - `01` §2.22
  - `04` §2.8, §2.10, §2.12, §2.22.1–§2.22.8
  - `08` §52–53
  - `10` §60 Managed Local-AI Provisioning Discipline

---

## Project plan (decisions, each tied to canon)

**Baseline:** Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Loom **1.18.2** (pinned 2026-09-28), Java 25, split client/common sources. Mod version `0.1.0-dev`.

**Naming (2026-09-28):**

| What | Name |
|---|---|
| Title / mod name | Inhabited World |
| Mod ID / resource namespace | `inhabited_world` |
| Java package | `io.github.nick92461.inhabitedworld` |
| Classes | `InhabitedWorld`, `InhabitedWorldClient` |
| Mixin configs | `inhabited_world.mixins.json`, `inhabited_world.client.mixins.json` |
| Gradle project / Maven group | `inhabited_world` / `io.github.nick92461.inhabitedworld` |
| Workspace folder | `Inhabited World/` |
| Codebase folder | `inhabited_world_mod/` |
| GitHub repo | `nick92461/Inhabited-World` |

Before the rename these were "Minecraft: Inhabited", `inhabited`, `io.github.nick92461.inhabited`, `Minecraft Inhabited/`, `inhabited_mod/` and `Minecraft-Inhabited`.

**Workspace layout:** `docs/` lives at the `Inhabited World/` workspace root. The mod codebase is the CHILD directory `inhabited_world_mod/`. Never move `docs/` into the codebase. Research records live in `docs/notes/` (e.g. `docs/notes/2026-10-02_llm_stress_tests/`).

**Git (decided by the user 2026-09-25):**
- One repo at the WORKSPACE ROOT, pushed to GitHub by the user (never by Claude).
- Canon changes are committed separately from code (`00` §7). Stage docs with `git add docs`, not `git add .`, when unfinished code is in the working tree.
- The root `.gitattributes` marks `docs/canon/**` as `-text`, so `core.autocrlf=true` can't rewrite canon bytes.
- **CI:** `.github/workflows/build.yml` at the repo root, with working directory `inhabited_world_mod`, Gradle caching, and artifacts from `inhabited_world_mod/build/libs/`.

**Packages (`08` §53):** worldgen, worldstate, poi, region, npc, memory, dialogue, quest, simulation, spawn, ai/{backend, orchestration, context, validation, telemetry, config, runtime, hardware, provisioning}, persistence, network; client: ui. Each is created in the phase that first needs it.

**Dev identity (`08` §51):** pinned 2026-09-28 in `build.gradle` (`loom.runs.client.programArgs "--username", "Dev"`). The offline UUID is derived from the username.

### P2A build decisions

**RPG-world identity (`02` §4).** Minecraft doesn't persist the chosen preset, so identity is carried by two persisted things we own:
1. **Dimension type `inhabited_world:rpg_overworld`** (data; step 1, DONE):
   - a copy of vanilla `dimension_type/overworld.json` (26.3)
   - plus `minecraft:gameplay/can_start_raid: false`, `minecraft:gameplay/can_pillager_patrol_spawn: false`, `minecraft:gameplay/nether_portal_spawns_piglin: false`
   - Verified in the jar: exact attribute IDs; the only biome setting any of them is mushroom_fields (patrols `false`), so the dimension-level values hold.
   - A lit portal spawns zombified piglins in the Overworld even with no Nether, which is why that attribute matters.
2. **Noise-settings entry `inhabited_world:rpg_overworld`** (step 2):
   - a verbatim copy of vanilla `worldgen/noise_settings/overworld.json` (3.9 KB) under our ID, persisted in `level.dat` with the generator
   - `RpgWorlds.isRpg(ChunkGenerator)` = "is a `NoiseBasedChunkGenerator` whose `generatorSettings()` key is ours"
   - Also the home for step 4's ocean-boundary density work.
   - (A subclass generator was planned first, but `NoiseBasedChunkGenerator` is `final`. `02` §4 says "such as its own chunk-generator type", so this satisfies it.)
- **Versioned config (`02` §6):** once shipped, `rpg_overworld` (dimension type and noise settings) and any density configs are never edited in place. A change gets a new ID (e.g. `_v2`).
- Everything per-world checks `RpgWorlds.isRpg(...)`. The world requires the mod to load, which is acceptable.

**Spike steps:**
- **Step 1 (data only): DONE 2026-09-28, user-tested.**
  - Files: the dimension type; `worldgen/world_preset/rpg_world.json` (overworld stem only); tag `data/minecraft/tags/worldgen/world_preset/normal.json` (`replace: false`); lang `generator.inhabited_world.rpg_world` = "Inhabited World".
  - Results: world type listed; world looks normal; `/execute in minecraft:the_nether …` → `Unknown dimension 'minecraft:the_nether'`; save logs only `minecraft:overworld`.
  - Portal behaviour (26.3 code):
    - `BaseFireBlock.inPortalDimension(Level)` checks only the level KEY (OVERWORLD/NETHER), so a portal still lights.
    - `NetherPortalBlock.getPortalDestination` returns null without a Nether, so there's no teleport.
    - **Decision:** block ignition in RPG worlds with a mixin on `BaseFireBlock.inPortalDimension` (step 3).
- **Step 2 (Java; HANDED OFF 2026-09-28, in progress):**
  - Status: the user has `worldgen/RpgWorlds.java` (compiles) and the noise-settings copy. Still to apply: the two mixins, `inhabited_world.mixins.json`, `InhabitedWorld.java` (identity log line) and the one-line preset change (`settings: inhabited_world:rpg_overworld`). Then run the step 2 test: no warning; `/locate` finds no village or mineshaft; the identity log says `true`; a Default world says `false` and still finds villages.
  - **Structure allowlist:**
    - `ChunkGeneratorMixin`: `@ModifyArg` on the `ChunkGeneratorStructureState.createForNormal(...)` call inside `ChunkGenerator.createState`, index 4 (the `HolderLookup`). It swaps in `RpgWorlds.allowlisted(lookup)` when `isRpg`.
    - `allowlisted` is a `HolderLookup` view: `listElements` and `get(ResourceKey)` are filtered; tags delegate (4 abstract methods in 26.3).
    - Vanilla does all seeding, biome checks and ring placement unchanged.
    - Rejected: `createForFlat` (it uses concentric-ring seed `0L`, not the level seed).
  - **Experimental-warning fix:** there are two causes, both in `WorldDimensions.bake`:
    1. The registry is experimental unless overworld + nether + end all exist (`BUILTIN_ORDER`).
    2. A non-vanilla dimension type makes `checkStability` experimental, and entry lifecycles merge into the registry with `Lifecycle.add`.
    - The create screen reads `allRegistriesLifecycle()` and the load path reads `complete.lifecycle() + allRegistriesLifecycle()`, so the fix sits at the source.
    - `WorldDimensionsMixin`, active only when the overworld stem `isRpg`:
      - `@ModifyArg` on `MappedRegistry.<init>(ResourceKey, Lifecycle)` in `bake` (index 1) → stable
      - `@Inject` HEAD cancellable into private static `checkStability` → stable
    - Fallback if the constructor-target `@ModifyArg` fails: `@ModifyVariable` on the lifecycle local in `bake`.
    - Our datapack entries are stable: Fabric mod packs carry a `KnownPack`, and `ResourceManagerRegistryLoadTask` rates those `Lifecycle.stable()`.
    - The rejected first attempt mixed into `Complete.lifecycle()`, which the create screen never reads.
  - **Menu placement (user question):** a custom "game mode" button is Phase 10 UI polish (a client mixin on `CreateWorldScreen`). The persisted settings remain the real identity.
- **Step 3:** feature suppression, spawn hook and portal-ignition block (below).
- **Step 4:** pre-generation + boundary (below).

**Feature suppression (`06` §15):**
- A tiny mixin on `PlacedFeature.place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos)`: if `isRpg(generator)` and the placed feature is in the denylist, return false.
- Denylist: `monster_room`, `monster_room_deep` (both in 26.3), `desert_well`. Keep geodes, fossils, ores, lakes, springs and trees.
- **Re-audit the placed-feature list at every Minecraft version bump** (it's a denylist).

**Spawn governance hook (built at the START of P2A; P2B adds region rules):**
- **Primary choke point:** `SpawnPlacements.checkSpawnRules(EntityType, ServerLevelAccessor, EntitySpawnReason, BlockPos, RandomSource)`. It has entity, reason and position before construction, so it's allocation-free. First rule: the human-like type ban in RPG worlds. **Verify** which paths bypass it.
- **Wandering traders:** remove `WanderingTraderSpawner` (and `VillageSiege`) from the RPG overworld's `List<CustomSpawner>` (a `ServerLevel` constructor parameter). Keep `PhantomSpawner`.
- **Patrols, raids, portal piglins:** handled by the dimension-type attributes.
- **Portals:** block ignition via `BaseFireBlock.inPortalDimension`.
- Note: 26.3 has a `minecraft:gameplay/natural_mob_spawns` environment attribute. It's a possible data path, but per-region semantics still need the hook.

**Pre-generation and boundary (`02` §6, §8; step 4):**
- Generate the ~1024×1024 playable area (~4,096 chunks) at world creation with progress display, in batches with unloading. Measure time and memory.
- Generate cheap open ocean beyond the boundary, via a density-function wrapper in our noise settings or a generator-side override (decide in the spike).
- Set the world border from the WorldPlan at creation.
- The town and POI are placed through the structure system (beardifier blending). Pre-generation **verifies** they generated.
- Stress-test note: world building and loading caused most large hitches in run 1 (§ Local-inference evidence). Pre-generation removes runtime chunk generation from play.

**Other:** every AI request tag = permanent world ID + a per-load session ID (`07` §2.18 edge case).

### WorldPlan (`02` §6–8)
Created once, before the first chunk generates. Persisted and immutable. Contains:
- world ID
- bounds/border
- POI placements + bounds + structure identity
- 3D semantic regions
- spawn marker reference (optional, `02` §16)
- geography facts

Sites are chosen from deterministic noise sampling before generation. After pre-generation the planner **verifies** POI presence, and geography facts are derived from or checked against real blocks. The terrain sampler is built in P2A and extended in P3. Regions exist in P2B, before the P3 structures.

### Regions + spawn governance (`06` §17)
- Regions are 3D volumes. Most-specific-wins: POI > settlement > wilderness.
- One decision on (entity type, spawn reason, region). Natural and chunk-generation spawns are governed; commands, eggs and our encounters pass.
- Pure-Java rules plus a thin mixin, and an in-memory index from the WorldPlan with allocation-free lookup. Chunks entirely inside wilderness can skip region checks.
- Guaranteed encounters are persisted (not spawned / active / defeated), persistence-required and spawned lazily when a player approaches. **Verify in P3** that persistence-required mobs don't count toward mob caps.
- Debug command reporting region, policy, reason, entity and source POI (`10` §60).

### POIs (`02` §10)
- Each POI has a semantic ID, type, bounds box, SOURCE reference and threat-profile reference.
- Immutable facts live in the WorldPlan; mutable POI and encounter state get their own store.
- "Who knows about it" is answered by knowledge queries. These are the same queries `04` §29.1 knowledge gating uses.
- The v0.1 POI is handcrafted.

### NPCs (`03` §18–19)
The record is the person and holds a logical location (a semantic location ID). Bodies are reconciled against records, never saved as truth. Reputation is per player UUID.

### Persistence (`08` §49)
- Split stores, each with a dataVersion: WorldPlan (incl. regions + world ID); POI/encounter state; NPCs (incl. relationships keyed (npcId, playerUuid)); quests; event log; memories.
- **Growing stores are SEGMENTED:** fixed-size segments, each its own SavedData ID, plus a small index/tail. Only the tail is dirty; closed segments are never rewritten. Memories are segmented per NPC.
- Codecs are reused for model output (`08` §50).

### Quests / events / memory (`05` §31–32, `03` §33–34, `01` §2.1)
- QuestDefinition vs QuestInstance.
- A `revision` int on important mutable objects drives stale-result checks.
- **Every event records two clocks (`05` §32):** `ordering` = `getGameTime()`, and `calendarDay`, our own counter that increments on day-clock wraps including sleep and ignores backward `/time set`. NPC schedules follow the 26.x world day clock.
- Memories are created only by Java from validated events. **Any AI-written summary is non-authoritative** (`03` §33): it's checked against its source records before use as prompt or presentation material, or discarded. Stress tests: 17–22% of 4B summaries failed, and others embellished.
- **Deterministic quest affordances (`01` §2.1):** accept, progress and turn-in are plain interactions. An inference outage pauses AI dialogue with clear status and never blocks progression.

### AI (`04` §20–29, §2.20, §36–37; `08` §2.21)
- `AiBackend` → `LocalBackend` (OpenAI-compatible HTTP); `CloudBackend` later. Plain-data request/result.
- Context + validation always live in the owning Minecraft server.
- Process-wide service; config holds no secrets; backend selection only at startup/reconnect/user action; no blind localhost probing (`04` §2.12).
- Lifecycle per `04` §24. Injectable executor. ScriptedBackend tests (incl. player disconnect, wrong-world).
- **Decision first, presentation second (`04` §24), for action-bearing turns:**
  1. Call 1: the constrained decision only (temperature ~0.2–0.4, few tokens).
  2. Java validates and commits.
  3. Call 2: prose for the committed outcome, reusing call 1's cached prompt.
  - Pure chat is a single prose call. Streaming later needs sentence-level buffering so checks run before display.
  - Stress tests: 25/25 decisions legal; prose matched the committed outcome every time; 2/13 lines added a stray number.
- **Knowledge gating in Java (`04` §29.1):**
  - Interpret the intent and subject (deterministic patterns first, model-assisted classification only where needed).
  - Check the NPC's knowledge (`03` §34) with the same knowledge queries POIs use.
  - The prompt then either supplies the exact facts to state or says "you do not know this; say so in character".
  - Stress tests: without this, the 4B fabricated or wrongly denied facts in 4 of 29 geography answers.
- **Numbers rendered by Java (`04` §28):**
  - Distances, rewards, prices and counts are formatted by Java and placed in the prompt as literal text to repeat (e.g. "430 blocks").
  - The verifier parses digits *and* number words (as in `tools/stress.ps1` `Get-Numbers`), and a mismatch triggers a retry or a corrected template.
  - This supersedes the spike-era idea of "rounded/banded words". The 4B mis-spelled numbers in words ("eighteen hundred" for 180, "sixty-one" for 610).
- **Output constraints (`04` §23, `08` §50):** the request carries a backend-neutral `OutputConstraint` (allowed decisions, fields, int ranges computed from current state). `LocalBackend` translates it to llama.cpp `response_format` json_schema, and the same constraint drives Codec validation.
- **Prompt order (`04` §26):** shared instructions/output rules → shared common knowledge (respecting `03` §34) → NPC identity + NPC-specific knowledge → quest/state → memories → recent dialogue → input.
  - **Cache work (measured hit rate only 32–51%; warm calls took about half the time of cold ones):**
    - keep the per-NPC block byte-stable
    - order memories stably (by ID or time, not by relevance score)
    - put volatile material last
- **Scheduling (`04` §36; measured):**
  - The runtime serves **one request at a time** (`-np 1`) from a priority queue: player-facing first; background never concurrent with a player-facing request. Overlap cost the player 30–66% latency.
  - **Background inference only when the scene is static:** dialogue screens, menus, sleeping, idle (no input for N seconds), or paced at most one call per long interval otherwise. During active building, any generation roughly tripled frame-time spikes; a static scene showed almost none.
- Relevance-based trimming; token estimate + calibration.
- Deterministic prose checks: numbers (digits and words), directions, committed reward amounts, known names.
- Negotiation inputs per `04` §30 (personality, relationship, desperation, legal range). Quest prompts must state who pays whom: the test's ambiguous wording made the 4B invert the roles.
- Client UI reaches server state only through network payloads, even in single-player.
- **P5 constraint from the managed-provisioning canon:** `LocalBackend` receives its endpoint and an optional bearer token as constructor inputs.
- **llama-server config for single-player:** `-np 1`; keep the RAM prompt cache (`--cache-ram`) but cap it (and measure its share of the ~5 GB working set); flash attention and KV quantization to be measured.

### Managed local-AI provisioning (P10 only; `01` §2.22, `04` §2.22.1–§2.22.8, `10` §60)
Late v0.1 setup/polish. Planned shape:
- **Architecture:** `ManagedLocalBackend` = a runtime manager (infrastructure only: detect → provision → verify → launch → health → stop) plus the same `LocalBackend` client. No game or domain logic in the manager (`08` §53).
- **Scope for v0.1:** tier A (~8 GB) only, plus manual override.
  - **Tier A default model class: ~4B parameters at 4-bit** (`04` §2.22.3, §38; evidence `04` §2.14.1).
  - Current candidate: Qwen3.5-4B Q4_K_M.
  - Q6_K only on lean desktops, if measured.
  - Larger defaults need new evidence.
- **Runtime:** an official upstream llama.cpp `llama-server` release, pinned by version and sha256.
  - Vulkan build as the baseline; CUDA optional for NVIDIA, by benchmark.
  - Files live in a user-writable game-managed folder. No service, no elevation.
  - Below-normal CPU priority.
  - **To test: lowering the process's GPU scheduling priority, and smaller `-b`/`-ub` batches, as hitch mitigations.**
- **Lifecycle (decided 2026-09-28, `01` §2.22):** start when an RPG world begins opening; stop ~60 s after no RPG world is open; never outlive Minecraft.
- **Network isolation (`04` §2.22.5.1):**
  - `--host 127.0.0.1`
  - a per-launch `SecureRandom` token passed via environment variable (verify `LLAMA_ARG_API_KEY`), never on the command line or in logs
  - a free port, retried on bind failure
  - fail closed
  - The b11221 server warns "no API key is set and CORS allows all origins", which confirms the default is open. Also verify CORS controls. Upstream plans to change the default port to 9931, so always pass an explicit port.
- **Process lifetime:** graceful stop, then a forced kill. A Windows Job Object with `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE` via JNA `jna-platform` (Minecraft ships 5.17.0).
- **Hardware detection:** OSHI 6.9.0 (shipped with Minecraft) for `GraphicsCard` name + VRAM. Manual override always available.
- **Distribution (`04` §2.22.6.1):**
  - Automatic provisioning by default (consent screen, pinned official sources, hash verification).
  - Guided provisioning as the fallback.
  - Modrinth disclosure covers it. CurseForge to confirm with one support ticket (precedent: MCEF).
- **Tiers B/C:** after v0.1, from benchmarks (the 3060 12 GB as the first tier B datapoint). Model licences must allow redistribution or automatic download.
- **LAN servers** stay the advanced manual backend path.

### Future NPC-NPC
Canon `03` §40: outcome first; server-side perception; generate once and broadcast; drop presentation before outcome.

---

## Local-inference evidence

Full records live in `docs/notes/`. Canon indexes them in `04` §2.14.1.

### Spike run 1, 2026-09-28 (single requests, no load script)
- **Config:** RX 7600 8 GB / Ryzen 5 5600 / 16 GB, llama.cpp b11221 Vulkan (`-ngl 99 -c 8192 --jinja`), Qwen3.5-4B Q4_K_M (2.74 GB), Minecraft 26.3 dev client at Fancy, render distance 16.
- **Memory:** llama-server ≈ 2.7 GiB of VRAM, preallocated. Minecraft ≈ 1.4 GiB *at that moment*; it later measured 1.9–2.1 GB under play.
- **Speed:** generation 30–47 tok/s; warm prompt processing 330–450 tok/s; short replies 0.5–2.2 s.
- **Prefix cache:** an 88-token prompt dropped from 4.8 s to 0.5 s.
- **FPS:** ~120 → ~80 minimum during generation.
- **Quality failures:** "forty-three" for 430; invented facts; `ACCEPT` at 30 above a max of 25 (twice). These led to the legal-action-set output constraint, low decision temperature, and decision-first. (The spike-era "banded number words" idea is superseded by Java-rendered numbers, `04` §28.)

### Stress tests runs 1–2, 2026-10-02
**Full record:** `docs/notes/2026-10-02_llm_stress_tests/REPORT.md`, with raw data, tools and analysis scripts.

| | Run 1 (heavy desktop, unlimited FPS) | Run 2 (minimal desktop, vsync 120 Hz) |
|---|---|---|
| Calls / failures | 77 / 0 | 83 / 0 |
| Player-facing p50 / p95 | ≈ 3.0–4.3 s / 6.4–8.5 s | ≈ 3.6–4.2 s / 5.5–7.2 s |
| Generation alone / overlapped by background | 23.6 / 15.7 tok/s | 24.0 / 16.2 tok/s |
| Peak VRAM (of 8,192 MB) | 7,108 | 6,182 |
| Runtime + Minecraft VRAM | ~4.9 GB | ~4.7 GB |
| System RAM available (min) | 279 MB | 549 MB |
| llama-server RAM working set | (not logged) | ~5.1 GB |

**Conclusions** (confidence as rated in the report):
- Tier A stays in the 4B Q4 class (H).
- Background must yield and must not run during active play (H).
- Generation stutters the game only when the game is also busy: a static scene stayed clean with the model 63% busy, while building tripled frames over 33 ms (p = 0.008) (H).
- The 4B's failures are knowledge gating, number spelling and summaries, not decisions (H).
- The mechanism (GPU contention, plus a possible paging contribution) is unproven (M).

**Pass criteria (my proposal) against results:**

| Criterion | Result |
|---|---|
| Hard validation failures = 0 | **pass** in both runs |
| VRAM ≤ ~6 GB under a heavy desktop | fail at 7.1 (our own ~4.9 GB is fine) |
| FPS floor ≥ ~60 during generation | not met in either run, but Minecraft's own floor was also below 60 without the model |
| Cold 2k-token prompt ≤ ~5–6 s | borderline: 1.1–1.4k-token cold prompts took 3.7–6.0 s |

The criteria should be revised to frame-pacing terms after the scheduling run.

**Tools:** `tools/stress.ps1` and `tools/gpulog.ps1` in the record folder. Working copies are in `C:\llm\`. AMD logging goes to `C:\Users\Nick\AppData\Local\AMD\CN\`.

### Next experiments (in order)
1. `-np 1` + `-DeferBackground`, with half the run standing still and half building.
2. Flash attention + `q8_0` KV cache (VRAM, speed).
3. 4B Q6_K on a lean desktop.
4. GPU scheduling priority and smaller batches as hitch mitigations.
5. llama-server RAM: `--cache-ram 256/0`, `-np 1`, `--no-mmap`.
6. A launcher-like JVM heap (`-Xmx2G`); GC logging if large hitches persist.
7. Prefix-cache hit rate after stabilizing the per-NPC prefix and memory order.
8. The 3060 12 GB as a tier B / LAN datapoint.
9. A decision-first versus single-call A/B on latency.

---

## Open items
- **Colibri / GLM-5.2 (744B MoE, experts streamed from NVMe), evaluated 2026-10-02: NOT for v0.1 or any player-waiting path.**
  - Reported speed is ~0.05–1.06 tok/s, with ~370 GB of weights on disk and one generation at a time.
  - Possible future niche: a rare background "Director" on the LAN server (`11` §45).
  - Revisit after v0.1 through the manual backend path, benchmarked first.
- OS keychain dependency when BYOK is built (`04` §2.11 vs `08` §52). Future only.
- Desert-well suppression is Claude's implementation call under `06` §15; the user may keep them.
- Pass criteria to be rewritten in frame-pacing terms (hitch rate in active versus static play) after the scheduling experiment.

## Risks to verify
- **8 GB VRAM with Minecraft:** measured 2026-10-02. Our own footprint is ~4.7–4.9 GB, leaving ~1 GB on a heavy desktop and ~2 GB on a minimal one. Shaders or higher render distance would eat this. Re-check when finalizing tier A and the published requirements.
- **16 GB system RAM:** llama-server holds ~5 GB of RAM. Measure the reducible part, and check a launcher-like game heap.
- P2A spike:
  - Does anything in 26.3 assume the vanilla overworld dimension type or noise settings?
  - constructor-target `@ModifyArg` in `bake`
  - `createState` filtering
  - `PlacedFeature.place` mixin
  - `SpawnPlacements.checkSpawnRules` coverage
  - custom-spawner list injection
  - portal-ignition block
  - pre-generation time/memory
  - ocean-boundary approach
- Distribution: CurseForge confirmation of automatic runtime download (fallback: guided path).
- Model licence terms for automatic download.
