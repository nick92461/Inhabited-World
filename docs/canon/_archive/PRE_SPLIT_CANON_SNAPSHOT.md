# Canonical Project Scope

## Local-AI Procedural RPG for Minecraft

### Status

This document defines the current canonical vision, architecture, constraints, development philosophy, implementation rules, long-term direction, and initial vertical-slice scope for the project.

It is the project's primary source of truth unless explicitly superseded by a later approved revision.

All contributors, coding agents, design discussions, and implementation plans should treat this document as the shared authoritative description of the project.

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

It is acceptable for local mode to require reasonably modern hardware.

It is also acceptable for some lower-end systems to be incapable of running the mod well using local inference.

Performance efficiency remains extremely important, but hardware requirements should not force the project to abandon meaningful AI mechanics.

Optional cloud inference may later be supported through a bring-your-own-key model.

In multiplayer, inference responsibility belongs to the authoritative server operator rather than to each connected player.

---

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

## 2.6 Optional Bring-Your-Own-Key Cloud Inference

A future version may support optional cloud inference using credentials supplied by the party responsible for inference.

For single-player, that party is the player.

Cloud inference exists for users who:

- cannot comfortably run Minecraft and a local LLM simultaneously
- prefer reduced local hardware load
- want to use a stronger model
- otherwise prefer remote inference

The mod developer should not be responsible for paying ordinary player inference costs.

Cloud usage therefore uses the user's own provider account and API key.

The single-player product philosophy is:

> **AI is required. Paying for AI is not.**

Local inference remains free and recommended.

Cloud inference is an optional alternative compute location.

In multiplayer, the server operator assumes this responsibility instead of each connected player.

---

---

## 2.7 BYOK Must Remain Simple

Ordinary single-player users should not need to understand AI infrastructure.

Cloud mode should use one active model at a time.

Normal setup should not require users to configure:

- multi-model routing
- multiple simultaneous providers
- endpoint URLs
- raw model IDs
- temperatures
- token limits
- JSON
- request-routing rules

Recommended options should be represented as understandable presets.

The intended normal single-player flow is approximately:

1. Choose a recommended model/tier.
2. Open the provider's signup link.
3. Create an account.
4. Create an API key.
5. Paste the key into the mod.
6. Test the connection.
7. Play.

Advanced configuration may exist later, but it must not be required for ordinary use.

Dedicated-server operators may reasonably receive additional administrative controls because server deployment is inherently more technical.

Ordinary players connecting to such a server should not need to configure any AI backend themselves.

---

---

## 2.8 Automatic Backend Selection

The eventual default single-player backend mode should be:

> **Automatic — Recommended**

At startup:

1. Check for a compatible local inference backend.
2. If one is available and healthy, use local inference.
3. Otherwise check for a configured cloud backend/API key.
4. If configured and valid, use cloud inference.
5. If neither exists, clearly explain the player's setup options.

Manual selection should eventually permit:

- Automatic
- Local
- Cloud

Automatic mode should reduce setup friction without removing user control.

Dedicated servers use an equivalent operator-selected backend configuration.

They do not consume credentials belonging to connected clients.

---

---

## 2.9 Paid Cloud Inference Must Never Begin Silently Mid-Session

Automatic startup selection does not authorize an invisible transition from free local inference to paid cloud inference during gameplay.

In single-player, if local inference becomes unavailable after gameplay begins, the game should prompt the player with explicit choices such as:

- Reconnect Local AI
- Switch to Cloud
- Open AI Settings

The game must not silently begin spending the player's API balance.

Paid usage must remain visible and intentional.

For a dedicated server, failover behavior is controlled by the server operator.

A server may fail over between operator-owned inference resources only if the operator explicitly configures such behavior.

Connected players are never assumed to provide or pay for fallback inference merely by joining.

---

---

## 2.10 AI Setup and Status Are First-Class UI Concepts

AI is fundamental to the game and should therefore have clear top-level UI representation.

The main menu or equivalent interface should eventually expose an AI status/setup entry.

Example single-player states:

- AI: Local ✓
- AI: Cloud ✓
- AI Setup Required
- AI Unavailable

A dedicated AI setup screen should clearly communicate:

- active mode
- active backend
- active model
- health/readiness
- whether setup is required
- how to switch local/cloud mode

The player should never need to inspect logs or manually edit configuration files merely to determine whether AI is ready.

When connected to a dedicated server, the client should instead understand that AI is **server-provided**.

A multiplayer client should not be asked to configure a local model or API key for a server whose operator already supplies inference.

---

---

## 2.11 API Credentials Are Strictly User- or Operator-Controlled Secrets

API credentials must be handled conservatively.

An API key must never be:

