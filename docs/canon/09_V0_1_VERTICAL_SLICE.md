# Version 0.1 Vertical Slice Specification

**Status:** Canonical Tier 3 release specification  
**Authority:** Subordinate to `01_PROJECT_CHARTER.md` and applicable Tier 2 subsystem specifications  
**Scope:** Exact Version 0.1 slice content, development order, explicit non-goals, and Definition of Done.

The numbered sections below retain their identities from the pre-split canonical charter for lossless migration and auditability.

---

# 3. Initial Vertical Slice

Development begins with a deliberately small world that demonstrates the architecture of the full project.

The slice should engage nearly every important mechanism in miniature rather than attempting to create large amounts of content.

The goal is not merely to build a quest demo.

The goal is to prove, in small form, that the major architectural categories intended for the full project can coexist coherently.

The approximate Version 0.1 world should contain:

- one finite RPG world
- approximately 1024 × 1024 playable blocks
- one small handcrafted town
- one significant external POI
- one short authored questline connecting town and POI
- approximately 6–10 significant NPCs
- basic NPC schedules
- persistent NPC identities
- AI-driven natural-language conversations
- persistent player/NPC relationship information
- persistent memories
- persistent world state
- deterministic geographic analysis
- a generalized quest framework
- one shop or equivalent structured NPC interaction if practical
- at least one AI interaction that could not reasonably be represented by a traditional dialogue tree
- explicit semantic-region-based hostile spawn governance
- a protected settlement region in which ordinary natural hostile spawning is prohibited
- ordinary vanilla-style hostile spawning in general wilderness
- at least one external-POI hostile hotspot, controlled encounter area, or guaranteed hostile population
- deterministic handling of quest-critical hostile encounters so progression does not depend solely on random spawn luck

Version 0.1 deliberately demonstrates three different hostile-generation regimes.

## Protected Settlement

Inside the town's semantic settlement boundary:

- ordinary natural hostile spawning is suppressed
- hostile mobs may still physically enter from outside
- no advanced raid simulation is required

## General Wilderness

Outside explicitly governed settlement/POI regions:

- ordinary Minecraft-style hostile spawning remains active
- vanilla nighttime/cave danger remains
- Version 0.1 does not replace the full vanilla creature ecology

## Controlled POI Threat Region

The external POI demonstrates that a semantic location can own its own threat rules.

Depending on its design, this may include:

- guaranteed hostile mobs
- localized spawn eligibility
- controlled hostile type selection
- encounter hotspots
- defined hostile areas
- suppression of irrelevant ambient spawning in selected subregions

This mechanism should be architecturally reusable rather than implemented as quest-specific hardcoded coordinates.

Hostile cognition remains deliberately simple in Version 0.1.

Vanilla hostile mobs may continue using ordinary vanilla per-entity behavior such as:

- detecting players
- selecting targets
- pathfinding
- attacking
- wandering
- pursuing

Version 0.1 does not require them to:

- reason as a coordinated team
- share information
- plan ambushes
- maintain faction strategy
- use LLM inference
- select strategic group roles

The slice proves:

> **where hostile threats exist and why**

before attempting:

> **advanced reasoning by intelligent hostile actors.**

The initial external POI may be something such as:

- an abandoned mine
- authored ruins
- a deliberately placed hostile encampment
- a shrine

The initial authored quest can be simple.

Example:

A blacksmith needs the player to retrieve a unique object from an old mine.

The purpose is not sophisticated authored narrative.

The purpose is to prove an end-to-end architecture in which:

- world generation
- semantic geography
- semantic regions
- hostile spawn governance
- POI-specific threat behavior
- persistent NPC identity
- dialogue
- AI reasoning
- quest state
- memory
- persistence
- player choice

all interact coherently.

Version 0.1 is initially a **single-player proof of the game architecture**.

Dedicated multiplayer support is part of the long-term architecture but is not required for the initial slice.

The Version 0.1 architecture should nevertheless avoid assumptions that make future server-authoritative multiplayer unnecessarily difficult.

---

# 54. Development Order

## Phase 1 — Mod Foundation

Establish:

- project
- loader
- development environment
- mod initialization
- reliable dev launch
- reliable build

Nothing more unless needed to validate a foundational assumption.

---

## Phase 2A — RPG World Preset, Finite World, and Planner

Implement:

