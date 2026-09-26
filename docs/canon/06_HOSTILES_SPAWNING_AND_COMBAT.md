# Hostiles, Spawning, and Combat Specification

**Status:** Canonical Tier 2 subsystem specification  
**Authority:** Subordinate to `01_PROJECT_CHARTER.md`  
**Scope:** Vanilla human-like entity suppression, uncontrolled hostile features, semantic-region spawn governance, guaranteed encounters, and long-term intelligent hostile actors.

The numbered sections below retain their identities from the pre-split canonical charter for lossless migration and auditability.

---

# 13. Vanilla Human-Like Mobs Are Removed From RPG Worlds

The RPG world should not contain ordinary disposable vanilla pseudo-people alongside persistent RPG humans.

That undermines the central social-simulation premise.

Version 0.1 should therefore suppress normal generation/spawning of vanilla entities intended to represent human-like or villager-like people.

This includes at minimum:

- villagers
- wandering traders
- zombie villagers
- pillagers
- vindicators
- evokers
- other ordinary illager populations
- witches

Vanilla patrols and raids should also be disabled.

Trader llamas generated specifically as part of the wandering-trader system should consequently not appear through that system.

Ordinary llamas remain normal passive animals and are not globally removed.

Similarly, ordinary non-human passive animals remain.

Ordinary non-human hostile creatures also remain unless restricted by semantic spawn policy.

The broad Version 0.1 rule is:

> **Animals and ordinary monsters may remain. Vanilla pseudo-human populations do not.**

Long-term, hostile towns, bandits, enemy settlements, raiders, soldiers, cultists, and similar human-like adversaries may return through the project's own persistent NPC/faction systems.

A hostile human settlement should eventually be represented as:

- real persistent NPC records
- relationships
- knowledge
- faction stance
- goals
- social structure

rather than disposable vanilla illagers.

---

# 15. Monster-Spawner Dungeons and Other Uncontrolled Generated Features

Minecraft's small monster-spawner dungeons are not necessarily classified through the same system as large structures.

Therefore merely disabling "structures" is insufficient to guarantee they disappear.

Version 0.1 should explicitly suppress vanilla monster-room/spawner-dungeon generation and any equivalent unrelated generated feature that would undermine deliberate world planning.

The reason is architectural rather than aesthetic:

> **Exploration content should not appear wherever an unrelated vanilla feature generator happens to place it.**

Spawner-based dungeons may later be deliberately reintroduced as semantic POIs or components of intentional POIs.

Likewise, monster spawners themselves may eventually be used intentionally where a POI design benefits from them.

For Version 0.1:

- no uncontrolled monster-room dungeons
- no accidental spawner-dungeon exploration content
- no reliance on vanilla dungeon randomness for the authored quest

This principle should also be considered whenever another vanilla feature is discovered that materially creates exploration content outside the World Planner's control.

---

# 17. Hostile Mob Ecology, Spawn Governance, and Future Hostile Intelligence

Hostile spawning should not remain completely uncontrolled merely because vanilla Minecraft normally handles it globally.

The RPG world contains semantic regions with different meanings.

Hostile population behavior should respect those meanings.

The project distinguishes at minimum between:

- protected settlement space
- ordinary wilderness
- semantic POIs with explicit danger/threat profiles

This distinction is part of Version 0.1.

---

## 17.1 Settlements Are Protected Spawn Regions

Natural hostile mob spawning should be prohibited inside defined city/town/settlement boundaries unless an explicit authored or simulated event overrides that rule.

A functioning inhabited settlement should not routinely produce:

- zombies
- skeletons
- creepers
- spiders
- other ordinary hostile mobs

inside its streets and buildings merely because vanilla spawn rules permit it.

This does not mean a settlement is permanently immune from danger.

Hostile creatures may still enter from outside.

Future systems may deliberately create:

- raids
- sieges
- monster attacks
- infiltration
- outbreaks
- faction attacks
- supernatural events

Those are intentional events.

The rule is:

> **Ordinary hostile generation does not occur inside protected settlements. Hostile presence inside a settlement should normally have a reason.**

---

## 17.2 Wilderness Initially Retains Vanilla-Style Hostile Ecology

For Version 0.1, general wilderness continues to use ordinary vanilla-style hostile spawning.

This preserves:

- nighttime danger
- cave danger
- exploration pressure
- normal Minecraft survival gameplay

without requiring an immediate replacement of Minecraft's entire creature ecology.

Long-term wilderness ecology may become more intentional or region-specific.

That is not required now.

---

## 17.3 Semantic POIs Define Threat Profiles

A semantic POI may define its own hostile behavior.

Possible properties include:

- allowed hostile types
- blocked hostile types
- guaranteed hostile regions
- weighted hostile types
- local caps
- spawn conditions
- respawn behavior
- encounter zones
- deliberately safe areas
- future custom enemies

An abandoned mine may intentionally support:

- zombies
- skeletons
- cave spiders

while a shrine may intentionally suppress hostile spawning.

A future bandit camp may use persistent hostile NPCs instead of vanilla monsters.

The semantic POI owns its threat meaning.

Version 0.1 must implement a **minimal reusable threat/spawn profile mechanism** for the external POI.

---

## 17.4 Regions Are Three-Dimensional

Semantic spawn regions should be capable of representing three-dimensional spaces rather than only flat two-dimensional map outlines.

This matters because:

- an underground mine may exist underneath ordinary wilderness
- a protected building may exist inside a larger settlement
- future layered POIs may overlap geographically