- inserted into NPC prompts
- stored in ordinary world-save data
- sent to multiplayer clients
- written to normal logs
- included in ordinary telemetry
- exposed in crash reports where avoidable
- transmitted to any system that does not require the credential to perform the selected inference

In single-player, the key belongs to the player.

In dedicated multiplayer, the key belongs to the server operator.

A dedicated server's cloud credential must remain server-side.

Clients must never receive or need access to that credential.

Where practical, future implementation should prefer:

- operating-system credential storage
- server secret storage
- equivalent secure credential facilities

over ordinary plaintext configuration.

The relevant UI or administrative configuration should provide an obvious way to remove or replace the stored key.

Most importantly:

> **The project must never require a single-player user's BYOK credential to transit infrastructure controlled by the mod developer.**

For ordinary single-player BYOK:

```text
Minecraft client
      ↓
selected provider
```

not:

```text
Minecraft client
      ↓
mod developer's server
      ↓
selected provider
```

A developer who independently operates a public Minecraft server may of course configure that server's own operator-controlled credential.

In that case the credential belongs to the server deployment, not to its connected players.

---

---

## 2.12 Local Backend Detection Requires Capability Validation

Finding something listening on localhost is not enough.

Detection should distinguish between:

- endpoint discovered
- runtime identified
- model identified
- capabilities checked
- backend ready

Before declaring a local backend ready, the game or server should verify whatever capabilities are ultimately required, potentially including:

- endpoint health
- protocol compatibility
- model availability
- sufficient context capacity
- acceptable structured-output behavior
- basic inference readiness

The UI should display actual readiness rather than mere process detection.

For dedicated servers, "local" means local to or privately hosted by the server/operator infrastructure.

It does not refer to the connected player's computer.

---

---

## 2.13 Cloud Cost Transparency

Single-player users using cloud inference should receive clear cost information.

Before setup, recommended cloud presets should provide approximate expectations such as:

- cost per gameplay hour
- cost per 100 gameplay hours

These must be presented as estimates.

Once actual gameplay data exists, the mod should preferably report locally calculated usage such as:

- input tokens
- cached input tokens when reported
- output tokens
- current-session estimated cost
- current-world estimated cost
- average cost per gameplay hour
- projected cost for another 100 hours based on actual usage

Pricing metadata must not be assumed permanent.

Provider prices change.

Cost telemetry should remain local unless a user explicitly chooses otherwise.

Dedicated-server deployments should expose equivalent operator-facing usage information where applicable.

A cloud-backed server operator should be able to understand:

- total requests
- token usage
- cached usage where reported
- estimated cost
- usage by world or server instance where practical

Ordinary connected players do not need to manage the backend bill.

---

---

## 2.14 Performance Claims Must Be Evidence-Based

Cloud inference removes local LLM model storage and inference work from the machine that would otherwise perform inference.

For single-player, this can reduce:

- GPU contention
- CPU contention
- VRAM usage
- system-RAM pressure

For a dedicated server, hosting inference on separate hardware can similarly isolate Minecraft simulation resources from inference resources.

However, the project must not advertise universal numerical performance claims without measurement.

Statements such as:

> Cloud inference removes the local model's inference and VRAM workload.

are architectural facts.

Statements such as:

> Cloud mode improved FPS from X to Y.

must come from controlled benchmarks on named configurations.

---

---

## 2.15 Multiplayer AI Is Server-Authoritative

The long-term architecture should support dedicated multiplayer servers that specialize in this RPG experience.

A server may host:

- one persistent RPG world
- multiple independent RPG worlds
- several related world instances
- a larger network of linked Minecraft servers

For multiplayer, AI inference is a **server responsibility**.

Connected players should not need to:

- run their own local LLM
- provide their own API key
- independently select the NPC model
- pay per-interaction inference costs simply to participate

The server operator chooses and provides inference.

The server operator may choose:

### Server-Hosted Private Inference

The operator may run a model on:

- the Minecraft server itself
- another machine on the same infrastructure
- a dedicated GPU inference server
- a private cluster

A large or commercially operated Minecraft server may therefore run a substantially more capable model than an average single-player computer can support.

### Operator-Supplied Cloud API

The operator configures its own provider account and credentials.

All server-side LLM usage is billed through the operator-controlled account.

### Shared Inference Across Linked Servers

Several Minecraft servers may share one model-serving layer.

However, **only model execution and optionally inference queueing may be centralized**.

Each authoritative Minecraft server must retain control of:

- selecting the relevant world state
- building game-aware context
- determining what an NPC knows
- validating returned proposals
- mutating authoritative state

Conceptually:

```text
WORLD SERVER A ─┐
WORLD SERVER B ─┼──→ SHARED MODEL / INFERENCE SERVICE
WORLD SERVER C ─┘
```

