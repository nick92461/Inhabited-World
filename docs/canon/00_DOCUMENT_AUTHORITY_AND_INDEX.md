# Canonical Document Authority and Index

## Status

This file defines the authority, hierarchy, change-control protocol, and canonical file layout for the Minecraft: Inhabited project.

The **user is the ultimate project authority**.

**Claude maintains the canonical document corpus and the implementation plans on the user's behalf.** Claude may analyze, challenge, implement, and propose changes, but canon changes only when the user explicitly approves them. Claude does not independently redefine canon.

The purpose of this arrangement is to ensure that the user and Claude always converge on one exact shared source of truth rather than allowing parallel design documents to drift.

History: from 2026-09-25 to 2026-09-28, ChatGPT was the user-designated generator of the canonical corpus, and Claude synchronized ChatGPT-generated packages onto disk. On 2026-09-28 the user retired that arrangement and assigned canon maintenance to Claude under the approval rule above.

The canonical corpus is stored at the **workspace level**, not inside the mod codebase. All relative paths in this document are rooted at the `Minecraft Inhabited/` workspace directory. The eventual mod codebase is a child of that workspace and remains subordinate to the workspace-level canon.

Conceptually:

```text
Minecraft Inhabited/
├── docs/
│   ├── canon/
│   ├── plans/
│   └── notes/
└── <mod codebase>/
```

This allows documentation, design history, implementation plans, and potentially additional project repositories/tools to share one stable canonical source.

---

# 1. Canon Is a Document Corpus, Not One Giant File

The project's source of truth is the complete set of files under:

```text
docs/canon/
```

No single subsystem document should be interpreted in isolation from the rest of the canonical corpus.

The root Project Charter defines the project's constitutional principles. Detailed subsystem documents define how particular areas of the game work. Version/milestone specifications define what a particular release must implement. Development rules govern how coding agents work with the project.

The move from one monolithic charter to this hierarchy is organizational only. It must not silently remove previously approved content.

---

# 2. Authority Hierarchy

Use the following authority order:

## Tier 0 — Canon Governance

```text
docs/canon/00_DOCUMENT_AUTHORITY_AND_INDEX.md
```

Defines document authority, conflict resolution, change control, and the canonical file map.

## Tier 1 — Project Constitution

```text
docs/canon/01_PROJECT_CHARTER.md
```

Defines the core thesis, project-wide design principles, and rules that all subsystems must obey.

## Tier 2 — Canonical Subsystem Specifications

Current subsystem specifications:

```text
docs/canon/02_WORLD_WORLDGEN_AND_POIS.md
docs/canon/03_NPC_SOCIAL_AND_MEMORY.md
docs/canon/04_AI_ARCHITECTURE.md
docs/canon/05_QUESTS_AND_WORLD_HISTORY.md
docs/canon/06_HOSTILES_SPAWNING_AND_COMBAT.md
docs/canon/07_MULTIPLAYER_AND_SERVER_ARCHITECTURE.md
docs/canon/08_TECHNICAL_ARCHITECTURE_AND_PERSISTENCE.md
docs/canon/11_LONG_TERM_SYSTEMS.md
```

Future detailed RPG mechanics should normally receive their own Tier 2 canonical specifications rather than being expanded indefinitely inside the root charter. Likely future files include:

```text
docs/canon/12_PLAYER_PROGRESSION.md
docs/canon/13_ITEMS_EQUIPMENT_AND_LOOT.md
docs/canon/14_MAGIC_SYSTEM.md
docs/canon/15_ECONOMY_AND_TRADING.md
docs/canon/16_FACTIONS_AND_POLITICS.md
```

These files should be created only when their systems are actually designed.

## Tier 3 — Version and Milestone Specifications

```text
docs/canon/09_V0_1_VERTICAL_SLICE.md
```

Version/milestone specifications define exact delivery scope and Definition of Done. They remain subordinate to the Project Charter and relevant subsystem specifications.

## Tier 4 — Development Governance

```text
docs/canon/10_DEVELOPMENT_AND_AGENT_RULES.md
```

Defines how coding agents operate and how implementation work is handed off.

## Tier 5 — Non-Canonical Implementation Material

Implementation plans belong under:

```text
docs/plans/
```

Working notes, research, scratch decisions, and proposals belong under:

```text
docs/notes/
```

These materials may inform future canon but do not override canonical files.

Code is an implementation of canon. Existing code does not automatically redefine canon merely because it behaves differently.

---

# 3. Conflict Rules

When documents appear to conflict:

1. This authority/index file governs document authority and change control only.
2. `01_PROJECT_CHARTER.md` outranks every subsystem, milestone, plan, note, and implementation decision on project-wide principles.
3. A specifically scoped Tier 2 subsystem specification governs details within its domain so long as it does not contradict the Project Charter.
4. `09_V0_1_VERTICAL_SLICE.md` governs what Version 0.1 must and must not implement, but it cannot override higher-level architectural rules.
5. `10_DEVELOPMENT_AND_AGENT_RULES.md` governs coding-agent workflow, not game-design content.
6. Implementation plans and code never silently override canon.
7. If two same-tier canonical documents genuinely conflict and precedence is not obvious, do not invent a resolution. Surface the conflict to the user for a canonical decision.

A more specific document may clarify a broader rule. It may not contradict it.

---

# 4. Canon Update Authority

The user is the final authority on project direction.

Claude maintains the canonical files and applies canon changes, but only changes the user has explicitly approved.

The expected workflow is:

