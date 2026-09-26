# Multiplayer and Server Architecture Specification

**Status:** Canonical Tier 2 subsystem specification  
**Authority:** Subordinate to `01_PROJECT_CHARTER.md`  
**Scope:** Dedicated-server inference ownership, multiplayer authority, multi-instance isolation, inference scheduling, deployment models, server security, and shared-world social consequences.

The numbered sections below retain their identities from the pre-split canonical charter for lossless migration and auditability.

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
