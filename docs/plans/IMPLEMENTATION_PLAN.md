# Implementation Plan — Minecraft: Inhabited

**Status:** Tier 5, NON-CANONICAL (see `docs/canon/00_DOCUMENT_AUTHORITY_AND_INDEX.md` §2). This is Claude's architecture/implementation plan for building the canon. It never overrides `docs/canon/`. If they disagree, canon wins and this file is corrected.

**Maintained by:** Claude (coding agent), with the user. Migrated on 2026-09-25 from Claude's private working notes when the canon moved from the monolithic v6 charter to the hierarchical corpus. Only the source-of-truth references were changed in migration (old single-file line numbers → canon file + retained section number). The decisions themselves are unchanged.

**Section references** use the pre-split section numbers, which the canon files retain (see `docs/canon/MIGRATION_MANIFEST.md`). File key:

| Sections | File |
|---|---|
| §1, §2.1–2.5 | `01_PROJECT_CHARTER.md` |
| §4–§12, §14, §16 | `02_WORLD_WORLDGEN_AND_POIS.md` |
| §18–19, §33–34, §39–43 | `03_NPC_SOCIAL_AND_MEMORY.md` |
| §2.6–2.14, §2.20, §20–30, §35–38 | `04_AI_ARCHITECTURE.md` |
| §31, §32, §44 | `05_QUESTS_AND_WORLD_HISTORY.md` |
| §13, §15, §17 | `06_HOSTILES_SPAWNING_AND_COMBAT.md` |
| §2.15–2.19, §58 | `07_MULTIPLAYER_AND_SERVER_ARCHITECTURE.md` |
| §2.21, §49–53 | `08_TECHNICAL_ARCHITECTURE_AND_PERSISTENCE.md` |
| §3, §54, §55, §56 | `09_V0_1_VERTICAL_SLICE.md` |
| §57, §60, §61 | `10_DEVELOPMENT_AND_AGENT_RULES.md` |
| §45–48, §59 | `11_LONG_TERM_SYSTEMS.md` |

---

## Always-on checks (before any design/code handoff)

- v0.1 scope? `09` §55, `10` §60 scope priority, `10` §57 no speculative frameworks.
- AI involved? `10` §60 AI discipline (facts, context, decision, output, validation, executor); `01` §2.3, §2.5; `04` §24 lifecycle, §23 plain data.
- Spawn/hostile work? `10` §60 Hostile-System Discipline: entity type, spawn reason, region, policy, encounter state, persistence.
- MP hedge (`10` §60 Multiplayer Architectural Discipline, `08` §2.21): state lives server-side; client UI only via protocol messages; plain-data AI contracts; explicit world/request identity; validation at the authoritative boundary. Don't build networking.
- Blocking work off the game threads, through the injected handoff; nothing outside `ai/` knows a runtime; semantic IDs; everything scoped to the RPG preset; outcome before presentation.
- Handoff format per `10` §60 Communication Style. The user types all code. NEVER `git push` (`10` §57.1).

## Canon to re-read per phase

- **P1:** `08` §52, `09` §54 Phase 1, `10` §61.
- **P2A:** `02` §4–§8, §11–§12, §14; `06` §13, §15; `09` §54 Phase 2A, DoD 1–9.
- **P2B:** `06` §17.1–17.6; `09` §54 Phase 2B, DoD 10, 13–15.
- **P3:** `02` §9–10, §16; `06` §17.3, §17.7–17.8, §17.12; `08` §49; `09` §54 Phase 3, DoD 16–19.
- **P4:** `03` §18–19, §33–34; `05` §31–32; `04` §35; `08` §51; `09` §54 Phase 4.
- **P5:** `01` §2.4; `07` §2.18; `04` §2.20, §20–27; `08` §50; `09` §54 Phase 5; `10` §60.
- **P6:** `04` §26–29. **P7:** `01` §2.3, §2.5; `04` §30. **P8:** `05` §32; `03` §33–34. **P9:** `04` §36–38, §2.14. **P10:** `09` §54 Phase 10; `04` §2.10.

---

## Project plan (decisions, each tied to canon)

**Baseline:** Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Loom 1.18.x, Java 25, split client/common sources. Milestone 1 = the official template + our own initializer log line.

**Workspace layout (from the canon package, 2026-09-25):** `docs/` lives at the `Minecraft Inhabited/` workspace root. The mod codebase is created by Milestone 1 as a CHILD directory of the workspace. Never move `docs/` into the codebase.

**Git (decided by the user 2026-09-25):** one repo at the WORKSPACE ROOT, pushed to GitHub by the user (never by Claude). A root `.gitattributes` marks `docs/canon/**` as `-text` so Windows `core.autocrlf=true` (system setting on the dev machine) can't rewrite canon line endings and break the canon hashes.

