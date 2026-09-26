# Long-Term Systems and Success Condition

**Status:** Canonical Tier 2 long-term subsystem specification  
**Authority:** Subordinate to `01_PROJECT_CHARTER.md`  
**Scope:** Game Director, deterministic macro simulation, endgames/major events, structured shops, and the project's long-term success condition.

The numbered sections below retain their identities from the pre-split canonical charter for lossless migration and auditability.

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
