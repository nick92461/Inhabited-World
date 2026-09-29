# Canonical Project Scope

## Local-AI Procedural RPG for Minecraft

### Status

This document defines the project's core vision and the non-negotiable design principles that every other canonical document must obey.

Together with the rest of the canonical corpus under `docs/canon/`, it is the project's primary source of truth unless explicitly superseded by a later approved revision. Architecture details, subsystem rules, development rules, long-term direction, and the initial vertical-slice scope live in the subsystem, release, and development documents indexed by `00_DOCUMENT_AUTHORITY_AND_INDEX.md`.

All contributors, coding agents, design discussions, and implementation plans should treat this document, together with that corpus, as the shared authoritative description of the project.

This document is intentionally comprehensive.

Existing approved details, constraints, examples, and rationale should not be silently removed, condensed away, or replaced merely for brevity.

When the charter is revised:

- previously approved details should be preserved unless explicitly changed
- overlapping ideas should be reconciled rather than silently deleted
- new decisions should be integrated into the relevant existing sections
- long-term context should remain present even when a feature is not currently being implemented
- implementation discoveries that conflict with the charter should be surfaced for deliberate design resolution

The project should be developed incrementally.

The initial vertical slice is **not a disposable prototype**.

It is Version 0.1 of the intended full architecture.

Systems built for the slice should be genuine foundations for the larger project where doing so is practical.

At the same time, the project must avoid speculative over-engineering for distant functionality that the slice does not yet exercise.

The guiding balance is:

> **Build the smallest complete form of the real architecture rather than either a disposable prototype or an unfinished version of the entire future game.**

---

### Canonical Corpus Note

Document authority, hierarchy, and change-control rules are now defined in:

```text
docs/canon/00_DOCUMENT_AUTHORITY_AND_INDEX.md
```

The charter status/preservation statement above was retained verbatim through the corpus split. On 2026-09-28 its first paragraphs were updated with user approval so they describe this document's post-split role accurately; the original monolithic wording remains preserved in `_archive/PRE_SPLIT_CANON_SNAPSHOT.md`. Detailed subsystem material has been relocated losslessly to canonical subsystem files according to `MIGRATION_MANIFEST.md`.

---

# 1. Core Vision

This project is a Minecraft Java Edition mod that transforms Minecraft into a finite, persistent, procedurally generated RPG simulation enhanced by language-model-driven cognition.

The core idea is to extend Minecraft's original procedural philosophy beyond terrain and into society itself.

Vanilla Minecraft is built around a simple but powerful premise:

> No two worlds are ever exactly the same.

Traditionally, that uniqueness applies primarily to the physical world:

- terrain
- biomes
- caves
- structures
- resource placement
- geography

The player enters a randomly generated landscape, finds a place within it, and decides what to build there.

This project takes that same abstract concept and applies it to the systems of an RPG.

The goal is to translate:

> randomly generated terrain

into:

> a randomly generated world of people, relationships, societies, conflicts, histories, and civilizations.

The world should not merely be a different arrangement of mountains and forests.

It should become a different arrangement of human lives.

The same procedural philosophy should eventually govern:

- who becomes friends
- who becomes enemies
- who falls in love
- who has children
- who dies
- who becomes influential
- which families rise or disappear
- which settlements prosper
- which settlements decline
- which rumors spread
- which factions rise or collapse
- which wars occur
- which shortages create crises
- which NPCs become important
- which quests emerge
- which places become historically significant
- which events define the world's history
- which hostile forces become significant
- how the player is remembered

The LLM is the enabling technology that allows procedural variation to expand beyond geometry and into meaning.

Traditional procedural generation is extremely effective at creating physical variation because terrain, placement, and numerical simulation can be represented computationally.

Human relationships, motives, conversations, beliefs, negotiations, grudges, ambitions, interpretations, and unanticipated player propositions are much harder to represent comprehensively using predefined branching logic alone.

The language model allows structured, procedurally evolving world state to be interpreted as believable social reasoning and natural language.

It therefore serves as a bridge between:

> procedural simulation

and:

> emergent human narrative.

The player should not feel like they are moving through a prewritten RPG whose content merely appears in different places.

They should feel like they have entered a society that could only have existed in this particular world.

The player is not necessarily a chosen hero.

They are another person entering an already functioning world.

Instead of only asking:

> "Where should I build my house?"

the larger RPG question becomes:

> "Where do I fit into this society?"

The player may become:

- a merchant
- a criminal
- a respected citizen
- an adventurer
- a political figure
- a mercenary
- a landowner
- a family member
- an enemy of a faction
- a trusted friend
- a historical figure
- or someone relatively ordinary

Their place in the world emerges from interaction rather than being assigned by a scripted role.