not:

```text
WORLD SERVERS
      ↓
CENTRAL SERVICE OWNS GAME LOGIC
```

The shared service should not need direct authoritative access to every game's mutable world state.

---

---

## 2.16 Multiplayer Clients Do Not Own AI Authority

In multiplayer, the dedicated server remains authoritative for:

- world state
- NPC state
- quest state
- memories
- relationship state
- world history
- AI request construction
- AI result validation
- final mutations

The client may send player language/input to the server.

The server determines:

- which NPC is being addressed
- what that NPC actually knows
- which memories are relevant
- which actions are legal
- whether returned AI output remains valid

Conceptually:

```text
PLAYER CLIENT
      ↓
player message / interaction
      ↓
AUTHORITATIVE SERVER
      ↓
context snapshot
      ↓
SERVER-SELECTED AI BACKEND
      ↓
structured result
      ↓
SERVER VALIDATION
      ↓
authoritative mutation
      ↓
result replicated to clients
```

A modified client must never be able to submit a fabricated AI result as authoritative.

The same fundamental rule applies:

> **The LLM proposes. The authoritative game system decides.**

---

---

## 2.17 Multiplayer Backend Choice Belongs to the Server Operator

A dedicated server chooses the inference policy for its hosted RPG environment.

That policy may be:

- server-local/private model
- operator-owned cloud API
- shared network inference service

The normal multiplayer design does **not** require each player to bring an individual key.

The operator decides:

- model/backend
- hardware allocation
- cloud account
- context limits
- concurrency limits
- inference scheduling
- operational budget

Conceptually:

```text
SINGLE PLAYER

player owns authoritative world
player chooses/provides inference


MULTIPLAYER

server owns authoritative world
server operator chooses/provides inference
```

---

---

## 2.18 Multiplayer and Multi-Instance Isolation

When one inference service handles requests from multiple worlds or servers, every request must carry sufficient identity to prevent cross-world contamination.

Every RPG world should therefore receive a **permanent unique world ID** when its WorldPlan is first created.

That world ID persists for the lifetime of the save.

Relevant inference identity may include:

- server/network instance ID
- permanent world ID
- dimension ID where applicable
- conversation ID
- NPC ID
- player UUID where relevant
- request ID

A result generated for:

```text
Server A
World 9142
NPC Haldor
```

must never be allowed to mutate:

```text
Server B
World 1288
NPC Haldor
```

even if both worlds use the same semantic NPC template.

The permanent world ID also provides a useful stable key for:

- inference metrics
- per-world cloud cost tracking
- logging
- save diagnostics
- world isolation

---

---

## 2.19 Multiplayer Inference Scheduling

A dedicated server may have many players generating AI-relevant interactions simultaneously.

Long-term multiplayer inference therefore requires server-side scheduling, queueing, and prioritization.

Potential priorities include:

### High

- player actively waiting for an NPC response
- immediate player-facing negotiation
- synchronous important interaction

### Medium

- important nearby consequence
- significant NPC cognition related to active players

### Low

- off-screen planning
- memory compression
- Director inference
- distant NPC cognition

Server operators may eventually need controls such as:

- maximum concurrent inference requests
- queue depth
- per-player pacing
- timeout behavior
- model/context limits
- low-priority deferral

These controls protect:

- server tick stability
- inference hardware
- cloud budget
- response latency

They must not change the deterministic game rules.

---

---

## 2.20 AI Transport Uses Plain Data Contracts

AI requests and responses should cross the backend boundary as plain structured data.

The backend contract should not depend on:

- live Minecraft entities
- `ServerWorld`
- UI objects
- arbitrary callbacks
- mutable Minecraft runtime objects

An AI request should contain an immutable representation of everything required for inference.

An AI result should contain structured output that can later be validated against current authoritative state.

This is important for:

- testability
- local inference
- cloud inference
- dedicated inference servers
- linked multiplayer servers
- backend replacement

The backend should not need to know whether its request originated from:

- integrated single-player server
- dedicated multiplayer server
- another network topology

---

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

---

# 20. One Shared Model Per Active Backend

NPCs do not each run their own language model.

There is one logical shared inference service for the active backend.

Conceptually:

```text
SHARED MODEL
    ↓
Haldor context
    ↓
acts as Haldor

SHARED MODEL
    ↓
Innkeeper context
    ↓
acts as Innkeeper
```

NPC identity lives in persistent data.

It is supplied through context.

Increasing NPC count therefore increases structured simulation data, not loaded model count.

In multiplayer, the logical shared inference service may physically be:

- one process
- one dedicated server
- one GPU machine
- a GPU cluster
- a cloud API

The gameplay abstraction remains the same.

---

---

# 21. AI Backend Integration

