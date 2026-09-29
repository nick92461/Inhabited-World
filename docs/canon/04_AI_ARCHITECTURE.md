# AI Architecture Specification

**Status:** Canonical Tier 2 subsystem specification  
**Authority:** Subordinate to `01_PROJECT_CHARTER.md`  
**Scope:** Single-player AI backend selection, BYOK, credential handling, backend contracts, shared model architecture, orchestration, request lifecycle, context, dialogue, inference scheduling, and AI performance.

The numbered sections below retain their identities from the pre-split canonical charter for lossless migration and auditability.

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

## 2.8 Automatic Backend Selection

The eventual default single-player backend mode should be:

> **Automatic — Recommended**

At startup:

1. Check for a compatible local inference backend: the managed local runtime (§2.22.1) or a local/private backend the user explicitly configured.
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

## 2.12 Local Backend Detection Requires Capability Validation

Finding something listening on localhost is not enough.

The game should not blindly probe arbitrary local ports and send prompts to whatever answers. A local backend is either the managed runtime the game itself launched (§2.22.5) or an endpoint the user or operator explicitly configured.

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

## 2.22.1 Managed Local Runtime Provisioning

Sections 2.22.1–2.22.8 detail the charter principle `01` §2.22 (Managed Local AI Provisioning Is the Intended Default Consumer Experience).

For supported Windows single-player installations, the intended default local-AI path is a **managed local backend** rather than requiring users to install and configure an inference runtime manually.

The mod may use a small runtime-management/bootstrap layer responsible for:

- GPU and VRAM detection
- selecting the supported local model tier
- determining whether required files are already present
- downloading approved runtime/model files when missing
- verifying downloaded files
- starting the local inference runtime
- health checking the runtime
- restarting it when appropriate
- shutting it down when the Minecraft session no longer needs it
- reporting setup failures in understandable language

This bootstrap/runtime-management layer is infrastructure. It does not own NPC cognition, game context, validation, or authoritative state. Those responsibilities remain in the normal AI orchestration/game architecture.

Conceptually:

```text
Minecraft / Integrated Server
        ↓
ManagedLocalBackend
        ↓
Local AI Runtime Manager
        ↓
approved inference runtime
        ↓
curated local model
```

Advanced users may still be allowed to point the project at a compatible manually managed local backend where supported.

---

## 2.22.2 Windows Is the Required Consumer Platform for Managed Local Provisioning

The managed consumer local-AI path is only required to support Windows.

macOS and Linux support are not required by the project unless explicitly added later.

This allows the project to optimize the ordinary installation/runtime lifecycle for one operating-system environment rather than multiplying deployment complexity before it provides gameplay value.

The Java/Fabric gameplay architecture should remain generally clean and portable where practical, but the project is not obligated to ship or support equivalent native AI bootstrap behavior on non-Windows platforms.

---

## 2.22.3 Curated Hardware and Model Tiers

The normal local user should not select arbitrary models from a large catalog.

The project should hand-pick, test, and publish supported model packages for a small number of hardware tiers.

The current intended tier structure is conceptually:

```text
~8 GB VRAM
    → supported local tier A

~12 GB VRAM
    → supported local tier B

~16+ GB VRAM
    → supported local tier C
```

These thresholds are design targets, not permanent promises about exact models or memory use. The actual support matrix should be based on measured behavior for each release.

Model selection should consider at minimum:

- reliable structured output
- context capacity
- dialogue/reasoning quality
- VRAM/RAM consumption
- prompt-processing speed
- generation speed
- stability with the project's orchestration contract
- license/distribution suitability

The same mod build should preferably select among these supported local packages rather than requiring a separate mod binary for every VRAM tier.

Tiers are added as they are benchmarked. Version 0.1 is required to ship only tier A (~8 GB VRAM) plus the manual override (§2.22.4); tiers B and C follow once measured (see `09`).

---

## 2.22.4 Automatic Hardware Detection and Manual Override

The project should automatically inspect supported Windows hardware where practical and recommend/select the appropriate curated local-AI tier.