**Packages (`08` §53):** worldgen, worldstate, poi, region, npc, memory, dialogue, quest, simulation, spawn, ai/{backend, orchestration, context, validation, telemetry, config}, persistence, network; client: ui. Each is created in the phase that first needs it.

### P2A build decisions
- The **spawn-decision hook** is built at the START of P2A with just one rule: the human-like type ban in RPG worlds. P2B then adds region rules to the same hook.
  - Why: Fabric's biome spawn-list modification is global across all worlds, so it can't implement a per-preset rule (`02` §4). Natural witch/zombie-villager spawns therefore need our per-world hook, and P2A's DoD (no witches) wouldn't hold without it.
  - Other human-like sources handled in P2A without the hook: villages/outposts/huts/mansions (structure suppression); wandering traders + patrols (per-world game rules / special spawners off); raids (gamerule + no bad-omen sources). Verify 26.x gamerule names.
- The **town and external POI are placed through Minecraft's own structure system**, with a custom placement rule that reads positions from the WorldPlan.
  - Why: the town spans many chunks, and chunks generate lazily and in parallel. The structure system already builds multi-chunk structures piece by piece safely.
  - Bonus: per-world structure suppression becomes "in RPG worlds, only our structure sets are allowed".
  - Verify in the P2A spike.
- **Feature audit per `06` §15:** suppress monster rooms (required) and desert wells (archaeology loot = small exploration content; the user may override). Keep geodes, fossils, ores, lakes, springs, trees (terrain/resources).
- **Every AI request tag = permanent world ID + a per-load session ID.** Copying a save folder duplicates the world ID; the session ID stops a late result landing in the copy (`07` §2.18 edge case).

### WorldPlan (`02` §6–8)
Created once, before the first chunk generates. Persisted and immutable. Contains:
- world ID
- bounds/border
- POI placements + bounds + structure identity
- 3D semantic regions
- spawn marker reference (optional, `02` §16)
- geography facts

The terrain sampler is built in P2A (placement) and extended in P3 (facts). Regions exist in P2B, from the plan, before the P3 structures.

### Regions + spawn governance (`06` §17)
- Regions are 3D volumes. Most-specific-wins precedence: POI > settlement > wilderness.
- One decision on (entity type, spawn reason, region). Natural and chunk-generation spawns are governed; commands, eggs and our encounters pass.
- Pure-Java rules plus a thin mixin. An in-memory index built from the WorldPlan; allocation-free lookup on the tick thread.
- Guaranteed encounters are persisted (not spawned / active / defeated). Their mobs are persistence-required and don't respawn on reload.
- Debug command reporting region, policy, reason, entity and source POI (`10` §60 Favor Inspectability).
- Watch vanilla mob caps vs POI hotspots (P3).

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
- The v0.1 event log in SavedData is fine; revisit if P9 shows growth.
- Codecs are reused for model output (`08` §50).
- Pin the dev username at P4 (`08` §51).

### Quests / events / memory (`05` §31–32, `03` §33–34)
- QuestDefinition vs QuestInstance.
- A `revision` int on important mutable objects drives stale-result checks.
- The append-only event log uses getGameTime.
- Memories are created only by Java from validated events.

### AI (`04` §20–27, §2.20; `08` §2.21)
- `AiBackend` → `LocalBackend` (OpenAI-compatible HTTP); `CloudBackend` later. Plain-data request/result.
- Context + validation always live in the owning Minecraft server.
- Process-wide service; config holds no secrets; backend selection only at startup/reconnect/user action.
- Lifecycle per `04` §24. Injectable executor. ScriptedBackend tests (incl. player disconnect, wrong-world).
- Stable-prefix prompt order; relevance-based trimming; token estimate + calibration.
- Deterministic prose checks: directions, committed reward amounts, known names.
- Negotiation inputs per `04` §30 (personality, relationship, desperation, legal range); the current offer comes from the QuestInstance.
- Client UI reaches server state only through network payloads, even in single-player.

### Future NPC-NPC
Canon `03` §40: outcome first; server-side perception; generate once and broadcast; drop presentation before outcome.

### Post-M1 inference spike
Measure VRAM, RAM, generation tok/s, prompt-processing tok/s, prefix-cache effect and FPS, on named hardware (RX 7600 8 GB / Ryzen 5 5600 / 16 GB) per `04` §2.14.

---

## Open items
- OS keychain dependency when BYOK is built (`04` §2.11 vs `08` §52 "avoid unnecessary dependencies"). Future only.
- Desert-well suppression is Claude's implementation call under `06` §15; the user may keep them.

## Risks to verify
- 8 GB VRAM shared with Minecraft → spike after M1.
- P2A spike: Overworld-only preset, per-world structure/feature suppression, custom structure placement from the WorldPlan, spawn-hook injection point, 26.x gamerule names.