The mod communicates with AI through a replaceable backend interface.

```text
Minecraft authoritative systems
        ↓
AI orchestration
        ↓
plain-data backend contract
        ↓
active model service
```

The active backend may be:

## Local / Private

```text
Minecraft
    ↓
local/private inference service
    ↓
model
```

## Cloud

```text
Minecraft
    ↓
provider API
    ↓
model
```

Version 0.1 validates local inference first.

BYOK and multiplayer deployment must not delay the local slice.

---

---

# 22. AI Orchestration Layer

All inference calls pass through a dedicated orchestration layer.

Responsibilities include or may eventually include:

- selecting backend
- building context
- retrieving memories
- selecting NPC identity
- injecting world facts
- injecting quest facts
- defining allowed actions
- requesting structured output
- parsing
- validation
- scheduling
- concurrency control
- prioritization
- metrics
- usage telemetry
- request/world isolation

Raw model calls should not be scattered throughout gameplay classes.

In multiplayer, the orchestration layer that understands game state remains associated with the authoritative Minecraft server.

A shared remote model service performs inference but does not own world-aware validation.

---

---

# 23. AI Request Data Contract

Requests passed to an AI backend should be immutable plain data.

They should not contain:

- live entities
- mutable Minecraft worlds
- direct references to registries
- UI components
- arbitrary callbacks
- client-only state

A request contains everything the backend needs to perform inference.

A result contains structured data for later authoritative validation.

This makes the same backend contract usable for:

- local testing
- local inference
- cloud inference
- remote dedicated inference
- server networks

---

---

# 24. AI Request Lifecycle

AI inference may take seconds while the game continues changing.

Every request therefore operates on a snapshot-and-revalidation lifecycle.

```text
AUTHORITATIVE GAME THREAD
        ↓
copy relevant state
        ↓
immutable request snapshot
        ↓
assign request ID
        ↓
attach permanent world ID
        ↓
attach other relevant identity
        ↓
asynchronous inference
        ↓
structured result
        ↓
safe authoritative-thread handoff
        ↓
verify world/request still valid
        ↓
revalidate current preconditions
        ↓
accept or reject
        ↓
authoritative mutation
```

The AI worker never reads live mutable game state.

If relevant state changes before the result returns, the result may be stale.

Examples include:

- player leaves world
- server switches/unloads world
- NPC becomes unavailable
- quest changes
- another player changes the negotiated state
- server shuts down

Stale output must not commit.

Revision/version fields on mutable domain objects may be used where useful.

## Dialogue Must Match Committed State

If an AI action proposal fails validation, prose implying that rejected action occurred must also be discarded.

The game must never allow:

> "Fine, thirty emeralds."

while authoritative quest reward remains ten.

Action and prose should remain semantically atomic.

The system may retry with updated state.

A short temporary in-character waiting/error line is permissible as error handling.

## Conversation Concurrency

Initially:

> **Only one active inference request per conversation at a time.**

Different independent conversations may eventually proceed concurrently subject to scheduling.

## Correlated Logging

Every request carries one stable request ID throughout:

- trigger
- snapshot
- backend request
- backend response
- parsing
- validation
- retry
- mutation
- completion/failure

In multiplayer, logs must also make world/server identity clear.

---

---

# 25. Testable AI Boundaries

AI orchestration should be testable without launching a full Minecraft client where practical.

Useful design choices include:

- injectable authoritative-thread handoff
- fake/scripted AI backend
- immutable snapshots
- deterministic validators
- Codec-based structured parsing

Tests should be able to simulate:

- valid reply
- malformed reply
- stale reply
- world change during inference
- player disconnect
- invalidated conversation
- validation failure
- retry
- backend failure
- wrong-world response
- discarded result

---

---

# 26. Prompt Construction and Context Eviction Are Separate Concerns

Prompt ordering should optimize stable-prefix reuse where practical.

Likely order:

1. NPC identity/personality
2. stable world facts
3. relevant quest/state
4. relevant memories
5. recent dialogue
6. current player input

Stable material near the beginning may improve:

- local prompt caching
- KV/prefix reuse
- cloud cached-input pricing

However, prompt ordering does not define trimming priority.

When context must be reduced, remove based on relevance.

Likely trimming priority:

1. least-relevant memories
2. oldest nonessential dialogue
3. lower-value supporting context

Protect:

- current player message
- active state being reasoned about
- essential quest constraints
- authoritative facts required for the decision

Different models tokenize differently.

Use:

- token estimates
- safety margins
- actual backend-reported usage where available

---

---

# 27. Context Management

Never give the model the entire world.

Retrieve only relevant information.

Example:

Player asks where the mine is.

Relevant:

- NPC identity
- relationship with player
- NPC knowledge of mine
- relevant geography
- active quest
- recent conversation