The ordinary flow should not ask the player to identify their graphics card manually if the software can determine it itself.

Detection should focus on the information actually needed for supported-tier selection, such as:

- active/discrete GPU identity
- dedicated VRAM
- other capability information required by the chosen inference runtime

A manual override should remain available because:

- multi-GPU systems may be ambiguous
- integrated/discrete configurations can be unusual
- users may deliberately prefer a smaller model
- detection can fail
- future hardware may not match the built-in table cleanly

The mod page/documentation should still publish explicit hardware requirements so users can choose the local or cloud path before installation.

---

## 2.22.5 Local Runtime Lifecycle

The managed local inference runtime should ordinarily be an application-owned process rather than a permanently installed system service.

Preferred lifecycle:

```text
Minecraft starts / local AI becomes needed
(an RPG world begins opening; model loading overlaps world loading)
        ↓
verify required files
        ↓
launch local inference process
        ↓
health check
        ↓
serve AI requests
        ↓
Minecraft session/world closes
(no RPG world remains open after a short grace period, or Minecraft exits)
        ↓
graceful runtime shutdown
        ↓
forced termination only if graceful shutdown fails
```

The relevant session is defined in `01` §2.22. The runtime never outlives the Minecraft process, including when Minecraft exits abnormally.

Where practical, the runtime/model should live inside a user-writable game/project-managed directory rather than requiring installation into protected system locations.

The project should avoid requiring:

- a permanent Windows service
- machine-wide runtime installation
- administrator elevation
- unrelated background processes that remain running after Minecraft closes

If a future technical dependency genuinely requires elevation, that should be treated as an explicit product/security decision rather than assumed by default.

### 2.22.5.1 Managed Runtime Network Isolation

The ordinary managed local-AI runtime is an internal application component. It should not become a general-purpose network service merely because the underlying inference runtime exposes an HTTP API.

For the managed consumer path:

- bind the inference service to the loopback interface only
- never expose the managed runtime to the LAN, Wi-Fi network, Internet, or other external interfaces by default
- use a per-launch unguessable access credential/token or an equivalently strong local authorization mechanism so unrelated local software cannot freely submit inference requests
- choose a dynamically assigned or otherwise collision-safe local port rather than relying on one globally assumed fixed port where practical
- pass the current endpoint/port and access credential directly to the managed backend rather than persisting the launch credential as ordinary long-lived configuration
- invalidate the per-launch credential when the runtime terminates
- configure cross-origin/browser access conservatively so web content cannot treat the managed runtime as an unauthenticated local service
- fail closed if the runtime cannot be launched with the required network-isolation guarantees

These rules apply to the **managed automatic consumer runtime**.

They do not prohibit an advanced user or server operator from deliberately configuring a manually managed inference server on a LAN or another host. Such exposure is an explicit advanced deployment decision and must not be silently enabled by the normal consumer path.

The security objective is:

> **Only the Inhabited World managed backend should be able to use the automatically launched local runtime by default.**

Threat model: the per-launch credential and loopback binding protect against web content, other machines on the network, and other operating-system user accounts. They cannot protect against malicious software already running as the same user, which could equally inspect Minecraft's own memory; the project must not claim otherwise.

The credential should reach the runtime through a channel less exposed than command-line arguments, which ordinary tools display to any program the user runs (for example, an environment variable of the child process). It must never be written to logs, crash reports, or diagnostic output, and the runtime launch command must not be logged with the credential in it.

---

## 2.22.6 Managed Download Security and Integrity

Automatic provisioning must not become arbitrary remote-code execution.

Every runtime/model artifact automatically downloaded by the managed local path should come from a project-approved source and should be verifiable before execution/use.

The implementation should use appropriate protections such as:

- HTTPS transport
- pinned/approved download metadata
- cryptographic hashes
- code signatures where practical
- explicit supported versions
- atomic or recoverable installation/update behavior

The project should prefer official upstream runtime releases or reproducibly built/signed project distributions.

Model redistribution or automatic download must respect the selected model's license and required notices.

