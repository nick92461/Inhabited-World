# NPC, Social Simulation, and Memory Specification

**Status:** Canonical Tier 2 subsystem specification  
**Authority:** Subordinate to `01_PROJECT_CHARTER.md`  
**Scope:** Persistent NPC identity, body/entity representation, memory, knowledge, off-screen simulation, social presentation, NPC-to-NPC interaction, and future social simulation.

The numbered sections below retain their identities from the pre-split canonical charter for lossless migration and auditability.

---

# 18. NPC Architecture

NPCs are persistent individuals rather than disposable mobs.

An NPC record should eventually support:

- stable identity
- name
- personality
- occupation
- home
- current location
- schedule
- goals
- relationships
- family
- faction
- beliefs
- memories
- knowledge
- emotional state
- reputation toward each relevant player
- relevant personal history

Version 0.1 implements only the subset needed.

The complete future social simulation is not required during the slice.

---

# 19. The NPC Record Is the Person

A foundational invariant is:

> **The persistent NPC record is the person. The Minecraft entity is the person's temporary physical body.**

The NPC does not cease to exist when a chunk unloads.

Persistent NPC state owns identity.

When the relevant region is loaded:

```text
NPC RECORD
    ↕
PHYSICAL MINECRAFT ENTITY
```

When unloaded:

```text
NPC RECORD
```

The record remains.

The game eventually creates, synchronizes, and removes physical bodies according to:

- authoritative NPC state
- logical location
- loaded-world state

This enables off-screen simulation and avoids fighting vanilla chunk-based mob persistence.

---

# 33. NPC Memories and Knowledge

NPC memories are not identical to world history.

Memories should be created deterministically from validated events.

The LLM does not directly create authoritative memories.

Otherwise hallucinated dialogue could become persistent "fact."

Memory may eventually contain:

- subject
- event/claim reference
- source
- confidence
- timestamp
- significance
- emotional association

AI may interpret known information.

It does not invent the authoritative basis for knowledge.

---

# 34. Knowledge vs Truth

World truth and NPC belief are different.

Example:

World truth:

```text
Player stole sword.
```

Blacksmith:

```text
source = witnessed
confidence = 1.0
```

Innkeeper:

```text
source = Blacksmith
confidence = 0.85
```

Guard:

```text
source = Innkeeper
confidence = 0.60
```

This enables:

- rumors
- lies
- secrets
- gossip
- investigation
- reputation propagation
- mistaken belief
- misinformation

Version 0.1 does not require a sophisticated rumor network.

The distinction must nevertheless exist architecturally.

---

# 39. Off-Screen NPC Simulation

Long-term scale must not require every NPC to remain an active entity.

Unloaded NPCs may exist as simulation records.

Instead of physically simulating every step:

```text
08:00 home → smithy
18:00 smithy → tavern
23:00 tavern → home
```

off-screen simulation may simply advance logical state.

When the area loads again, the entity appears where the simulation says it should be.

Guiding rule:

> **Only fully simulate what currently matters.**

---

# 40. Outcome First, Presentation Second

Simulation outcome and visible presentation are separate concepts.

This applies especially to NPC-to-NPC interaction.

The authoritative simulation should first determine:

> **What happened?**

Only then should the game determine:

> **Does a player need to see this happen in detail?**

A watched and unwatched interaction should not produce fundamentally different outcomes merely because one happened to be observed.

For example:

```text
Haldor tells Mara about the player.
```

The simulation first determines the semantic outcome:

```text
Mara receives information X
source = Haldor
confidence = Y
```

If nobody can perceive the interaction:

- store the outcome
- do not generate unnecessary conversational prose
- do not animate an elaborate scene solely for nobody to see

If one or more players can perceive the interaction:

- the world may physically play out the interaction
- language may be generated to represent the already-determined semantic outcome
- nearby players see the same canonical interaction

The language is presentation of the outcome.

It must not independently decide a contradictory outcome.

This avoids a world in which:

```text
watched conversation → outcome A
unwatched conversation → outcome B
```

merely because generated dialogue happened to differ.

---

## 40.1 Player Perception Is Determined Server-Side

In multiplayer, "someone is watching" should not be defined by a client's render-distance setting.

The authoritative server determines whether a player can reasonably perceive the event.

Relevant criteria may eventually include:

- relevant chunk/area is loaded due to a player
- player is within an appropriate hearing/interaction range
- player is otherwise in a state where the interaction is meaningfully observable

The exact perception calculation may evolve.

The important principle is:

> **The authoritative simulation determines observation relevance, not arbitrary client rendering configuration.**

---

## 40.2 Generate Visible Dialogue Once

If multiple players can perceive the same NPC-to-NPC conversation:

- determine the semantic outcome once
- generate the visible conversation once
- broadcast the resulting canonical presentation to relevant players

Do not generate separate contradictory versions of the same world event for each observer.

---

## 40.3 Under Load, Preserve Outcome Before Presentation

Under heavy server or inference load:

> **Skip or simplify optional presentation before skipping authoritative simulation.**

An NPC interaction can still logically occur without generating full dialogue.

The world state must remain coherent even when language generation is deferred or omitted for an unimportant background event.

---

# 41. NPCs May Intentionally Visit Semantic POIs

Long-term NPC planning should treat semantic POIs as real places.

Examples:

- miner goes to a mine for work
- adventurer seeks an ancient city
- scholar visits ruins
- merchant travels to another settlement
- faction occupies a fortress

NPC choice may depend on:

- goals
- knowledge
- profession
- risk tolerance
- relationships
- economic pressure
- curiosity
- faction needs
- quests

This enables unscripted encounters.

The player may enter a mine and meet another adventurer who independently chose to go there.

The player may:

- cooperate
- compete
- betray them
- rescue them
- abandon them
- lure them into danger
- kill them

That encounter need not have been manually scripted.

---

# 42. Future Social Simulation

Long-term systems may include:

- friendship
- rivalry
- romance
- marriage
- children
- aging
- death
- inheritance
- migration
- occupation changes
- family lineage
- political relationships
- social mobility

These arise through persistent simulation rather than fixed narrative.

Example:

Two NPCs form a relationship.

They have a child.

One parent dies during an actual simulated event before the child is born.

Years later, that child can discuss their parent because the event genuinely happened.

No developer authored that biography.

It emerged.

---

# 43. NPC-to-NPC Interaction

NPCs eventually exchange information and influence one another.

Most off-screen interactions should operate semantically rather than generating language.

Example:

```text
Haldor knows:
PLAYER_STOLE_SWORD
```

An interaction transfers that information.

Now:

```text
Innkeeper knows:
PLAYER_STOLE_SWORD
source = Haldor
```

If no player can perceive the interaction, no dialogue generation is required.

If a player can perceive it, the game may generate a visible representation of that same already-determined semantic exchange.

Core principle:

> **Simulate meaning. Generate language only when language matters.**