Irrelevant:

- unrelated NPC histories
- distant economics
- unrelated political events
- irrelevant world history

Good context selection improves:

- quality
- latency
- memory use
- local performance
- cloud cost
- multiplayer throughput

---

---

# 28. Dialogue

Dialogue is a tightly scoped natural-language interaction.

Players should eventually speak freely rather than rely entirely on dialogue buttons.

NPC responses should generally be concise and in-character.

This improves:

- realism
- latency
- readability
- token efficiency
- server throughput

NPCs are not general assistants.

They are people in the world.

---

---

# 29. Intent Interpretation

AI may interpret arbitrary player language into structured meaning.

Example:

> "Where did you say that old mine was?"

```text
intent = ASK_LOCATION
subject = OLD_MINE
```

Example:

> "I'll do it for twenty instead."

```text
intent = NEGOTIATE_REWARD
quest = LOST_HAMMER
requestedReward = 20
```

Deterministic systems then reason about the structured request.

---

---

# 30. Initial AI Demonstration Requirement

The vertical slice must demonstrate why an LLM is useful.

A strong example is negotiation.

Blacksmith offers ten emeralds.

Player asks for thirty.

Game state defines:

- personality
- relationship
- desperation
- legal reward range

AI chooses:

```text
ACCEPT
REJECT
COUNTEROFFER
```

Java validates.

Only then does state change.

The slice should also demonstrate arbitrary natural-language questions about generated geography.

Gameplay-critical factual answers should be checked where practical.

---

---

# 31. Quest Architecture

Quest handling distinguishes:

## QuestDefinition

Reusable authored content:

- quest identity
- possible objectives
- legal transitions
- reward constraints
- related NPCs/POIs

## QuestInstance

A live occurrence containing:

- assigned player UUID or future ownership model
- current state
- negotiated reward
- progress
- timestamps
- completed objectives
- instance-specific parameters

Example:

```text
NOT_STARTED
    ↓
ACCEPTED
    ↓
OBJECTIVE_COMPLETE
    ↓
RETURN_TO_GIVER
    ↓
COMPLETE
```

Future dynamic quests create new instances using the same machinery.

Multiplayer may eventually support:

- player-specific quests
- party quests
- shared world objectives

Those are future scope.

---

---

# 32. World History

World history is an append-oriented authoritative event log.

Version 0.1 begins with only needed events.

Examples:

- quest accepted
- quest completed
- major discovery
- relationship-changing event

Future examples:

- birth
- death
- marriage
- settlement attack
- political event
- war
- migration
- disaster

Events use meaningful total elapsed world time rather than time-of-day.

Sleeping or `/time set` must not corrupt historical ordering.

The authoritative server writes history.

---

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

---

# 35. AI Inference Is Event-Driven

NPCs do not continuously invoke a model.

Routine existence remains deterministic.

Without AI:

- walking
- sleeping
- working
- eating
- opening doors
- travel
- ordinary combat
- schedules
- routine state updates

AI wakes when meaningful cognition is required.

Examples:

- player conversation
- negotiation
- significant personal event
- high-level decision
- unusual world-state change
- occasional planning

Guiding rule:

> **Java handles existence. AI wakes up when something requires thought.**

---

---

# 36. Inference Scheduling and Priority

Inference must not carelessly compete with Minecraft.

Possible priorities:

## High

- player waiting on NPC
- immediate negotiation
- synchronous social decision

## Medium

- nearby meaningful reaction
- player-caused consequence

## Low

- off-screen cognition
- memory compression
- Director work
- distant planning

Low-priority inference may be deferred.

Single-player local inference may avoid heavy work during:

- combat
- rapid exploration
- chunk generation
- low-FPS periods

Good opportunities include:

- dialogue screens
- menus
- sleeping
- idle periods

Dedicated servers similarly prioritize player-facing inference and may defer background cognition under load.

---

---

# 37. Performance Philosophy

The objective is not zero inference cost.

The objective is sparse, efficient inference that preserves responsive gameplay.

A temporary FPS reduction while an NPC thinks locally may be acceptable.

Persistent degradation is not.

Optimize for:

- model reuse
- short prompts
- concise output
- limited context
- sparse inference
- controlled concurrency
- off-screen abstraction
- event-driven cognition
- prefix reuse

Multiplayer additionally requires attention to:

- queue latency
- shared-model throughput
- concurrent player load
- server tick stability
- inference hardware utilization

---

---

# 38. Model Size and Capability Philosophy

Single-player development should begin by evaluating a relatively small quantized model, roughly in the 3B–8B class.

The objective is to determine the smallest model reliably capable of:

- concise dialogue
- intent interpretation
- structured output
- negotiation
- limited reasoning