A failed integrity check must prevent the artifact from being executed or treated as a valid model package.

### 2.22.6.1 Distribution-Platform Compatibility

Mod distribution platforms have their own rules about what a mod may download or execute. Setup must not depend on a single delivery mechanism that one platform might prohibit.

The provisioning flow should therefore support:

- **automatic provisioning:** after the player approves, the game downloads the approved files itself (the preferred, simplest path)
- **guided provisioning:** the game opens the official approved download page, the player downloads the file, and the game finds, verifies, and installs it with the same integrity checks

Both paths end in the same verified installation. Which path a given distribution channel uses is a release decision, confirmed against that platform's current rules before publishing there. Wherever a platform permits it, the automatic path is used.

Each publication must disclose that the mod requires an additional local-AI download, its approximate size, what it contains, and where it comes from.

---

## 2.22.7 Consumer Setup Disclosure Should Be Minimal but Accurate

The ordinary player should not be burdened with AI-infrastructure terminology.

Normal setup may communicate the requirement as an additional local-AI component/download rather than explaining model architecture, quantization, inference servers, or implementation details.

The user-facing prompt should communicate the facts that materially affect consent, such as:

- an additional download is required
- approximate download size
- local AI processing is being enabled
- the files run locally while the game uses local AI

More technical information may be available behind an Advanced Details surface.

The project should not deliberately misrepresent what is being downloaded or executed. Exact language must remain compatible with applicable distribution-platform requirements, software/model licenses, security requirements, and law.

---

## 2.22.8 Cloud/BYOK Is the Alternative Path, Not the Default Setup Burden

For a supported Windows machine that meets local requirements, the user should not need an API account merely to obtain the normal experience.

The consumer choice should conceptually be:

```text
SUPPORTED LOCAL HARDWARE
    → automatic managed local package
    → no recurring inference fee

UNSUPPORTED / WEAKER HARDWARE OR USER PREFERENCE
    → cloud/BYOK setup
    → user supplies provider credentials
```

Users choosing the cloud path may reasonably accept additional configuration because that path substitutes remote inference for hardware they do not provide locally.

The local path remains the preferred low-friction experience on machines that meet published requirements.

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

Where structured output is required, the request also carries a backend-neutral description of the permitted output: for example, the allowed decisions, required fields, and numeric ranges derived from current authoritative state. Each backend translates that description into whatever constrained-output mechanism it supports, such as a JSON Schema, a grammar, or a tool schema. The same description drives validation. Constrained decoding reduces invalid output, but it never replaces validation.

This makes the same backend contract usable for:

- local testing
- local inference
- cloud inference
- remote dedicated inference
- server networks

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

The preferred structure for action-bearing turns is **decision first, presentation second**:

1. the model produces only the constrained structured decision
2. Java validates it and commits the authoritative change
3. the reply prose is generated afterward to express the committed outcome

Prose is then never generated for an action that was not committed, which applies the outcome-first principle of `03` §40 to dialogue. Numbers, names, and directions in the prose are still checked deterministically where practical. The second step reuses the first step's prompt, so prefix caching keeps its cost low.

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

# 26. Prompt Construction and Context Eviction Are Separate Concerns

Prompt ordering should optimize stable-prefix reuse where practical.

Likely order:

1. shared instructions and output rules common to all NPCs
2. shared world knowledge common to the relevant population
3. NPC identity/personality and NPC-specific knowledge
4. relevant quest/state
5. relevant memories
6. recent dialogue
7. current player input

Material shared by many NPCs comes before NPC-specific material, so one cached prefix serves every NPC. Placing NPC identity first would invalidate the cache every time the player addresses a different NPC.

Shared world knowledge must respect the knowledge/truth distinction (`03` §34). Only information that the relevant NPCs could all plausibly know belongs in the shared prefix. Anything else is NPC-specific knowledge.

(Before 2026-09-28 this list began with NPC identity/personality, followed by stable world facts. It was reordered with user approval for cross-NPC prefix reuse.)

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