Where semantic regions overlap, the more specific applicable region should ordinarily take precedence.

A useful conceptual order is:

```text
specific POI region
      ↓
settlement region
      ↓
general wilderness
```

The exact region implementation may vary, but it must support the intended semantics.

---

## 17.5 Spawn Decisions Consider Entity, Spawn Reason, and Region

Spawn governance should not be implemented as a simplistic:

```text
if inside town → cancel everything
```

The decision should account for at least:

- what entity is being spawned
- why it is being spawned
- which semantic region applies

This allows the same system to distinguish:

- natural ambient spawning
- custom guaranteed encounters
- commands
- spawn eggs
- future scripted events
- future raids
- other intentional generation

For example, blocking **natural** hostile spawning inside a town should not automatically prevent an explicitly authored future event from deliberately spawning attackers there.

Likewise, global removal of human-like vanilla mobs should apply to the relevant normal generation paths without accidentally making development/debugging impossible.

---

## 17.6 Spawn Governance Must Be Fast

Minecraft evaluates spawn eligibility frequently.

Region/threat-policy lookup must therefore be efficient.

The intended architecture should favor:

- in-memory semantic-region data
- deterministic lookup
- minimal allocation
- plain Java policy logic
- narrow integration hooks into Minecraft spawn checks

The authoritative region information originates from persisted world data such as the WorldPlan, but runtime checks should not repeatedly perform expensive disk or generation operations.

---

## 17.7 Guaranteed Encounters and Ambient Spawning Are Different

The project must distinguish:

**Ambient hostile spawning**

from:

**Intentional encounters**

Ambient spawning creates general ecological danger.

Intentional encounters exist because:

- a POI requires a specific threat
- a quest requires an enemy
- an event deliberately creates hostile actors

A required quest enemy should not rely entirely on random ambient spawn luck.

A deliberately dangerous POI should not become empty merely because ordinary spawn rolls did not cooperate.

The system may therefore support:

- controlled ambient spawning
- explicitly guaranteed encounters

---

## 17.8 Guaranteed Encounter State Is Persistent

If Version 0.1 creates guaranteed hostile encounters for its external POI, the relevant encounter state should survive save/reload.

The game should be able to distinguish states such as:

- not yet spawned
- currently active
- defeated/completed

A required enemy should not:

- duplicate every time the player reloads
- disappear permanently due to ordinary despawn behavior when the quest requires it
- respawn indefinitely after being defeated unless the design explicitly requires that

The exact representation can remain minimal.

The important point is that intentional encounters are deterministic world state, not disposable ambient spawn accidents.

---

## 17.9 Version 0.1 Proves Spawn Governance, Not Advanced Hostile Cognition

Version 0.1 proves that the world can determine:

- where hostile mobs may spawn
- where spawning is suppressed
- where specific threats are intentionally created

It does not change ordinary hostile cognition.

Vanilla hostile mobs may continue using ordinary logic such as:

- sensing players
- selecting targets
- pathfinding
- attacking
- pursuing
- wandering

Version 0.1 does not require:

- coordinated group reasoning
- shared information
- tactical roles
- flanking
- planned ambushes
- strategic group retreat
- faction strategy
- LLM-based hostile decisions
- persistent hostile social memories

The initial hostile-system milestone is:

> **Semantic control over threat placement and spawning.**

The future hostile-intelligence milestone is:

> **Meaningful reasoning about what intelligent hostile actors choose to do.**

---

## 17.10 Future Intelligent Hostiles

Long-term, the project may create:

- custom hostile creatures
- persistent hostile humans
- hostile settlements
- bandit groups
- military factions
- monsters with strategic behavior
- hostile political entities

These may have, where appropriate:

- persistent identity
- goals
- territory
- faction membership
- memory
- relationships
- morale
- knowledge
- tactical preferences
- high-level plans

Not every hostile entity needs this depth.

A zombie does not require the same architecture as an intelligent bandit leader.

---

## 17.11 Advanced Hostile AI Follows the Same AI Philosophy

Language models should not replace ordinary:

- movement
- pathfinding
- collision
- attack animation
- damage calculation
- per-tick pursuit logic

If intelligent hostile actors eventually use AI, inference should be reserved for high-level cognition such as:

- whether to attack
- whether to retreat
- whether to negotiate
- whether to ambush
- choosing strategic goals
- deciding whom to betray
- responding to faction conditions
- planning an expedition
- planning an organized assault

Conceptually:

```text
WORLD / ACTOR STATE
        ↓
high-level reasoning
        ↓
structured intent
        ↓
deterministic game systems
        ↓
movement / combat / execution
```

An army attack could therefore be decided at a high level while ordinary game systems execute the actual participants' behavior.

A hostile town could be represented as ordinary persistent NPCs whose faction stance toward the player or another settlement is hostile.

---

## 17.12 Version 0.1 Hostile-System Definition of Done

The hostile system is complete for Version 0.1 when:

1. The town has a persisted semantic protected region.

2. Ordinary natural hostile spawning is prevented inside it.

3. Hostile mobs can still physically enter from outside unless another rule prevents it.

4. Wilderness retains ordinary hostile spawning.

5. The external POI has a persisted semantic region.

6. The POI demonstrates at least one threat rule differing from wilderness.

7. Quest-required hostile presence is guaranteed when necessary.

8. Spawn rules derive from semantic region identity rather than quest-specific coordinates.

9. Save/reload preserves required threat policy.

10. Guaranteed encounter state persists correctly where applicable.

11. Ordinary hostile mobs may still use vanilla individual AI.
