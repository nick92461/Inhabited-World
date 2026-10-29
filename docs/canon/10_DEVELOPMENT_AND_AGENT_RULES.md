# Development and Coding-Agent Rules

**Status:** Canonical Tier 4 development-governance specification  
**Authority:** Subordinate to project-design canon for game-design meaning; authoritative for coding-agent workflow  
**Scope:** Development philosophy, agent operating mode, Git workflow, implementation discipline, diagnostics, testing, and required behavior for new coding agents.

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

Do not require ordinary supported Windows local users to manually install or configure an inference runtime/model when the managed provisioning path is the intended consumer experience.

Do not download and execute arbitrary native binaries or model artifacts without project-approved source metadata and integrity verification.

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

Coding agents operate in **autonomous build mode**. Claude coding sessions write, build, test, and commit the mod's code and files directly. The user does not type code.

The work is organized as:

- **The main session** is the user's point of contact. It maintains the canon (under `00` §4–§7) and the implementation plan, picks the next step from the plan, briefs build sessions, reviews their work against canon, merges it, and pushes it.
- **Build sessions** each implement one briefed step on their own Git branch in a separate worktree, never directly on `main`.

Normal workflow:

1. The main session reads canon and the plan, and briefs a build session for one step.
2. The build session inspects the current code and implements the step on its branch.
3. The build session verifies its work (§60 Testing Strategy) and commits it on its branch.
4. The main session reviews the diff against canon and the plan, merges it into `main`, and pushes `main` to GitHub.
5. The user tests the merged change in-game using the test launch and the change's test checklist (§60 Testing Workflow).
6. The user reports results and directs what comes next. Fixes go back through the same flow.

Git:

- Claude may commit, merge, and push to the project's GitHub repository.
- `main` must build and pass its automated tests at every push.
- Never force-push, rewrite published history, or delete remote branches without the user's explicit direction.
- Canon changes are committed separately from code (`00` §7).

Limits that remain:

- Canon changes only with the user's explicit approval (`00` §4–§7). Build sessions never edit canon. They report conflicts and canon questions to the main session, which raises them with the user as Canon Proposals.
- Authority to build covers this repository. Changes outside it (the user's system configuration, the local inference setup, other projects) and destructive or irreversible actions need the user's explicit approval for that specific action.
- Design decisions that canon leaves open and that materially change the player experience are surfaced to the user rather than silently decided.

Responsibility split:

**Claude:** inspect, reason, design, build, test, commit, review, merge, push.

**User:** direct, decide, approve canon, test in-game, evaluate.

History: from project start until 2026-10-29 this section required manual handoff mode (the agent provided exact code, and the user typed, ran, and published it), and it absolutely prohibited the agent from running `git push`. On 2026-10-29 the user replaced that rule with autonomous build mode. The earlier wording is preserved in Git history.

---

# 60. Instructions for Coding Agents

The canonical corpus under `docs/canon/` (indexed by `00_DOCUMENT_AUTHORITY_AND_INDEX.md`, with `01_PROJECT_CHARTER.md` as its constitution) is the canonical design charter.

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

## Managed Local-AI Provisioning Discipline

When implementing the Windows consumer local-AI path:

- keep runtime/model provisioning separate from game/domain logic
- prefer user-writable application/game storage over machine-wide installation
- avoid administrator elevation unless a real dependency requires it
- do not install a permanent Windows service merely to run local inference
- launch the inference runtime as an application-owned process
- bind the managed runtime to loopback only; never expose it to the network by default
- protect the managed runtime with a per-launch unguessable access token or equivalently strong local authorization mechanism
- pass that token to the runtime through a channel less exposed than command-line arguments (for example an environment variable), and never write it or the launch command containing it to logs, crash reports, or diagnostics (`04` §2.22.5.1)
- prefer a dynamic/collision-safe local port and communicate the resolved endpoint directly to the managed backend
- keep browser/cross-origin access disabled or otherwise constrained so arbitrary web content cannot freely call the managed runtime
- treat deliberately exposed LAN/remote inference servers as an advanced manual configuration path, not normal managed behavior
- perform explicit readiness/health checking
- shut the runtime down when it is no longer needed
- ensure abnormal Minecraft termination does not intentionally leave the managed inference process running indefinitely; use Windows process-lifetime mechanisms where practical so the child runtime dies with its owning game/setup process
- use curated project-approved model/runtime packages
- support both automatic and guided provisioning paths ending in the same verified installation (`04` §2.22.6.1)
- prefer libraries Minecraft already ships (for example, hardware information and Windows process management) over new dependencies
- verify downloaded artifacts before use
- fail safely on hash/signature/integrity mismatch
- keep exact model/runtime details available for diagnostics even if the ordinary UI hides technical complexity
- allow manual override for advanced users or failed hardware detection
- never silently switch a failed local setup to paid cloud inference

The normal user-facing goal is low-friction setup, not concealment of unsafe behavior.

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

### Testing Workflow

Before a change is merged, its build session verifies it without the user:

- the project compiles and builds
- automated unit tests cover its deterministic logic (the list above)
- in-world behavior that code can check (for example, that a suppressed feature or structure is absent, that a prohibited spawn is refused, or that a portal does not light) is covered by automated headless game tests where practical

The user then sees what was built in the real game:

- A **one-click test launch** builds the current `main` and starts the development client directly in a fresh test world: the RPG world type, a fixed seed, creative mode, and cheats enabled, with the pinned development identity (`08` §51).
- Each merged change comes with a short **test checklist**: what to look at, commands to run, the expected result, and a Default-world comparison where useful.

Test conveniences (automatic test worlds, debug commands, development launch options) exist only in development runs and never alter the shipped consumer experience.

Performance, hardware and model-selection experiments are recorded under `docs/notes/` with the named configuration, raw data, method and interpretation, and indexed in `04` §2.14.1. Conclusions that change design go through the canon change process (`00` §4–§7); the record preserves the evidence they rest on.

## Backend Neutrality

Local inference first.

Future cloud support should primarily require another backend/config/UI.

Future server inference should primarily change deployment and scheduling.

It should not require rewriting NPC cognition.

## Communication Style

When reporting finished implementation work to the user, prefer:

**What was built**

**Why**

**How to test it** (the test launch and what to look at)

**Expected result**

**Known issues**

If implementation discoveries conflict materially with this charter, explicitly flag them.

Do not silently reinterpret project direction.

---

# 61. Required First Response From a New Coding Agent

When a new coding agent receives the canonical corpus:

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

7. Carry out that milestone under the operating mode in §57.1.

The first milestone should establish a clean, runnable mod foundation and nothing more unless additional work is genuinely necessary to validate a foundational assumption.

Do not progress to the next major phase until the current foundation has been run successfully by the user.