Architecture should help small models through:

- excellent context
- narrow tasks
- constrained output
- deterministic validation

Stronger models remain supported.

A dedicated professional server may reasonably operate a significantly larger or more capable model.

Better model:

> better cognition

not:

> different rules.

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

---

# 44. Long-Term Dynamic Quest Design

Eventually, quests should emerge from world conditions and NPC goals.

Example:

```text
mine closes
    ↓
iron supply falls
    ↓
blacksmith lacks materials
    ↓
weapon production declines
    ↓
guards weaken
    ↓
security problems increase
```

NPCs have different interests.

A conversation with a player may transform a goal into a negotiated agreement.

At that moment the game creates a QuestInstance.

The quest need not have existed beforehand.

Version 0.1 uses one authored quest on generalized machinery.

---

# 45. Long-Term Game Director

A high-level AI Game Director may eventually examine compressed world state.

Possible inputs:

- settlement conditions
- political tensions
- important deaths
- faction conflicts
- shortages
- unresolved pressures
- player actions

The Director proposes major developments.

It does not simulate ordinary mechanics.

Example:

```text
Blackwood requests food aid from Capital.
```

Normal game systems execute validated consequences.

One expensive Director thought should ideally generate many cheap downstream consequences.

In multiplayer, the Director is server-authoritative and reasons about shared world state rather than separately for every player.

---

---

# 46. Deterministic World Simulation

Long-term simulation may include:

- population
- food
- resources
- production
- trade
- prices
- wealth
- security
- faction strength
- crime
- migration
- warfare
- political relationships

These systems should primarily be deterministic or conventional probabilistic Java simulations.

AI interprets consequences.

AI does not perform basic bookkeeping.

---

---

# 47. Endgames and Major Events

The world is intended to remain effectively open-ended.

Certain state thresholds may trigger major authored or semi-authored events.

Example:

```text
faction tension threshold
      ↓
succession crisis
      ↓
war / rebellion / negotiation
```

Some eventual endgame states may remain guaranteed to be reachable despite unpredictable AI and player decisions.

Completing an endgame does not necessarily terminate the world.

---

---

# 48. Shops and Structured Interactions

NPC merchants may eventually provide RPG-style shop interfaces.

Transactions remain deterministic and authoritative.

AI may influence:

- negotiation
- special agreements
- refusal
- relationship-dependent terms

AI must not fabricate inventory or bypass transaction rules.

A minimal shop may appear in Version 0.1 only if it does not threaten core delivery.

---

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
- finite map
- boundary behavior
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
- local model setup
- demo video
- useful automated tests

BYOK and dedicated multiplayer must not delay Version 0.1.

---

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

35. Maintain reasonable performance through sparse, scoped, asynchronous, validated inference.

Dedicated multiplayer does not need to be demonstrated in Version 0.1.

---

# 57. Development Philosophy for Coding Agents

When implementing this project:

Do not build the entire future game immediately.

Do not replace deterministic systems with AI merely because AI exists.

Do not allow AI to mutate authoritative state directly.

Do not trust model output without validation.

Do not make every NPC continuously infer.

Do not make every monster continuously infer.

Do not dump the world into prompts.

Do not prematurely implement future systems.

Do not hardwire gameplay to one local model implementation.

Do not hardwire AI ownership to the client.

Do not preserve vanilla content merely because Mojang generates it by default when it conflicts with intentional RPG-world design.

Do not leave hostile spawning uncontrolled where semantic regions should govern it.

Do not implement one-off quest-coordinate spawn hacks where reusable semantic regions solve the same problem.

Do not conflate spawn governance with hostile cognition.

Do not construct speculative frameworks simply because they might someday be useful.

Instead:

- build real foundations needed by the slice
- preserve meaningful extension points
- prefer end-to-end completeness over broad unfinished systems
- keep authoritative state explicit
- use structured AI outputs for actions
- separate authored content from engine rules where practical
- favor semantic identity over raw coordinates/entity instances
- preserve vanilla systems when they remain coherent
- instrument AI performance early
- treat tokens, VRAM, RAM, CPU, GPU, latency, concurrency, and cloud cost as finite resources
- prefer deterministic code unless AI offers genuinely different capability

A future requirement should influence current architecture when ignoring it now would create substantial future rework.

---

## 57.1 Coding-Agent Operating Rules

The coding agent operates in **manual handoff mode by default**.

Normal workflow:

1. Inspect current code.
2. Explain intended change.
3. Provide exact code/files/patches/commands.
4. User manually applies them.
5. User runs/tests.
6. User reports results.
7. Continue.

The coding agent must not directly modify the project unless explicitly authorized for that specific task.

Permission to:

- inspect
- analyze
- review
- search
- suggest

does not imply permission to modify.