The long-term objective is for the world to evolve continuously and irreversibly.

Two worlds could begin with:

- the same handcrafted towns
- the same initial NPC archetypes
- the same major POI archetypes
- the same foundational systems

and still end up, many hours later, with societies that are almost completely different.

One world may experience war.

Another may remain peaceful.

One family may become politically dominant.

In another world, that family may disappear entirely.

A minor NPC in one playthrough may die young.

In another, that same starting character may become one of the most important people in the world's history.

A mine may remain an ordinary workplace in one world.

In another, it may collapse, ruin a town's economy, become abandoned, become infested, be occupied by hostile actors, and eventually become the site of a major historical event.

The fundamental design goal is:

> **Apply Minecraft's procedural uniqueness to every layer of an RPG, not merely the terrain.**

Minecraft's original promise is that no two worlds are ever the same.

This project extends that promise to civilization itself.

The world should not merely give the player a unique place to build.

It should give the player a unique society to enter, understand, influence, and find a place within.

Because that society evolves through deterministic simulation, procedural randomness, AI interpretation, NPC interaction, hostile activity, and player action:

> **It should never repeat itself exactly.**

The ultimate objective is not simply:

> Minecraft with AI NPC dialogue.

It is:

> **A persistent procedural RPG simulation in which deterministic systems create and maintain a coherent world, AI allows appropriate actors to interpret and reason about that world, and the interaction between simulation, AI, randomness, and human players produces an effectively endless emergent history.**

---

# 2. Non-Negotiable Design Principles

## 2.1 Local AI Is the Default and Recommended Single-Player Experience

The complete intended single-player game must remain playable indefinitely without recurring AI fees.

For a single-player user, local inference is therefore:

- the default
- the recommended mode
- the canonical free experience

The mod must not require a paid cloud service in order to provide its complete intended gameplay.

There will be no non-AI gameplay fallback mode.

This means the game will not offer an alternative mode that replaces AI-driven interaction with a non-AI design. It does not mean every authoritative state transition must wait on inference. Core deterministic actions, such as accepting, progressing, or turning in a quest, should remain available through deterministic interaction affordances, with AI enriching them where it adds something fundamentally different (§2.2). If inference becomes unavailable, AI-dependent interactions pause with clear status (`04` §2.9–§2.10) while authoritative world state and deterministic progression remain intact.

It is acceptable for local mode to require reasonably modern hardware.

It is also acceptable for some lower-end systems to be incapable of running the mod well using local inference.

Performance efficiency remains extremely important, but hardware requirements should not force the project to abandon meaningful AI mechanics.

Optional cloud inference may later be supported through a bring-your-own-key model.

In multiplayer, inference responsibility belongs to the authoritative server operator rather than to each connected player.

---

## 2.2 Use AI Only Where AI Adds Something Fundamentally Different

Before introducing an AI inference call, ask:

> Can ordinary deterministic Java logic accomplish this equally well?

If yes, Java should handle it.

AI should generally not perform:

- pathfinding
- terrain noise
- structure placement calculations
- coordinate calculations
- direction calculations
- inventory operations
- combat resolution
- damage resolution
- economy arithmetic
- quest-state tracking
- routine schedules
- ordinary relationship arithmetic
- basic social-state changes
- deterministic world simulation
- simple random selection
- persistence
- validation
- ordinary mob movement
- per-tick target pursuit

AI should primarily perform tasks involving:

- natural-language dialogue
- understanding arbitrary player language
- interpretation
- contextual reasoning
- negotiation
- persuasion
- ambiguous intent
- high-level planning
- NPC cognition
- interpretation of memories
- emergent quest formulation
- high-level hostile strategy where appropriate
- high-level story/world decisions
- situations whose possible inputs cannot realistically be enumerated beforehand

---

## 2.3 The LLM Is Never the Source of Truth

Authoritative state always lives in deterministic game systems.

An LLM must never directly modify authoritative world state.

The general pattern is:

```text
AUTHORITATIVE GAME STATE
        ↓
select relevant context
        ↓
AI inference
        ↓
structured proposal
        ↓
deterministic validation
        ↓
authorized state change
        ↓
persistence
```

The model proposes.

Java validates and executes.

If an NPC states something false, that should ideally happen because:

- the NPC is mistaken
- the NPC heard incorrect information
- the NPC lacks complete information
- the NPC is intentionally lying

Free-form language generation can never make accidental factual mistakes mathematically impossible.

Therefore:

> **The architecture must prevent hallucinated statements from becoming authoritative facts, aggressively ground dialogue in structured context, and validate gameplay-critical factual responses where practical.**

For mechanically important facts such as:

- directions
- negotiated rewards
- quest conditions
- location identities
- critical names
- required factual answers

