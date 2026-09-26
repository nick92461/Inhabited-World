# Development and Coding-Agent Rules

**Status:** Canonical Tier 4 development-governance specification  
**Authority:** Subordinate to project-design canon for game-design meaning; authoritative for coding-agent workflow  
**Scope:** Development philosophy, manual handoff rules, Git restrictions, implementation discipline, diagnostics, testing, and required behavior for new coding agents.

The numbered sections below retain their identities from the pre-split canonical charter for lossless migration and auditability.

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
