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

# 62. NPC Voice Synthesis (Undecided Potential Feature)

**Status: UNDECIDED.** This section records an idea so it is not lost. It is not a requirement, not a commitment, and not a Version 0.1 goal (`09` §55). No coding agent may build it, or architect the project around it, unless the user approves it and this section is revised to say so.

## 62.1 The idea

NPCs could be given spoken voices by running a small text-to-speech model on the reply text the NPC has already produced and the authoritative game has already committed.

## 62.2 Constraints any design must obey

These follow from existing canon and from measured evidence (`04` §2.14.1):

- **Presentation, not authority.** Speech is presentation of an outcome that is already decided (`03` §40). Audio never decides, changes or replaces what an NPC says.
- **Text is always available.** Subtitles or an equivalent text presentation always accompany speech. Audio is never the only way to receive information.
- **Optional.** The player can disable voices entirely.
- **Client-side synthesis.** The authoritative server produces and sends text only (`07` §2.16). Each client synthesizes locally, which keeps server authority intact and sends no audio over the network. Multiplayer clients voice the same canonical line.
- **Must not compete with the inference runtime or the game for the GPU.** The tier-A VRAM headroom is small, and model generation during active play already tripled frame-time spikes (`04` §2.14.1). A voice feature is expected to run on the CPU with a bounded thread count. It is a player-facing task, but it must not degrade frame pacing (`04` §36–§37).
- **Verified text first.** Speech starts only after the sentence has passed the deterministic checks of `04` §28 and §24. The system never speaks an unverified number or an uncommitted action.
- **Licensing and distribution.** Any voice model or phonemizer redistributed or downloaded automatically is subject to the managed-provisioning rules (`04` §2.22.6) and the project's licensing. A model's license alone is not enough: every component it needs must be checked.

## 62.3 Leading candidate (not selected)

If this is ever pursued, the leading candidate as of 2026-10-02 is **Kokoro**: about 82 million parameters, Apache 2.0, close to real time on a CPU, with a set of built-in voices that can reportedly be blended.

The reasons are quality for its size, a clean model license, and distinct voices without voice cloning. Each NPC's voice could be derived deterministically from the world seed.

Alternatives noted: **Piper** (MIT, very small, more robotic) and **KittenTTS** (very small, fewer voices). Larger cloning-oriented models (for example F5-TTS, Orpheus, Voxtral) are GPU-class and unsuitable for tier A.

This landscape moves quickly. The survey was a web search, not a benchmark. It must be re-verified, and the chosen model measured under `04` §2.22.3's whole-system rules, before any decision.

## 62.4 Open questions that must be answered before deciding

1. **Text-to-phoneme conversion in Java.** The leading candidate's reference implementations use a Python phonemizer, with the **espeak-ng** engine as the fallback. A Java-friendly approach (a port, a small helper process, or a dictionary-based approach) has not been identified. This is the main engineering unknown.
2. **Phonemizer licensing.** espeak-ng is GPL. Whether and how it could accompany an All-Rights-Reserved mod, for example as a separately downloaded program, must be checked before any commitment.
3. **Expressiveness.** The candidate has no voice cloning and little emotion control. Whether emotion tags from the decision stage (`04` §24) can steer speed and pitch enough to be worthwhile is untested.
4. **Voice variety.** How many distinct, consistent NPC voices can be produced from the built-in set, and how they are assigned deterministically from the NPC record and world seed.
5. **Latency.** How much of the multi-second reply time sentence-by-sentence synthesis can hide, given that speech waits for the first verified sentence.
6. **Measured cost.** CPU load, memory, and frame-pacing impact on the reference 8 GB tier machine, with the game running.
7. **Packaging.** Download size and whether voice files fit the managed-provisioning consent flow (`04` §2.22.6.1).
8. **Presentation details.** Positional audio at the NPC entity, interruption when the player walks away, and per-NPC and global volume and mute controls.

## 62.5 Optional related ideas (equally undecided)

- Pre-generated audio for common stock lines.
- Speech-to-text for the player's microphone as the counterpart to NPC voices.

## 62.6 Decision rule

Nothing in this section is built, scheduled or assumed until the user approves it. If approved, a revised version of this section replaces the "Undecided" status, states the chosen model and design, and adds any new requirements to `09`.

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
