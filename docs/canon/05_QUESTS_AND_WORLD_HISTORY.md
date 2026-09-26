# Quests and World History Specification

**Status:** Canonical Tier 2 subsystem specification  
**Authority:** Subordinate to `01_PROJECT_CHARTER.md`  
**Scope:** Quest definitions/instances, authoritative world history, and long-term dynamic quest creation.

The numbered sections below retain their identities from the pre-split canonical charter for lossless migration and auditability.

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