One authorized edit does not grant permanent edit authority.

The agent may suggest Git commands.

However:

> **The coding agent is NEVER permitted to execute `git push`.**

This prohibition is absolute.

Only the user publishes repository changes remotely.

Preferred responsibility split:

**Coding agent:** inspect, reason, design, explain, provide code.

**User:** apply, run, evaluate, publish.

---

---

# 58. Long-Term Multiplayer Deployment Model

The project may eventually support servers whose primary purpose is to host this RPG experience.

Multiplayer is not currently a mandatory product requirement.

However, the architecture should preserve it where doing so is inexpensive.

A future server operator may host:

- one RPG world
- multiple independent RPG worlds
- a network of related worlds
- multiple linked Minecraft servers

The authoritative server owns simulation.

The operator owns inference responsibility.

---

## 58.1 Server-Provided AI

A player joining a dedicated RPG server should normally experience:

```text
Join server
    ↓
server already has AI configured
    ↓
play
```

not:

```text
Join server
    ↓
install model
    ↓
create API account
    ↓
paste key
```

The server provides inference as part of hosting the world.

---

## 58.2 Powerful Server-Side Models

A professional or large community server may operate:

- larger local models
- dedicated GPU servers
- multiple GPUs
- inference clusters

This creates a natural spectrum:

```text
lower-end single-player machine
    → small model

gaming desktop
    → stronger model

professional dedicated server
    → potentially very powerful model
```

The game should automatically benefit from stronger cognition without changing model authority.

---

## 58.3 Operator BYOK

A server operator may instead configure a cloud provider/API key.

Conceptually:

```text
Players
   ↓
Minecraft Server
   ↓
Server-owned inference configuration
   ↓
Cloud model
```

The operator assumes the cost.

Individual players do not each need an API account.

---

## 58.4 Linked Server Networks

A larger deployment may contain multiple Minecraft servers.

Example:

```text
Gateway
  ↓
World Server A
World Server B
World Server C
```

They may share a model-serving layer:

```text
Server A ─┐
Server B ─┼──→ Shared Inference Service
Server C ─┘
```

The shared component may contain:

- model serving
- batching
- queueing

Each authoritative Minecraft server still owns:

- game-state context selection
- NPC knowledge selection
- validation
- authoritative mutations

---

## 58.5 Server Operator Controls Model Policy

Operator controls may eventually include:

- backend
- model
- context limits
- concurrency
- queue behavior
- hardware allocation
- cloud budget
- failover

Players experience the configured quality as part of the server.

---

## 58.6 Server-Side Security

Server credentials remain server-side.

Never send:

- API keys
- backend secrets
- privileged configuration

to clients.

Modified clients cannot bypass authoritative validation by fabricating AI results.

---

## 58.7 Multiplayer Social Consequences Are Shared World Consequences

Multiple human players may influence one society.

Example:

- Player A earns Haldor's trust.
- Player B steals from Haldor.
- Player C hears a rumor about B.
- Player A later asks Haldor about B.

The social simulation should eventually support these interactions as consequences of one shared world.

NPCs do not treat all human players as one generic "player."

Relevant player identity and relationship information enters context.

---

## 58.8 Multiplayer Does Not Change the Core Thesis

Multiplayer adds more human actors to the emergent history.

It does not turn the project into:

> a normal Minecraft server with AI chatbots.

The server still represents one evolving procedural civilization.

---

# 59. Long-Term Success Condition

The project succeeds if a player can begin with familiar foundations and, hundreds of hours later, inhabit a society whose history could never have been authored completely in advance.

Original NPCs may have died.

Their descendants may remain.

Settlements may prosper, decline, change purpose, or disappear.

Wars may occur.

Relationships may form and collapse.

NPCs may remember events from decades earlier in world time.

Economic and political structures may change.

Dangerous places may change.

A wilderness may become safe.

A mine may become infested.

A settlement may come under organized attack.

A hostile human town may emerge as a genuine social faction.

Vanilla-derived structures may acquire unique histories.

A mineshaft may become:

- a town's source of wealth
- a disaster site
- a bandit refuge
- an infested ruin
- a faction asset
- the site of a betrayal
- the place two important NPCs first met

A minor NPC may independently explore a dangerous location and become central to history.

A child may eventually become a ruler.

None of this needs to be directly scripted.

The simulation creates circumstances.

AI interprets them.

NPCs act.

Hostile actors may eventually act strategically.

Human players intervene.

Together they create history.

In multiplayer, many human players may contribute to the same history.

The grand design principle remains:

> **Take Minecraft's defining procedural idea—that every world is unique—and extend it from geography to civilization.**

No two landscapes should be exactly the same.

No two societies should be exactly the same.

No two histories should be exactly the same.