```text
User + Claude discuss/design
        ↓
Claude writes proposals (Section 5)
        ↓
User considers all proposals and ideas
        ↓
User approves, rejects, or amends each proposal
        ↓
Claude applies exactly the approved changes losslessly (Section 7)
        ↓
Claude records the change in the implementation plan's canon change log
        ↓
User commits the canon change
```

Claude is encouraged to identify:

- missing design implications
- contradictions
- technical risks
- architectural improvements
- unclear requirements

However, those observations are proposals until the user approves them.

Claude must not silently edit canonical design meaning based on its own preference, and must not make opportunistic canon edits (including "obvious" wording cleanups) outside an approved change.

---

# 5. Canon Proposal Flow

When Claude discovers a design issue or proposes a change that may belong in canon, Claude should provide the user with a clearly labeled **Canon Proposal**.

Related proposals should be batched so the user can consider them together.

That proposal should state:

- what existing canonical rule is affected
- the exact proposed addition/change/removal
- why the change is recommended
- which canonical file(s) Claude believes are affected
- whether any existing wording would need to be removed or superseded
- implementation consequences
- any unresolved alternatives

The user decides whether and how it enters canon.

If approved, Claude applies the approved change under Section 7.

---

# 6. Lossless Canon Rule

Canonical revisions are **append-preserve by default**.

Approved content must not disappear because a document is being:

- reorganized
- split into multiple files
- cleaned up
- reformatted
- deduplicated
- rewritten for brevity
- moved to another subsystem document

Unless the user explicitly approves removal or replacement, preserve:

- requirements
- constraints
- examples
- rationale
- non-goals
- long-term intent
- edge cases
- coding-agent rules
- architectural invariants

If wording is moved between canonical files, movement is not deletion.

If wording is condensed, merged, or semantically rewritten, that is a canonical change and requires deliberate approval rather than being treated as formatting.

When performing a large reorganization, use a migration manifest so every prior canonical section has an explicit destination.

---

# 7. Applying Approved Canon Changes

When the user approves a canon change, Claude should:

1. Re-read the affected canonical files from disk before editing.
2. Apply exactly the approved change, and nothing else.
3. Preserve all other wording under the Lossless Canon Rule (Section 6).
4. Keep retained section numbering stable; new sections receive new, unused numbers.
5. Create or remove canonical files only when the approved change says so.
6. Record the change in the implementation plan's canon change log (what changed, where, and when it was approved).
7. Re-read the changed files and any files relevant to the current implementation milestone before continuing implementation work.

Git history is the canonical audit trail. Each approved canon change set should be committed on its own, separately from code changes, so it can be reviewed and reverted independently. Separate backup copies under `_archive/` are not required for ordinary changes.

Claude maintains implementation plans and notes separately, and they remain subordinate to canon.

---

# 8. Current Canonical File Index

```text
docs/
├── canon/
│   ├── 00_DOCUMENT_AUTHORITY_AND_INDEX.md
│   ├── 01_PROJECT_CHARTER.md
│   ├── 02_WORLD_WORLDGEN_AND_POIS.md
│   ├── 03_NPC_SOCIAL_AND_MEMORY.md
│   ├── 04_AI_ARCHITECTURE.md
│   ├── 05_QUESTS_AND_WORLD_HISTORY.md
│   ├── 06_HOSTILES_SPAWNING_AND_COMBAT.md
│   ├── 07_MULTIPLAYER_AND_SERVER_ARCHITECTURE.md
│   ├── 08_TECHNICAL_ARCHITECTURE_AND_PERSISTENCE.md
│   ├── 09_V0_1_VERTICAL_SLICE.md
│   ├── 10_DEVELOPMENT_AND_AGENT_RULES.md
│   ├── 11_LONG_TERM_SYSTEMS.md
│   ├── MIGRATION_MANIFEST.md
│   └── _archive/
│       ├── PRE_SPLIT_CANON_SNAPSHOT.md
│       └── MIGRATION_AUDIT.md
├── plans/
└── notes/
```

`_archive/` is historical/audit material and is not independently authoritative when it conflicts with the active canonical corpus.

---

# 9. Reading Canon Efficiently

A contributor does not always need every canonical file in active context.

Always understand:

```text
00_DOCUMENT_AUTHORITY_AND_INDEX.md
01_PROJECT_CHARTER.md
```

Then load the canonical subsystem documents relevant to the current task.

Examples:

World generation work:

```text
01_PROJECT_CHARTER.md
02_WORLD_WORLDGEN_AND_POIS.md
06_HOSTILES_SPAWNING_AND_COMBAT.md when spawn policy is involved
08_TECHNICAL_ARCHITECTURE_AND_PERSISTENCE.md
09_V0_1_VERTICAL_SLICE.md
10_DEVELOPMENT_AND_AGENT_RULES.md
```

AI work:

```text
01_PROJECT_CHARTER.md
03_NPC_SOCIAL_AND_MEMORY.md as relevant
04_AI_ARCHITECTURE.md
08_TECHNICAL_ARCHITECTURE_AND_PERSISTENCE.md
09_V0_1_VERTICAL_SLICE.md
10_DEVELOPMENT_AND_AGENT_RULES.md
```

The purpose of splitting the corpus is to improve ownership and context efficiency without weakening the source of truth.

---

# 10. Principle for Future Canon Growth

The Project Charter should state **what must remain true across the project**.

Subsystem specifications should state **how a particular game system works**.

Version specifications should state **what a particular release must prove or deliver**.

Implementation plans should state **how the team intends to build the canonical design right now**.

As detailed RPG design expands into progression, skills, perks, talent trees, unique items, magic, economy, factions, and other systems, create focused canonical subsystem specifications rather than allowing the root charter to become an unbounded mechanics encyclopedia.

The canonical corpus remains one source of truth even when it spans many files.