deterministic semantic checks and retry logic should be used where feasible.

A model accidentally saying something false is a dialogue-generation failure.

It is not permission to alter world truth.

---

## 2.4 AI Backends Must Be Model-Agnostic

Higher-level game systems must not depend on one specific:

- model
- runtime
- provider
- local server
- cloud API
- inference location

The intended architecture is conceptually:

```text
GAME SYSTEMS
     ↓
AI ORCHESTRATOR
     ↓
COMMON AI BACKEND INTERFACE
     ↓
ACTIVE INFERENCE BACKEND
```

Higher-level systems such as:

- NPC cognition
- dialogue
- memories
- context construction
- quests
- structured result handling
- validation
- simulation

must not care whether inference is local or remote.

Changing the backend should not require rewriting NPC or simulation logic.

This abstraction must also hold across single-player and multiplayer deployment.

The same higher-level systems should work whether inference is performed by:

- a model running on the single-player user's machine
- a cloud API configured by the single-player user
- a model hosted privately by a dedicated Minecraft server operator
- a cloud API configured by a dedicated server operator
- a shared inference service used by several linked Minecraft server instances

---

## 2.5 Model Capability May Improve Gameplay Quality, Never Authority

Players or server operators may eventually use models with substantially different capabilities.

A stronger model may improve:

- dialogue quality
- personality consistency
- understanding of indirect language
- ambiguous-intent interpretation
- negotiation
- persuasion
- social reasoning
- long-term planning
- memory utilization
- emergent quest reasoning
- strategic hostile reasoning
- high-level world decisions

However, stronger models do not receive greater authority.

All models operate inside the same deterministic rules and permitted action spaces.

If an NPC may choose:

```text
ACCEPT
REJECT
COUNTEROFFER
```

a stronger model may choose more intelligently.

It may not invent an unsupported authoritative action simply because it is more capable.

Model capability scales the quality of cognition.

It does not scale control over authoritative game state.

This also allows the game to improve naturally as better models and hardware become available in the future.

---

## 2.22 Managed Local AI Provisioning Is the Intended Default Consumer Experience

The normal Windows single-player local-AI experience should not require the player to understand or manually configure AI infrastructure.

For the consumer-facing local edition, the intended setup experience is approximately:

```text
Install mod
    ↓
Launch Minecraft
    ↓
automatic hardware check
    ↓
"Additional AI files required: X GB"
    ↓
player approves download
    ↓
approved runtime + curated model are provisioned automatically
    ↓
integrity checks
    ↓
runtime starts automatically
    ↓
AI ready
    ↓
play
```

The player should not normally need to understand or manually configure:

- inference servers
- localhost endpoints
- GGUF files
- quantization
- context windows
- model identifiers
- command-line model installation

The local consumer path should use a project-curated set of model packages matched to supported hardware tiers. Hardware detection should select the recommended tier automatically where practical. Manual override exists for exceptional hardware or advanced users rather than as the ordinary setup path.

The current intended Windows hardware tiers are conceptually centered around supported configurations such as:

- approximately 8 GB VRAM
- approximately 12 GB VRAM
- approximately 16 GB or more VRAM

The exact model, quantization, memory budget, and minimum/recommended hardware attached to each tier must be selected from testing and may change between releases.

The public mod documentation should clearly distinguish:

- the local-AI path and its stated hardware requirements
- the cloud/BYOK path for players who cannot or do not want to run the supported local package

The local edition should remain the simplest path for a machine that meets the stated requirements.

Managed local setup should minimize technical explanation while remaining accurate about what the software is doing. A normal prompt may say, for example:

> **Additional AI files required**  
> This version uses local AI processing and requires an additional X GB download.  
> **Download and Continue**

Detailed runtime/model information may live behind an Advanced Details surface. Exact disclosure wording must remain compatible with applicable platform, distribution, licensing, and legal requirements.

The project should prefer a self-contained user-space installation that does not require machine-wide administration. Where possible:

- files live in a user-writable project/game-managed directory
- no permanent Windows service is installed
- the inference runtime runs only while needed by the game
- the game/runtime manager starts it automatically
- it shuts down automatically when the relevant Minecraft session ends

The relevant session is the period during which an RPG world is open in the running game. The runtime starts when an RPG world begins opening, so model loading overlaps world loading. It stops when no RPG world remains open, after a short grace period that avoids reloading the model when the player quickly re-enters a world. It never outlives the Minecraft process, including when Minecraft exits abnormally.

The default consumer experience is therefore:

> **Install, approve the required local-AI download, and play.**

Manual local-backend configuration remains an advanced-user path. Cloud/BYOK remains an alternative compute path, especially for unsupported or weaker machines.