- dedicated RPG world preset
- persisted RPG-world identity (own chunk-generator type and dimension type; see `02` §4)
- finite map
- boundary behavior
- cheap terrain beyond the playable boundary
- pre-generation of the finite playable area during world creation, with progress display
- Overworld-only slice
- Nether disabled
- End disabled
- vanilla villages disabled
- vanilla human-like mobs disabled
- wandering traders disabled
- illager patrols/raids disabled
- witches suppressed from normal RPG-world spawning
- uncontrolled vanilla structures suppressed
- monster-room/spawner-dungeon generation suppressed
- constrained world planner
- permanent world ID
- guaranteed town
- guaranteed external POI
- seed variation
- generated-once persistent WorldPlan

---

## Phase 2B — Semantic Regions and Spawn Rules

Implement:

- authoritative semantic town region
- authoritative semantic external POI region
- three-dimensional region support sufficient for the slice
- region precedence/specificity behavior
- efficient in-memory region lookup
- natural hostile-spawn suppression in town
- ordinary wilderness spawning
- global exclusion of prohibited human-like vanilla mobs
- spawn-policy decision based on entity, spawn reason, and semantic region
- tests for region membership and spawn eligibility

Phase 2 should establish:

```text
TOWN
WILDERNESS
EXTERNAL POI
```

as real semantic regions.

---

## Phase 3 — Structures, Semantic Locations, Geography, and POI Threat Governance

Implement:

- handcrafted town
- handcrafted external POI
- semantic structure markers
- semantic location registry
- persistent POI identity
- terrain sampling
- geographic analysis
- generated geography facts
- reusable minimal POI threat profile
- POI hostile hotspot
- guaranteed encounter behavior if required
- persistent guaranteed-encounter state
- relevant debug tools

At the end of Phase 3:

```text
Protected Town
    → natural hostile generation suppressed

General Wilderness
    → vanilla hostile generation

External POI
    → semantic threat policy
```

No advanced hostile cognition yet.

---

## Phase 4 — RPG Foundation

Implement:

- NPC record identity
- physical body synchronization
- persistent NPC state
- schedules
- stable dev-player identity
- interaction mechanism
- QuestDefinition
- QuestInstance
- one authored quest
- event log
- deterministic memory creation

The complete quest should function without AI first.

Quest-required enemies use the established threat/encounter system rather than a second quest-specific spawn architecture.

---

## Phase 5 — Local AI Infrastructure

Implement:

- plain-data backend interface
- local backend
- asynchronous inference
- immutable context snapshots
- permanent world ID on requests
- request IDs
- safe authoritative-thread handoff
- context builder
- structured output
- Codec parsing where appropriate
- validation
- stale-result rejection
- retry/error handling
- telemetry
- fake backend

The AI infrastructure initially supports social/NPC cognition.

Hostile strategic AI is not required.

---

## Phase 6 — AI NPC Dialogue

Implement:

- natural-language input
- NPC context retrieval
- stable prompt prefix
- relevance-based trimming
- concise responses
- world knowledge
- quest awareness
- relationship awareness

---

## Phase 7 — AI-Unique Interaction

Implement at least one:

- natural-language negotiation
- unscripted persuasion
- arbitrary geographic question
- equivalent interaction not reasonably represented by a dialogue tree

Critical factual replies receive deterministic checks where practical.

---

## Phase 8 — Persistent Memory

Implement:

- interaction memories
- save/reload
- memory retrieval
- knowledge/truth distinction
- source/confidence where required

---

## Phase 9 — Performance

Measure:

- FPS during local inference
- generation latency
- prompt-processing latency
- VRAM
- RAM
- context size
- prefix reuse
- model size
- inference queue behavior
- backend-neutral telemetry

Optimize scheduling/context before simply increasing model size.

---

## Phase 10 — Polish

Complete:

- UI cleanup
- bug fixes
- README
- architecture diagram
- setup documentation
- Windows managed local-AI provisioning for the consumer local edition
- automatic supported-hardware detection and curated local model-tier selection (Version 0.1 ships tier A, ~8 GB VRAM, plus manual override; further tiers follow as benchmarked, `04` §2.22.3)
- automatic and guided provisioning paths (`04` §2.22.6.1)
- first-run additional-file download flow
- integrity verification for managed runtime/model packages
- automatic local inference process startup/health check/shutdown
- advanced/manual local-backend override where supported
- demo video
- useful automated tests

Managed provisioning belongs here rather than in the early AI-engine milestones so product setup work does not distort core architecture development.