No two player lives should be exactly the same.

The player does not merely find a place in a procedurally generated landscape.

They find a place in a procedurally evolving society.

That is the project.

---

# 60. Instructions for Coding Agents

This document is the canonical design charter.

Treat it as the project's primary source of truth.

The immediate target is Version 0.1.

Long-term material exists so early decisions do not unnecessarily block future design.

It is **not** authorization to implement all future systems.

## Scope Priority

1. Preserve the procedural-civilization thesis.
2. Complete Version 0.1.
3. Preserve important architectural escape hatches.
4. Avoid speculative engineering.

## Challenge Weak Technical Decisions

Do not blindly agree with implementation choices.

If an approach:

- adds unnecessary complexity
- damages performance
- conflicts with Fabric/Minecraft architecture
- undermines deterministic authority
- misuses AI
- causes excessive coupling
- damages local-inference feasibility
- makes server authority unnecessarily difficult
- causes expensive future rework

explain the problem and propose a better approach.

The user owns product direction.

The coding agent critically evaluates technical implementation.

## AI-System Discipline

For each AI-backed feature identify:

- authoritative facts
- supplied context
- exact model decision
- permitted output
- validation
- deterministic executor

Never confuse generated prose with simulation truth.

## Hostile-System Discipline

Distinguish:

**spawn/threat governance**

from:

**actor cognition**

Version 0.1 implements the first.

When implementing spawn policy identify:

- entity type
- spawn reason
- semantic region
- applicable threat policy
- whether intentional encounter state exists
- persistence behavior

## Multiplayer Architectural Discipline

Version 0.1 does not implement dedicated multiplayer.

However:

- authoritative RPG state should live server-side
- client UI should use explicit protocol boundaries
- AI backend ownership should not be client-hardwired
- request/world identity should be explicit
- AI backend contracts should be plain data
- validation occurs at authoritative boundaries

Do not build speculative multiplayer infrastructure.

Preserve the boundary.

## Performance Is Architectural

Consider:

- VRAM
- RAM
- context
- KV cache
- prompt cost
- inference frequency
- output length
- GPU/CPU contention
- async execution
- queueing
- concurrency
- prefix reuse
- network latency
- server concurrency
- cloud cost

## Keep Minecraft Threads Safe

Inference, networking, external processes, and expensive persistence must not block critical Minecraft threads unnecessarily.

State-changing asynchronous results return through safe authoritative-thread boundaries.

## Favor Inspectability

AI diagnostics should make it possible to identify:

- simulation issue
- context issue
- model issue
- transport issue
- parsing issue
- validation issue
- persistence issue
- integration issue

Useful information includes:

- request ID
- world ID
- trigger
- NPC ID
- player UUID where relevant
- backend
- latency
- token counts
- structured result
- validation result
- mutation

Spawn diagnostics should make it possible to determine:

- current semantic region
- active policy
- why a spawn was allowed/blocked
- spawn reason
- entity type
- which POI/settlement supplied the rule

A development/debug command that reports the current semantic region and effective spawn policy is strongly encouraged.

## Prefer Data-Driven Content

Separate reusable systems from authored content where practical.

Do not build an elaborate content framework before required.

## Testing Strategy

Favor automated testing for deterministic logic including:

- geographic calculations
- quest transitions
- validation
- AI result parsing
- memory selection
- persistence transformations
- orchestration
- semantic region membership
- spawn eligibility
- prohibited human-like entity rules
- POI threat policy
- guaranteed encounter state
- world/request identity

Do not over-test trivial Minecraft wiring.

## Backend Neutrality

Local inference first.

Future cloud support should primarily require another backend/config/UI.

Future server inference should primarily change deployment and scheduling.

It should not require rewriting NPC cognition.

## Communication Style

When handing off implementation work, prefer:

**What we're doing**

**Why**

**Change**

**Run**

**Expected result**

**If it fails**

If implementation discoveries conflict materially with this charter, explicitly flag them.

Do not silently reinterpret project direction.

---

---

# 61. Required First Response From a New Coding Agent

When a new coding agent receives this charter:

**Do not modify files.**

**Do not generate the whole application.**

**Do not jump into later phases.**

Instead:

1. Confirm understanding of the core technical thesis.

2. State what Version 0.1 must prove.

3. Identify assumptions requiring verification.

4. Recommend the exact Minecraft/Fabric/JDK baseline using current documentation.

5. Propose the smallest sensible initial project/package structure.

6. Identify the first implementation milestone.

7. Guide the user through that milestone in manual handoff mode.

The first milestone should establish a clean, runnable mod foundation and nothing more unless additional work is genuinely necessary to validate a foundational assumption.

Do not progress to the next major phase until the current foundation has been run successfully by the user.
