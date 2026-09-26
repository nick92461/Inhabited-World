# World, World Generation, and POI Specification

**Status:** Canonical Tier 2 subsystem specification  
**Authority:** Subordinate to `01_PROJECT_CHARTER.md`  
**Scope:** RPG world preset, finite world generation, WorldPlan, terrain analysis, intentional structures/features, semantic POIs, and semantic structure markers.

The numbered sections below retain their identities from the pre-split canonical charter for lossless migration and auditability.

---

# 4. Dedicated RPG World Preset and Dimension Scope

The mod should use a dedicated RPG world preset/type rather than silently replacing normal Minecraft world generation.

Conceptually:

```text
Create New World
      ↓
World Preset
      ↓
RPG World
```

This establishes a clean technical and product boundary for:

- finite-world generation
- required POIs
- controlled structure generation
- world-planning logic
- custom settlement rules
- human-like mob suppression
- hostile spawn policy
- portal restrictions
- simulation initialization

The project may ultimately behave like a total conversion, but the RPG generator should still be represented architecturally as its own world preset rather than an invisible global override.

## Version 0.1 Dimension Scope

Version 0.1 is Overworld-only.

The Nether and End should not be accessible.

Vanilla portal mechanics would:

- undermine the finite-world premise
- effectively expand travel beyond the designed map
- introduce additional worlds not integrated with the RPG simulation
- complicate geographic reasoning

The Nether and End may later be deliberately reintegrated as intentionally designed RPG regions.

They should not exist merely because vanilla Minecraft normally provides them.

---

# 5. Authoritative Control Over Generated World Content

A fundamental world-generation principle is:

> **Content should not appear merely because vanilla Minecraft happens to generate it there.**

The RPG world should increasingly treat generated content as intentional world design rather than an uncontrolled accumulation of vanilla generators.

This applies not only to large structures but to generated features capable of materially affecting exploration or gameplay.

Version 0.1 therefore disables unrelated generated content rather than allowing it to appear wherever vanilla algorithms choose.

The long-term approach is not to permanently discard all Mojang content.

Instead:

> **Existing Minecraft generation systems are reusable primitives that the RPG World Planner may intentionally select, place, contextualize, or repurpose later.**

The distinction is between:

```text
vanilla generator decides independently
```

and:

```text
RPG world design decides this content belongs here
        ↓
existing Mojang generator may be used to construct it
```

This principle should guide future decisions about:

- structures
- dungeons
- settlements
- hostile bases
- special resources
- dimension features
- major exploration content

---

# 6. Finite and Constrained World Generation

The RPG world is finite.

The world should have an obvious geographical boundary where practical, backed by a hard technical boundary.

Possible natural boundaries include:

- ocean
- mountain ranges
- cliffs
- otherwise inaccessible terrain

The finite world allows the game to reason about the map as a coherent place.

Terrain itself remains procedurally generated.

However, the planner must guarantee that required gameplay elements exist.

For Version 0.1:

- the town always exists
- the external POI always exists
- both occupy valid terrain
- terrain between them remains procedural
- their position and relationship may vary between seeds
- world generation does not rely on AI
- the town receives an authoritative semantic settlement boundary
- the external POI receives an authoritative semantic region

The high-level concept is:

```text
WORLD SEED
    ↓
DETERMINISTIC WORLD PLANNER
    ↓
REQUIRED REGIONAL CONSTRAINTS
    ↓
TERRAIN SAMPLING
    ↓
CONTROLLED POI / STRUCTURE PLACEMENT
    ↓
SEMANTIC REGION DEFINITION
    ↓
SEMANTIC GEOGRAPHIC ANALYSIS
    ↓
PERSISTED WORLDPLAN
```

---

# 7. The WorldPlan Is Generated Once

The high-level WorldPlan is created once when the RPG world is created.

It is then persisted.

Existing worlds load their saved WorldPlan.

They do not rerun the newest planning algorithm whenever the save opens.

Future mod updates may alter generation logic.

Replanning an existing world could otherwise cause conceptual locations to move underneath already generated terrain.

The saved WorldPlan remains authoritative unless an explicit migration intentionally changes it.

Every WorldPlan should contain a permanent unique world ID created with the world and preserved for the lifetime of that save.

The WorldPlan should also preserve or authoritatively determine information required for:

- settlement boundaries
- POI boundaries
- semantic locations
- structure identity
- hostile spawn policy
- world isolation
- AI request world identity

---

# 8. Terrain Sampling and Geographic Analysis

Minecraft generates chunks lazily as players explore.

The project must not assume the entire finite map physically exists at world creation.

The planner and geographic analyzer should query deterministic terrain-generation information directly where possible.

This may include:

- biome
- approximate surface height
- terrain suitability
- broad terrain features
- relationships between candidate POIs

The same sampling mechanism should support both:

