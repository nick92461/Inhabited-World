# Implementation Plan — Minecraft: Inhabited

**Status:** Tier 5, NON-CANONICAL (see `docs/canon/00_DOCUMENT_AUTHORITY_AND_INDEX.md` §2). This is Claude's architecture/implementation plan for building the canon. It never overrides `docs/canon/`. If they disagree, canon wins and this file is corrected.

**Maintained by:** Claude (coding agent), with the user. Since 2026-09-28 Claude also maintains the canon itself, but changes it only with the user's explicit approval (`00` §4–§7). Migrated on 2026-09-25 from Claude's private working notes when the canon moved from the monolithic v6 charter to the hierarchical corpus.

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
    - Managed sections numbered §2.22.1–§2.22.8, plus §2.22.5.1 isolation (threat model, credential channel) and §2.22.6.1 distribution compatibility (automatic + guided paths).
    - §2.22.3: v0.1 ships tier A only.
    - §2.22.5: lifecycle wording.
    - §23: output constraints in the request.
    - §24: decision first, presentation second.
    - §26: shared-first prompt order.
  - `05` §32: two monotonic clocks per event.
  - `08` §50: schema-as-transport. `08` §51: pin dev identity early.
  - `09`: Phase 2A (identity, boundary, pre-generation), Phase 10 (tier A, both provisioning paths), DoD 35–36.
  - `10`: §60/§61 stale self-references; provisioning discipline (token channel, both paths, prefer libraries Minecraft already ships).
  - Removed from disk: `_archive/CLAUDE_PRE_HIERARCHY_CHARTER_BACKUP.md` (in git at eaee97e) and the two uncommitted `_archive/PRE_2026-09-28_*` backup folders (the first equals eaee97e's canon; the second differed by the one heading line above). **The user performs these deletions.**

**Section references** use the pre-split section numbers, which the canon files retain (see `docs/canon/MIGRATION_MANIFEST.md`). New sections get new unused numbers (`00` §7). File key:

| Sections | File |
|---|---|
| §1, §2.1–2.5, §2.22 (managed provisioning principle) | `01_PROJECT_CHARTER.md` |
| §4–§12 (incl. §6 subsections Pre-Generation / Terrain Beyond the Boundary / Versioned Config), §14, §16 | `02_WORLD_WORLDGEN_AND_POIS.md` |
| §18–19, §33–34, §39–43 | `03_NPC_SOCIAL_AND_MEMORY.md` |
| §2.6–2.14, §2.22.1–§2.22.8 (incl. §2.22.5.1, §2.22.6.1), §2.20, §20–30, §35–38 | `04_AI_ARCHITECTURE.md` |
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
- AI involved? `10` §60 AI discipline (facts, context, decision, output, validation, executor); `01` §2.3, §2.5; `04` §23 plain data + output constraints; §24 lifecycle + decision-first.
- Spawn/hostile work? `10` §60 Hostile-System Discipline: entity type, spawn reason, region, policy, encounter state, persistence.
- MP hedge (`10` §60 Multiplayer Architectural Discipline, `08` §2.21): state lives server-side; client UI only via protocol messages; plain-data AI contracts; explicit world/request identity; validation at the authoritative boundary. Don't build networking.
- General:
  - blocking work stays off the game threads, through the injected handoff
  - nothing outside `ai/` knows a runtime
  - semantic IDs
  - everything scoped to the **persisted RPG-world identity** (`02` §4), never to the preset choice
  - outcome before presentation
- Handoff format per `10` §60 Communication Style. The user types new code. On 2026-09-28 the user authorized Claude to make mundane edits to EXISTING files for the audit changes. That authorization doesn't cover new files or large unseen code. NEVER `git push` (`10` §57.1).

## Canon to re-read per phase

- **P1:** `08` §52, `09` §54 Phase 1, `10` §61.
- **P2A:** `02` §4–§8 (incl. §6 subsections), §11–§12, §14; `06` §13, §15; `08` §51; `09` §54 Phase 2A, DoD 1–9.
- **P2B:** `06` §17.1–17.6; `09` §54 Phase 2B, DoD 10, 13–15.
- **P3:** `02` §8–10, §16; `06` §17.3, §17.7–17.8, §17.12; `08` §49; `09` §54 Phase 3, DoD 16–19.
- **P4:** `01` §2.1 (deterministic affordances); `03` §18–19, §33–34; `05` §31–32 (two clocks); `04` §35; `08` §49, §51; `09` §54 Phase 4.
- **P5:** `01` §2.4; `07` §2.18; `04` §2.20, §20–27 (§23 output constraints, §24 decision-first); `08` §50; `09` §54 Phase 5; `10` §60.
- **P6:** `04` §26–29 (§26 shared-first order).
- **P7:** `01` §2.3, §2.5; `04` §30.
- **P8:** `05` §32; `03` §33–34.
- **P9:** `04` §36–38, §2.14; `09` DoD 37.
- **P10:**
  - `09` §54 Phase 10, DoD 35–36
  - `01` §2.22
  - `04` §2.8, §2.10, §2.12, §2.22.1–§2.22.8
  - `08` §52–53
  - `10` §60 Managed Local-AI Provisioning Discipline

---

## Project plan (decisions, each tied to canon)

**Baseline:** Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Loom **1.18.2 (pinned 2026-09-28; was the floating `1.18-SNAPSHOT`, which resolved to the same artifacts)**, Java 25, split client/common sources. Mod version `0.1.0-dev`.

**Workspace layout:** `docs/` lives at the `Minecraft Inhabited/` workspace root. The mod codebase is the CHILD directory `inhabited_mod/`. Never move `docs/` into the codebase.

**Git (decided by the user 2026-09-25):**
- One repo at the WORKSPACE ROOT, pushed to GitHub by the user (never by Claude).
- Canon changes are committed separately from code (`00` §7).
- The root `.gitattributes` marks `docs/canon/**` as `-text`, so `core.autocrlf=true` can't rewrite canon bytes.
- **CI:** GitHub only reads workflows at the repo root. The workflow was edited in place (working directory `inhabited_mod`, Gradle caching, artifact path) and must be moved by the user to `.github/workflows/build.yml`. The repo is private, so runs use the free Actions minutes; Gradle caching keeps each run short.

**Packages (`08` §53):** worldgen, worldstate, poi, region, npc, memory, dialogue, quest, simulation, spawn, ai/{backend, orchestration, context, validation, telemetry, config, runtime, hardware, provisioning}, persistence, network; client: ui. Each is created in the phase that first needs it.

**Dev identity (`08` §51):** pinned 2026-09-28 in `build.gradle` (`loom.runs.client.programArgs "--username", "Dev"`). The offline UUID is derived from the username.

### P2A build decisions

**RPG-world identity (`02` §4). This fixes the flaw found in the audit.** Minecraft doesn't persist the chosen preset, so identity is carried by two things we own:
1. **Dimension type `inhabited:rpg_overworld`** (data, spike step 1):
   - a copy of vanilla `dimension_type/overworld.json` (26.3)
   - plus `minecraft:gameplay/can_start_raid: false`, `minecraft:gameplay/can_pillager_patrol_spawn: false`, `minecraft:gameplay/nether_portal_spawns_piglin: false`
   - Verified in the jar: exact attribute IDs; the only biome setting any of them is mushroom_fields, which sets patrols `false`, so the dimension-level values hold.
   - Portals matter for piglins because a lit portal spawns zombified piglins in the Overworld even when no Nether exists.
2. **Chunk-generator type `inhabited:rpg`** (Java, spike step 2):
   - a subclass of `NoiseBasedChunkGenerator` with its own `MapCodec`, registered in the chunk-generator registry
   - It overrides `ChunkGenerator.createState(HolderLookup<StructureSet>, RandomState, long)` (public in 26.3) to pass a filtered lookup. That gives a per-world structure **allowlist** with no mixin. Initially the list is empty; our town/POI sets come later.
   - An allowlist automatically excludes structures that future MC versions add (26.x added `abandoned_camp`).
   - Everything per-world checks the level's generator type or dimension type key. The world requires the mod to load, which is acceptable.
- **Versioned config (`02` §6):** once shipped, `rpg_overworld`, the `rpg` generator settings and any noise/density configs are never edited in place. A change gets a new ID (e.g. `_v2`).

**Spike steps:**
- **Step 1 (data only; re-handed 2026-09-28, result pending):**
  - `data/inhabited/dimension_type/rpg_overworld.json`
  - `data/inhabited/worldgen/world_preset/rpg_world.json` (overworld stem only, `"type": "inhabited:rpg_overworld"`, vanilla noise generator + `minecraft:overworld` settings for now)
  - tag `data/minecraft/tags/worldgen/world_preset/normal.json` (`replace: false`)
  - lang `generator.inhabited.rpg_world`
  - `WorldPreset.requireOverworld` requires only the overworld (verified).
  - Test: preset listed; world creates and looks normal; no nether/end "Saving chunks" lines; portal-lighting outcome; `/execute in minecraft:the_nether …` response; no dimension-type errors in the log.
- **Step 2 (Java):** the `inhabited:rpg` generator type + structure allowlist + a log line proving identity survives reload.
- **Step 3:** feature suppression and the spawn hook (below).
- **Step 4:** pre-generation + boundary (below).

**Feature suppression (`06` §15):**
- A tiny mixin on `PlacedFeature.place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos)`: if the generator is ours and the placed feature is in the denylist, return false.
- Denylist: `monster_room`, `monster_room_deep` (both exist in 26.3) and `desert_well`.
- Keep geodes, fossils, ores, lakes, springs and trees.
- Features use a denylist, so **re-audit the placed-feature list at every Minecraft version bump.**

**Spawn governance hook (built at the START of P2A; P2B adds region rules):**
- **Primary choke point:** `SpawnPlacements.checkSpawnRules(EntityType, ServerLevelAccessor, EntitySpawnReason, BlockPos, RandomSource)`. It has entity, reason and position before the entity is constructed, so it's allocation-free. First rule: the human-like type ban in RPG worlds (natural witch and zombie-villager spawns). **Verify** which spawn paths bypass it; any bypassing path that can produce a prohibited type gets its own narrow hook.
- **Wandering traders:** remove `WanderingTraderSpawner` from the RPG overworld's `List<CustomSpawner>` (a `ServerLevel` constructor parameter), rather than relying on a gamerule players can change. The same approach can drop `VillageSiege`. Keep `PhantomSpawner`.
- **Patrols, raids, portal piglins:** handled by the dimension-type attributes above.
- **Portals:** block Nether-portal ignition in RPG worlds (`02` §4). Final approach depends on the step-1 portal result.
- Note: 26.3 has a `minecraft:gameplay/natural_mob_spawns` environment attribute. It's a possible data path for spawn lists, but per-region semantics still need the Java hook.

**Pre-generation and boundary (`02` §6, §8; spike step 4):**
- Generate the full ~1024×1024 playable area (~4,096 chunks) during world creation with a progress display. Measure time and memory on the dev machine, and generate in batches with unloading.
- Beyond the boundary, generate cheap open ocean, which is also the natural boundary. Approach to decide in the spike: a custom density-function wrapper around continentalness in our noise settings, or a generator override.
- Set the world border from the WorldPlan at creation.
- The town and POI are still placed through the structure system with a custom placement from the WorldPlan, which gives terrain blending via the beardifier. Pre-generation then **verifies** they generated.

**Other:** every AI request tag = permanent world ID + a per-load session ID. Copying a save folder duplicates the world ID; the session ID stops a late result landing in the copy (`07` §2.18 edge case).

### WorldPlan (`02` §6–8)
Created once, before the first chunk generates. Persisted and immutable. Contains:
- world ID
- bounds/border
- POI placements + bounds + structure identity
- 3D semantic regions
- spawn marker reference (optional, `02` §16)
- geography facts

Sites are chosen from deterministic noise sampling before generation. After pre-generation, the planner **verifies** POI presence, and geography facts are derived from or checked against real blocks (e.g. whether a river really lies between town and mine). The terrain sampler is built in P2A (placement) and extended in P3 (facts). Regions exist in P2B, from the plan, before the P3 structures.

### Regions + spawn governance (`06` §17)
- Regions are 3D volumes. Most-specific-wins precedence: POI > settlement > wilderness.
- One decision on (entity type, spawn reason, region). Natural and chunk-generation spawns are governed; commands, eggs and our encounters pass.
- Pure-Java rules plus a thin mixin. An in-memory index built from the WorldPlan, with allocation-free lookup on the tick thread. A per-chunk fast path is possible: chunks entirely inside wilderness skip region checks.
- Guaranteed encounters are persisted (not spawned / active / defeated). Their mobs are persistence-required and don't respawn on reload. Spawn them lazily when a player approaches. Vanilla should exclude persistence-required mobs from mob-cap counting; **verify in P3** so hotspots don't starve wilderness spawning.
- Debug command reporting region, policy, reason, entity and source POI (`10` §60 Favor Inspectability).

### POIs (`02` §10)
- Each POI has a semantic ID, type, bounds box, SOURCE reference (our structure now, vanilla later) and threat-profile reference.
- Immutable facts live in the WorldPlan; mutable POI state and encounter state get their own store.
- "Who knows about it" is answered by knowledge queries.
- The v0.1 POI is handcrafted.

### NPCs (`03` §18–19)
The record is the person and holds a logical location (a semantic location ID). Bodies are reconciled against records, never saved as truth. Reputation is per player UUID.

### Persistence (`08` §49)
- Split stores, each with a dataVersion:
  - WorldPlan (incl. regions + world ID)
  - POI/encounter state
  - NPCs (incl. relationships keyed (npcId, playerUuid))
  - quests
  - event log
  - memories
- **Growing stores are SEGMENTED (decided 2026-09-28; replaces "SavedData is fine").**
  - SavedData re-encodes a whole object on the server thread at each save, and `08` §49 forbids full rewrites of growing history.
  - The event log and memories are therefore stored as a sequence of fixed-size segments, each its own SavedData ID, plus a small index/tail.
  - Only the open tail segment is ever dirty; closed segments are never rewritten.
  - Memories are segmented per NPC.
- Codecs are reused for model output (`08` §50).

### Quests / events / memory (`05` §31–32, `03` §33–34, `01` §2.1)
- QuestDefinition vs QuestInstance.
- A `revision` int on important mutable objects drives stale-result checks.
- **Every event records two clocks (`05` §32):**
  - `ordering` = `getGameTime()`: monotonic, and it does not advance during sleep skips.
  - `calendarDay` = our own counter in world state. It increments when the world's day clock wraps, including sleep skips, and ignores backward `/time set`.
  - 26.x has world clocks and timelines (`default_clock: minecraft:overworld`, `timeline/day`, even `timeline/villager_schedule`). NPC schedules (P4) follow the day clock.
- Memories are created only by Java from validated events.
- **Deterministic quest affordances (`01` §2.1):** accept, progress and turn-in exist as plain interactions that don't need inference. AI enriches them. An inference outage pauses AI dialogue with clear status and never blocks deterministic progression.

### AI (`04` §20–27, §2.20; `08` §2.21)
- `AiBackend` → `LocalBackend` (OpenAI-compatible HTTP); `CloudBackend` later. Plain-data request/result.
- Context + validation always live in the owning Minecraft server.
- Process-wide service; config holds no secrets; backend selection only at startup/reconnect/user action. No blind localhost probing (`04` §2.12): the endpoint is either the managed runtime or explicitly configured.
- Lifecycle per `04` §24. Injectable executor. ScriptedBackend tests (incl. player disconnect, wrong-world).
- **Decision first, presentation second (`04` §24), for action-bearing turns:**
  - Call 1 is the constrained decision only (low temperature ~0.2–0.4, few tokens).
  - Java validates and commits it.
  - Call 2 generates prose for the committed outcome (higher temperature). It reuses call 1's cached prompt.
  - Pure chat turns are a single prose call.
  - Streaming prose to the UI becomes possible later, but number/direction checks must happen before display (sentence-level buffering).
- **Output constraints (`04` §23, `08` §50):**
  - The request carries a backend-neutral `OutputConstraint`: allowed decisions, fields, and int ranges computed from current state (e.g. drop `ACCEPT` when the ask exceeds the max).
  - `LocalBackend` translates it to llama.cpp `response_format` json_schema.
  - The same constraint drives Codec validation.
- **Prompt order (`04` §26):**
  - shared instructions/output rules
  - shared common knowledge (respecting `03` §34)
  - NPC identity + NPC-specific knowledge
  - quest/state, memories, recent dialogue, input
  - One cached prefix then serves every NPC.
- Relevance-based trimming; token estimate + calibration.
- Deterministic prose checks: directions, committed reward amounts, known names.
- Negotiation inputs per `04` §30 (personality, relationship, desperation, legal range); the current offer comes from the QuestInstance.
- Client UI reaches server state only through network payloads, even in single-player.
- **P5 constraint from the managed-provisioning canon (not P5 scope creep):** `LocalBackend` receives its endpoint and an optional bearer token as constructor inputs. P10's managed backend then reuses the same client with a per-launch port and token that are never persisted.
- **llama-server config for single-player:**
  - `-np 1`: one conversation at a time (`04` §24), full context per slot.
  - **Keep** the RAM prompt cache (`--cache-ram`), which holds other NPCs' prefixes, but cap it (e.g. 1–2 GB) given 16 GB system RAM. Do NOT set it to 0.
  - Measure in P9.

### Managed local-AI provisioning (P10 only; `01` §2.22, `04` §2.22.1–§2.22.8, `10` §60)
Late v0.1 setup/polish. The early milestones are unchanged. Planned shape:
- **Architecture:** `ManagedLocalBackend` = a runtime manager (infrastructure only: detect → provision → verify → launch → health → stop) plus the same OpenAI-compatible `LocalBackend` client. No game or domain logic lives in the manager (`08` §53).
- **Scope for v0.1:** tier A (~8 GB) only, plus manual override (`04` §2.22.3). Spike run 1 (Qwen3.5-4B Q4_K_M) is the first tier-A datapoint.
- **Runtime:** an official upstream llama.cpp `llama-server` release, pinned by version and sha256.
  - Vulkan build as the baseline; it's proven on the RX 7600 and also runs on NVIDIA. CUDA is an optional faster NVIDIA package, decided by benchmark.
  - Files live in a user-writable game-managed folder. No service, no elevation.
  - Launched at **below-normal CPU priority** to reduce contention with Minecraft's threads.
- **Lifecycle (decided 2026-09-28, `01` §2.22):**
  - Start when an RPG world begins opening, so model load overlaps world load.
  - Stop when no RPG world is open, after a grace period of ~60 s (tunable).
  - Never outlive the Minecraft process.
- **Network isolation (`04` §2.22.5.1):**
  - `--host 127.0.0.1`
  - a per-launch token from `SecureRandom`, held in memory only and **passed to the child via environment variable** (llama.cpp reads `LLAMA_ARG_*` env vars; verify `LLAMA_ARG_API_KEY` at P10), never on the command line
  - never log the launch command or the environment
  - a free port chosen at launch, retried on bind failure
  - fail closed if any of these can't be applied
  - Verify at P10: llama-server's CORS defaults, and whether it accepts port 0.
- **Process lifetime:**
  - Normal path: graceful stop, then forced kill.
  - Crash-safety: a Windows Job Object with `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE` via **JNA `jna-platform` (Kernel32), which Minecraft 26.3 already ships** (5.17.0). No FFM native-access code of our own.
- **Hardware detection:** **OSHI 6.9.0 (shipped with Minecraft 26.3)**, `GraphicsCard` name + VRAM, with no new dependency. Manual override is always available.
- **Distribution (`04` §2.22.6.1), decided:**
  - **Automatic provisioning is the default everywhere:** an in-game consent screen ("Additional AI files required: X GB — Download and Continue"), then download from pinned official sources (llama.cpp GitHub releases; the model from Hugging Face) with hash verification.
  - **Guided provisioning is the built-in fallback:** a button opens the official download page, and the mod finds, verifies and installs the file. It's used where a platform requires it, or when a download is blocked (proxies, firewalls).
  - Research 2026-09-28:
    - Modrinth's disclosure rule concerns *uploading data to remote servers* the user didn't choose. Localhost inference sends nothing off the machine, so it's out of scope. The download is covered by the consent screen + page disclosure.
    - CurseForge's "External download links for files are not allowed" appears aimed at listings linking out instead of hosting files on CurseForge. CurseForge also bans executables inside uploads. Neither rule addresses runtime downloads.
    - Precedent: MCEF auto-downloads Chromium native binaries on first launch and is published on both CurseForge and Modrinth.
    - **Before the first CurseForge publication:** confirm with CurseForge support (one ticket).
  - Every listing discloses the extra download, its size, contents and sources.
- **Tiers:** B/C come after v0.1 from benchmarks. Model licences must allow redistribution or automatic download (e.g. Qwen = Apache-2.0; Gemma = Gemma Terms, with a notice pass-through requirement).
- **LAN servers** (e.g. the 3060 PC) stay the advanced manual backend path, which canon explicitly allows. They are dev convenience, never the managed default.

### Future NPC-NPC
Canon `03` §40: outcome first; server-side perception; generate once and broadcast; drop presentation before outcome.

### Post-M1 inference spike: run 1 DONE 2026-09-28 (single GPU)
Config: RX 7600 8 GB / Ryzen 5 5600 / 16 GB RAM, Windows. llama.cpp b11221 Vulkan (`llama-server -ngl 99 -c 8192 --jinja`, 4 slots, unified KV). Model: Qwen3.5-4B Q4_K_M (2.74 GB), thinking disabled per request via `chat_template_kwargs.enable_thinking=false`. Minecraft 26.3, Fancy, render distance 16.

- **VRAM (per-process, Task Manager Details):**
  - llama-server ≈ 2.8M KB ≈ **2.7 GiB**, fully preallocated at load; it does not change while serving requests
  - Minecraft (`java.exe` under runClient) ≈ 1.5M KB ≈ **1.4 GiB** at Fancy / render distance 16
  - total with both ≈ 4.9/8.0 GB (the rest is Windows/other apps), so **~3 GB headroom**
  - the earlier 4.1 → 4.9 GB rise was Minecraft loading chunks, not the model
- **Speed:**
  - generation ~30–47 tok/s
  - warm prompt processing ~330–450 tok/s (the first-ever request pays warm-up: 24 tok/s)
  - short NPC reply ≈ 0.5–2.2 s end to end
- **Prefix cache:** a repeated 88-token prompt reused 84 tokens and went 4.8 s → 0.5 s. This strongly validates stable-prefix prompt order (`04` §26).
- **FPS with Minecraft:** ~120 idle → ~80 minimum during generation, instant recovery (repeated runs). One early 24 FPS dip ~3 s after a reply did not reproduce, so it was a coincidence. Acceptable per `04` §37.
- **Quality failures observed (4B, temp 0.7). Each is exactly what canon validation exists for:**
  - misread number: "forty-three blocks" instead of 430
  - invented facts ("old mill", "ridge", "a mile")
  - negotiation `ACCEPT` at 30 when max = 25, TWICE, once with prose saying 25 (the `04` §24 prose/state mismatch case, now handled structurally by decision-first)
- **Resulting implementation decisions:**
  - Compute the **legal action set per request** in Java and encode it in that request's output constraint: drop `ACCEPT` when the ask exceeds the max, and constrain `amount` with a min/max range. Constrained decoding then makes illegal output impossible. Validation still runs.
  - Decision calls use a low temperature (~0.2–0.4); flavour dialogue can run higher.
  - Present numbers to the model in model-friendly form (rounded/banded words), and deterministically check any number or direction it states.
  - Keep context minimal and factual to reduce invented details. Consider an explicit "only state facts listed above" instruction plus spot checks.
- **Still TODO:**
  - Q6_K quality comparison (3.53 GB; fits in the headroom)
  - run 2 on the RTX 3060 12 GB server PC over LAN (Gemma 4 E4B / larger models). This doubles as the first 12 GB tier datapoint. A LAN endpoint is the advanced manual path, so binding it beyond loopback there is a deliberate dev choice.
  - a lean single-player config: `-np 1` with a capped `--cache-ram` (not 0)
  - a decision-first A/B: two-call vs single-call latency and correctness on the negotiation test

---

## Open items
- OS keychain dependency when BYOK is built (`04` §2.11 vs `08` §52 "avoid unnecessary dependencies"). Future only.
- Desert-well suppression is Claude's implementation call under `06` §15; the user may keep them.
- **Pending user actions (2026-09-28):**
  - move `inhabited_mod/.github` to the repo root
  - delete the two Example mixin classes (the configs are already emptied)
  - delete the three redundant `_archive` items
  - commit canon and code separately

## Risks to verify
- 8 GB VRAM shared with Minecraft: VALIDATED by spike run 1 (≈3 GB headroom at render distance 16). Re-check with heavier settings/shaders when tiers are finalized.
- P2A spike:
  - Does anything in 26.3 assume the overworld uses the vanilla `minecraft:overworld` dimension type?
  - generator-subclass codec + registration
  - `createState` filtering
  - `PlacedFeature.place` mixin
  - `SpawnPlacements.checkSpawnRules` coverage (which paths bypass it)
  - custom-spawner list injection
  - portal-ignition block
  - pre-generation time/memory
  - ocean-boundary approach
- Distribution: CurseForge confirmation of automatic runtime download (fallback: guided path).
- Model licence terms for automatic download.
