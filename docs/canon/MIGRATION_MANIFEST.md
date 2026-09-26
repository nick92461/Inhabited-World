# Canon Migration Manifest

## Purpose

This manifest records the first lossless migration from the former monolithic `Canonical Project Scope` document into the hierarchical canonical corpus under `docs/canon/`.

The migration is organizational. Previously approved design content is not intentionally removed.

The pre-split charter is preserved for audit under:

```text
docs/canon/_archive/PRE_SPLIT_CANON_SNAPSHOT.md
```

That snapshot is historical rather than independently authoritative after migration.

---

# Section Destination Map

| Former monolithic section | Canonical destination |
|---|---|
| Status / document-preservation rules | `00_DOCUMENT_AUTHORITY_AND_INDEX.md` and `01_PROJECT_CHARTER.md` |
| §1 Core Vision | `01_PROJECT_CHARTER.md` |
| §2.1–§2.5 Project-wide AI/authority principles | `01_PROJECT_CHARTER.md` |
| §2.6–§2.14 Single-player AI backend/BYOK/security/cost/performance | `04_AI_ARCHITECTURE.md` |
| §2.15–§2.19 Multiplayer AI authority/backend/isolation/scheduling | `07_MULTIPLAYER_AND_SERVER_ARCHITECTURE.md` |
| §2.20 Plain-data AI transport | `04_AI_ARCHITECTURE.md` |
| §2.21 Client/server boundaries in single-player | `08_TECHNICAL_ARCHITECTURE_AND_PERSISTENCE.md` |
| §3 Initial Vertical Slice | `09_V0_1_VERTICAL_SLICE.md` |
| §4–§12 World preset, intentional generation, planner, terrain, POIs, structures | `02_WORLD_WORLDGEN_AND_POIS.md` |
| §13 Human-like vanilla mob removal | `06_HOSTILES_SPAWNING_AND_COMBAT.md` |
| §14 Strongholds/mechanically coupled structures | `02_WORLD_WORLDGEN_AND_POIS.md` |
| §15 Monster-spawner dungeons/uncontrolled features | `06_HOSTILES_SPAWNING_AND_COMBAT.md` |
| §16 Semantic markers | `02_WORLD_WORLDGEN_AND_POIS.md` |
| §17 Hostile ecology/spawn governance/future hostile intelligence | `06_HOSTILES_SPAWNING_AND_COMBAT.md` |
| §18–§19 NPC architecture / NPC record is the person | `03_NPC_SOCIAL_AND_MEMORY.md` |
| §20–§30 Shared model, AI integration/orchestration/request lifecycle/prompt/dialogue | `04_AI_ARCHITECTURE.md` |
| §31 Quest Architecture | `05_QUESTS_AND_WORLD_HISTORY.md` |
| §32 World History | `05_QUESTS_AND_WORLD_HISTORY.md` |
| §33–§34 NPC memory / knowledge vs truth | `03_NPC_SOCIAL_AND_MEMORY.md` |
| §35–§38 Event-driven inference/scheduling/performance/model size | `04_AI_ARCHITECTURE.md` |
| §39–§43 Off-screen NPCs/outcome-first/perception/POI visits/social simulation/NPC interaction | `03_NPC_SOCIAL_AND_MEMORY.md` |
| §44 Long-Term Dynamic Quest Design | `05_QUESTS_AND_WORLD_HISTORY.md` |
| §45–§48 Director/world simulation/endgames/shops | `11_LONG_TERM_SYSTEMS.md` |
| §49–§53 Persistence/domain dependencies/player identity/technical direction/boundaries | `08_TECHNICAL_ARCHITECTURE_AND_PERSISTENCE.md` |
| §54 Development Order | `09_V0_1_VERTICAL_SLICE.md` |
| §55 Explicit Version 0.1 Non-Goals | `09_V0_1_VERTICAL_SLICE.md` |
| §56 Vertical Slice Definition of Done | `09_V0_1_VERTICAL_SLICE.md` |
| §57 Development Philosophy / coding-agent operating rules | `10_DEVELOPMENT_AND_AGENT_RULES.md` |
| §58 Long-Term Multiplayer Deployment Model | `07_MULTIPLAYER_AND_SERVER_ARCHITECTURE.md` |
| §59 Long-Term Success Condition | `11_LONG_TERM_SYSTEMS.md` |
| §60 Instructions for Coding Agents | `10_DEVELOPMENT_AND_AGENT_RULES.md` |
| §61 Required First Response From a New Coding Agent | `10_DEVELOPMENT_AND_AGENT_RULES.md` |

---

# Migration Rules Applied

1. Canonical meaning was not intentionally condensed during relocation.
2. Section wording is preserved in the destination documents except where governance wording necessarily changes from a single-file charter to a canonical document corpus.
3. No subsystem detail is considered less authoritative merely because it moved out of the root Project Charter.
4. The new authority/index document is an additive canonical rule establishing document hierarchy and synchronization workflow.
5. Future cleanup or deduplication is a separate canonical-edit operation and must not be performed silently as part of migration.