> Is this a valid place to put the town?

and:

> What meaningful geographic facts describe the relationship between this town and mine?

Useful derived facts may include:

- mine is northwest of town
- mine is approximately 430 blocks away
- river lies between town and mine
- mountains lie north of mine
- road leaves town through western gate

These facts become structured world state.

AI does not calculate basic geometry.

It consumes derived facts and expresses them naturally.

---

# 9. Handcrafted POIs

Major locations should preserve authored identity.

A town should not simply be a random cluster of procedural buildings.

Important places can be designed manually and inserted into valid generated terrain.

Controlled variation may later exist.

For example, a town may contain:

- fixed road network
- fixed central plaza
- fixed major structures
- alternate building variants
- variable decorative elements
- variable surrounding geography

The governing principle is:

> **The identity of a place is handcrafted; its incarnation within a particular world is procedural.**

Version 0.1 requires only one small handcrafted town.

A sophisticated modular-city generator is not required.

---

# 10. Physical Structures and Semantic POIs Are Different Concepts

The project must distinguish between:

**Physical Structure**

and:

**Semantic POI**

A physical structure describes blocks and generation behavior.

Example:

```text
minecraft:mineshaft
```

A semantic POI describes what that location means inside this RPG world.

Example:

```text
blackwood_old_mine
```

with properties such as:

- POI type
- associated settlement
- historical ownership
- discovery state
- current danger
- occupants
- who knows about it
- relevant history
- economic importance
- accessibility
- semantic boundary
- threat profile
- hostile-spawn policy

Multiple semantic POIs may use the same underlying Minecraft generator.

Example:

```text
minecraft:mineshaft
    ↓
blackwood_old_mine

minecraft:mineshaft
    ↓
silvercrest_mine

minecraft:mineshaft
    ↓
smugglers_tunnels
```

NPCs and RPG systems should reason primarily about semantic identity rather than raw coordinates or structure type.

---

# 11. Vanilla Structures as Long-Term World-Building Primitives

Vanilla structures are not automatically part of an RPG world merely because Minecraft normally generates them.

They should be treated as reusable procedural building blocks.

Long-term, the World Planner may deliberately decide whether and how to integrate structures such as:

- mineshafts
- desert temples
- jungle temples
- shipwrecks
- trail ruins
- igloos
- ocean ruins
- ancient cities
- ocean monuments
- woodland mansions
- strongholds
- trial chambers
- ruined portals
- other suitable structures

When reused, they should become meaningful RPG locations rather than anonymous vanilla content.

Their meaning may involve:

- historical civilizations
- settlements
- guilds
- factions
- trade
- legends
- expeditions
- dynamic quests
- NPC goals
- occupations
- territorial control
- discoveries
- disasters
- region-specific threats
- hostile occupation

The governing rule is:

> **Vanilla structures are available building blocks, not guaranteed features.**

Mojang's procedural work should be reused where useful.

The RPG simulation owns whether a structure exists and what it means.

---

# 12. Structure Categories

Long-term vanilla structure reuse can be considered in broad categories.

## Easy Semantic Reuse

Examples:

- mineshafts
- desert temples
- jungle temples
- shipwrecks
- trail ruins
- igloos
- ocean ruins

## Major World POIs

Examples:

- ancient cities
- ocean monuments
- woodland mansions
- strongholds
- trial chambers

Their existence should be deliberate and significant.

## Civilization-Defining Structures

Examples:

- villages
- pillager outposts
- woodland mansions
- faction bases
- some fortress-like locations

These imply inhabitants or social organizations and therefore require deeper RPG integration.

---

# 14. Strongholds and Mechanically Coupled Structures

Some vanilla structures are tightly linked to broader mechanics.

Strongholds are the clearest example because they are associated with End access.

Such structures should not generate unchanged while their associated systems are unavailable.

Long-term options may include:

- deliberately restoring their original role
- repurposing them as major ruins
- assigning new historical significance
- integrating them into future End content

Version 0.1 should not generate strongholds.

---

# 16. Semantic Markers Inside Handcrafted Structures

Handcrafted structures should contain named semantic markers for important internal locations where practical.

Examples:

- `blackwood_smithy`
- `blackwood_inn`
- `blackwood_west_gate`
- `blackwood_haldor_home`
- `blackwood_bed_01`
- `blackwood_mayor_office`

When a structure is placed and rotated, these markers should resolve into world positions and be persisted.

NPC schedules and game systems should reference:

```text
blackwood_smithy
```

rather than:

```text
x=184 y=72 z=-39
```

This allows structure placement and rotation to vary without breaking RPG logic.

A player-spawn marker may also eventually be placed in the town template if useful for the slice, for example if the first iteration begins with the player as an existing resident.

This is an implementation/content choice rather than a fundamental architectural requirement.
