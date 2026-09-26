# Technical Architecture and Persistence Specification

**Status:** Canonical Tier 2 subsystem specification  
**Authority:** Subordinate to `01_PROJECT_CHARTER.md`  
**Scope:** Persistence domains/versioning, Minecraft dependency boundaries, player identity, initial technical direction, package/module boundaries, and integrated-server authority discipline.

The numbered sections below retain their identities from the pre-split canonical charter for lossless migration and auditability.

---

## 2.21 Client/Server Boundaries Should Remain Real in Single-Player

Minecraft single-player already operates through an integrated server.

Where gameplay state is server-authoritative, client-side UI code should not directly reach into authoritative server data merely because both happen to exist in the same process.

Client/server communication should use the same explicit messaging boundaries that can later operate over multiplayer networking.

This does not mean every internal pure-Java function requires networking.

It means:

- authoritative state lives server-side
- client UI requests information/actions through defined protocol messages
- the client does not obtain special authority merely because the server is integrated locally

This discipline significantly reduces later multiplayer rework.

---

# 49. Persistence Architecture

All important state must survive save/reload.

Do not place everything into one monolithic ever-growing save object.

Persistent state should be divided by domain where appropriate.

Likely domains include:

- WorldPlan
- NPC state
- quest state
- event history
- future simulation domains

Every persisted domain should contain a data-version value from the beginning.

Example:

```json
{
  "dataVersion": 1
}
```

This establishes a migration contract.

The project expects some worlds to survive:

- many mod versions
- hundreds of hours
- schema evolution

Migration must therefore be architecturally possible.

Do not over-engineer migration tooling immediately.

Establish version boundaries.

Avoid unnecessary full rewrites of indefinitely growing history data where better storage patterns are available.

Backend selection and credentials are not authoritative world state.

The WorldPlan or equivalent authoritative persisted data should preserve:

- permanent world ID
- semantic regions
- settlement boundaries
- POI identity
- information needed for spawn/threat policy

For multiplayer, authoritative persistence belongs to the server/world, not individual clients.

---

# 50. Domain Logic and Minecraft Dependencies

Domain logic should avoid dependence on live mutable Minecraft runtime objects.

This does **not** mean:

> no `net.minecraft` imports outside integration code.

Simple stable value/serialization types may be appropriate.

Examples:

- `BlockPos`
- identifiers
- Mojang `Codec`
- other immutable/value representations

The important boundary is against direct dependence on:

- live `ServerWorld`
- mutable entity objects
- active server instances
- mutable registries where avoidable
- other runtime objects preventing independent testing

Mojang Codecs are preferred where they cleanly serve as common representations for:

- persistence
- data-driven definitions
- structured AI output

If Codecs provide sufficient validation and useful errors, do not introduce a second JSON-schema stack without need.

---

# 51. Player Identity

Persistent player-linked state uses UUID.

Display names are not authoritative identity.

Development tooling must account for dev launches potentially generating changing identities.

When Phase 4 introduces player persistence, pin a stable development identity so reload testing is meaningful.

Multiplayer reinforces the importance of UUID identity because multiple players may independently possess:

- relationships with the same NPC
- reputations
- memories
- quest state

---

# 52. Initial Technical Direction

Initial platform:

- Minecraft Java Edition
- Java
- Fabric unless a concrete blocker justifies reconsideration
- modern supported JDK appropriate to chosen Minecraft version
- dedicated RPG world preset
- server-authoritative deterministic simulation
- backend-neutral AI interface
- local inference implemented first
- optional BYOK later
- long-term dedicated multiplayer support
- long-term server-controlled inference

The exact Minecraft/Fabric/JDK/model/runtime baseline should be chosen using current documentation at implementation time.

Avoid unnecessary dependencies.

Keep major systems modular.

Even though Version 0.1 is single-player, important gameplay state should live on the integrated server side rather than relying on client-only authority.

---

# 53. Architectural Boundaries

Prefer distinct packages/modules for concepts such as:

```text
worldgen
worldstate
poi
region
npc
memory
dialogue
quest
simulation
spawn
ai
persistence
ui
network
```

Within AI:

```text
backend
orchestration
context
validation
telemetry
configuration
```

Long-term multiplayer may additionally require:

```text
server
protocol
inference scheduling
instance identity
```

Do not build those prematurely.

Avoid giant classes.

Avoid embedding authored content directly into core engine logic where data-driven definitions are practical.

Prefer semantic identifiers such as:

```text
blacksmith_haldor
blackwood_town
blackwood_smithy
old_iron_mine
lost_hammer
```

instead of treating raw coordinates/entities as identity.