BYOK and dedicated multiplayer must not delay Version 0.1.

---

# 55. Explicit Version 0.1 Non-Goals

Do not implement during Version 0.1 unless an unforeseen dependency requires part of one:

- dedicated multiplayer
- multiplayer synchronization beyond normal architecture needs
- multi-player inference queues
- linked server networks
- multiple settlements
- hundreds of NPCs
- marriage
- children
- generational simulation
- NPC aging
- broad economy
- political simulation
- wars
- faction simulation
- procedural quests
- Game Director
- dynamic settlement expansion
- large-scale rumor propagation
- sophisticated economy
- dozens of quests
- fully procedural cities
- Nether gameplay
- End gameplay
- vanilla villages
- vanilla wandering traders
- vanilla illager populations
- vanilla witch population
- uncontrolled vanilla structures
- uncontrolled monster-spawner dungeons
- full semantic reuse of vanilla structures
- custom hostile ecosystem
- custom hostile roster
- persistent hostile factions
- LLM-driven hostile cognition
- organized hostile group AI
- AI combat squads
- advanced hostile strategy
- raids/sieges
- world-scale ecology
- developer-funded centralized inference
- BYOK if it delays local slice
- non-AI fallback
- continuously thinking LLM agents
- user-facing multi-model routing

Importantly, the following **are Version 0.1 requirements**:

- controlled world generation
- human-like vanilla mob suppression
- monster-spawner dungeon suppression
- semantic regions
- town hostile-spawn protection
- normal wilderness spawning
- POI-specific threat control
- guaranteed encounters where required

---

# 56. Vertical Slice Definition of Done

Version 0.1 succeeds when a player can:

1. Create the dedicated RPG world type.

2. Generate a finite world.

3. Receive terrain that varies meaningfully by seed.

4. Always receive the required town and external POI.

5. Receive a persisted WorldPlan with permanent world identity.

6. Receive no unrelated vanilla villages.

7. Receive no uncontrolled vanilla adventure structures.

8. Receive no uncontrolled monster-spawner dungeon features.

9. Encounter no ordinary vanilla human-like pseudo-populations such as villagers, wandering traders, illagers, or witches.

10. Observe that town and external POI possess authoritative semantic regions.

11. Enter the town and encounter persistent named RPG NPCs.

12. Observe NPC schedules referencing semantic locations rather than hardcoded coordinates.

13. Observe ordinary natural hostile mobs failing to spawn inside the protected town boundary.

14. Observe hostile mobs from outside remaining physically capable of entering the town where normal movement permits.

15. Leave town and encounter ordinary vanilla-style wilderness hostile spawning.

16. Enter the external POI and observe a threat policy differing from wilderness.

17. Encounter at least one controlled hotspot, guaranteed hostile population, or equivalent intentional POI threat.

18. Complete a hostile-dependent quest objective without relying solely on random ambient spawn luck.

19. Save/reload and observe guaranteed encounter state behaving correctly.

20. Observe ordinary hostile mobs still using vanilla individual AI.

21. Speak naturally with at least one NPC.

22. Ask about generated geography and receive a geographically correct response.

23. Begin the authored quest.

24. Travel to the generated POI.

25. Complete its objective.

26. Return and complete the quest.

27. Negotiate or influence an interaction using arbitrary language.

28. Observe the validated result becoming authoritative state.

29. Save.

30. Reload.

31. Observe that WorldPlan, semantic regions, NPC state, memories, quests, relationships, event history, threat policy, and required metadata persist.

32. Observe persistent NPC identity surviving chunk unload/load independently of entity persistence.

33. Observe spawn/threat rules remaining correct after reload.

34. Complete the experience using local inference without paid API calls.

35. On the supported Windows consumer local path, complete first-run AI setup without manually installing/configuring an inference server or model: the software detects supported hardware, recommends/selects the curated tier (tier A in Version 0.1), requests approval for the additional download, provisions and verifies required files, launches the local runtime, and reports readiness.

36. Observe the managed local inference process shutting down with the relevant Minecraft session rather than remaining as an unnecessary permanent background service. That means it stops when no RPG world remains open after the grace period, and always when Minecraft exits, including abnormally (`01` §2.22).

37. Maintain reasonable performance through sparse, scoped, asynchronous, validated inference.

Dedicated multiplayer does not need to be demonstrated in Version 0.1.
