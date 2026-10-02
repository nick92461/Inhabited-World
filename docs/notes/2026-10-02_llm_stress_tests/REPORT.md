# Local LLM Stress Tests — 2026-10-02 (Runs 1 and 2)

**Status:** Tier 5 research record (non-canonical; see `docs/canon/00_DOCUMENT_AUTHORITY_AND_INDEX.md` §2). Canon rules derived from it cite it from `docs/canon/04_AI_ARCHITECTURE.md` §2.14.1.

**Author:** Claude (analysis), with the user (test operator). Written 2026-10-02, the same day as the runs.

**Purpose:** decide, before any AI code exists, whether the planned 8 GB local tier (tier A) can carry a finished-mod inference workload alongside Minecraft, whether a larger model is viable, and what the workload does to gameplay. This avoids being forced into a bigger model later by something we built.

All numbers below come from the raw files in `data/`. "I" is Claude. Statements of interpretation are marked as such, with their confidence.

---

## 1. Configuration (both runs unless noted)

| Item | Value |
|---|---|
| CPU / RAM | AMD Ryzen 5 5600 (6c/12t) / 16 GB |
| GPU | AMD Radeon RX 7600, 8 GB (8,192 MB) |
| OS | Windows 10 Pro 19045 |
| Monitor | 120 Hz (derived from the run 2 vsync frame time, median 8.33 ms) |
| Inference runtime | llama.cpp build b11221, Vulkan, `llama-server` |
| Model | `Qwen3.5-4B-Q4_K_M.gguf` (2.74 GB), thinking disabled per request (`chat_template_kwargs.enable_thinking=false`) |
| Server flags | `-ngl 99 -c 8192 -np 2 --cache-ram 1024 --port 8080 --jinja` |
| Server reports | `n_slots = 2, n_ctx_slot = 4096, kv_unified = false`, 6 threads; warns "no API key is set and CORS allows all origins" |
| Game | Minecraft 26.3 **dev client** (`runClient`), Fabric Loader 0.19.5, mod `inhabited_world` 0.1.0-dev, Fancy graphics, render distance 16 |
| Telemetry | `tools/stress.ps1` (per-call CSV + every reply), `tools/gpulog.ps1` (per-process VRAM, RAM, CPU), AMD Software 26.8.1 performance logging (`FPS.Latency.*.CSV`, `Hardware.*.CSV`, per-frame `*.FrameTime`) |

| | Run 1 | Run 2 |
|---|---|---|
| Wall clock (stress loop) | 13:38:31 – 13:48:29 | 14:25:06 – 14:35:09 |
| Desktop load | **Heavy, deliberately careless:** IntelliJ open, Discord, several browser windows, a video playing, Steam. Desktop alone ≈ 2.1 GB VRAM before Minecraft or the model (user-reported) | **Minimal:** Discord only (Steam's web helper still resident, 418 MB VRAM). IntelliJ closed; Minecraft launched from Git Bash via `gradlew runClient` |
| Frame limit | Unlimited, no vsync | **Vsync on (120 Hz)**, slider unlimited |
| Player activity | Natural gameplay for the whole run (whether the user flew during the burst phase was not recorded) | Built a small structure. **Stepped away for ~3 minutes mid-test** (inferred from the data as ~150–330 s; see §5.4). **No flying.** |
| Stress script | First version (had a CSV formatting bug and a weaker reply checker; see §2.3) | Fixed version (as in `tools/`) |

---

## 2. Workload design (`tools/stress.ps1`)

### 2.1 Request mix (imitating a finished mod)
- 10 minutes: **steady** 0–210 s (player request every 8–18 s after the last reply; background every 30–50 s), **burst** 210–390 s (player every 2–5 s; background every 8–14 s), **steady** 390–600 s.
- Player-facing kinds: 30% **negotiation** with blacksmith Haldor, done **decision first** (call 1: constrained JSON decision with the legal options and amount range computed per request) then **presentation** (call 2: prose for the committed outcome; canon `04` §24). 30% **geography** questions, 70% about facts the NPC knows and 30% about facts it doesn't. 40% **chat**, including traps: a manipulation attempt, "are you an AI?", a question about a mayor who doesn't exist, and an off-world question.
- **Background** ("memory compression"): summarize 24 event lines from one NPC's point of view, overlapping player requests (2 server slots). This is the worst case.
- Prompts follow canon `04` §26 order: shared rules + common knowledge (shared prefix), then the NPC block (identity and the facts it knows), then quest state, 8–24 memories (from a pool of 42), the last 12 lines of a growing per-NPC history, and the player's line. Six NPCs rotate. Measured prompt size ≈ 1,100–1,450 tokens.

### 2.2 Automatic checks
- Decisions: legal option and amount in range (`illegal-decision`, `illegal-amount`, `schema-invalid`).
- Prose: invented, wrong or missing numbers (digits and, from run 2, number words), wrong or invented directions, admitting not knowing (`no-admission`), false denial of a place (`false-denial`), units, breaking character, manipulation compliance, unknown capitalized names, an invented mayor, replies over 70 words.
- These are heuristics. **Every reply of both runs was also read by hand** (`data/*/replies_*.txt`).

### 2.3 Script changes between runs
Run 1 exposed the following, all fixed before run 2:
- `prompt_ms` was written with a thousands separator, which shifted CSV columns. Run 1's CSV was re-parsed for the analysis.
- Direction matching was case-sensitive, so "North of the mine" was missed.
- Numbers written as words weren't recognized, so "ninety blocks" was counted as missing.
- The negotiation prompt was ambiguous: the 4B read it as the player *buying* the hammer. The quest state now says the NPC pays the reward.
- The logger gained per-process RAM working sets, `Pages Input/sec` and pagefile use.

---

## 3. Method of analysis (`analysis/`)

- **Model timeline:** each call's start is `t − latency`. Its prompt-processing phase lasts `prompt_ms` (reported by the server), and the remainder is token generation. The script's loop start is derived from the CSV's last write time minus the last call's `t`.
- **Frame timeline:** the AMD `.FrameTime` file lists every frame's duration (µs), with no timestamps.
  - Run 1: the durations summed to 908.0 s against a 907.4 s log span, so the file was anchored at the first FPS row.
  - Run 2: they summed to 625.7 s against 617.6 s. The offset was **calibrated by cross-correlating** per-second frame counts with the timestamped FPS rows: a sharp peak at **+6.0 s (r = 0.949; next best 0.844 at +7 s)**. Alignment uncertainty is about ±0.5 s.
- **Hitch:** a frame longer than 25 / 33.4 / 50 / 100 ms. At 120 Hz vsync, 33.4 ms is 4 missed refreshes.
- **Significance:** circular-shift permutation test. The whole call schedule is shifted against the frame timeline 1,500 times, preserving the clustering of both. p is the fraction of shifted schedules whose generating/idle hitch-rate ratio is at least the observed one.
- **Away window (run 2):** inferred from the 15 s timeline (§5.4), not timestamped by the user.

---

## 4. Run 1 results (heavy desktop, unlimited FPS)

### 4.1 Calls (77, 0 failures)
| Call | N | p50 ms | p95 ms | max ms | gen tok/s | avg prompt tok | cache hit |
|---|---|---|---|---|---|---|---|
| GEO prose | 13 | 3,842 | 8,083 | 8,083 | 21.0 | 1,122 | 39% |
| BACKGROUND summary | 23 | 6,983 | 11,685 | 12,876 | 16.8 | 1,157 | 45% |
| NEGOTIATE decision | 12 | 3,094 | 8,483 | 8,483 | 19.0 | 1,276 | 41% |
| NEGOTIATE prose | 12 | 2,940 | 3,929 | 3,929 | 21.6 | 1,277 | 51% |
| CHAT prose | 17 | 4,484 | 13,622 | 13,622 | 18.5 | 1,276 | 40% |

- **Player-facing latency by phase (p50 / p95 / max ms):**

  | Phase | p50 | p95 | max |
  |---|---|---|---|
  | steady1 | 2,986 | 8,483 | 8,483 |
  | burst | 4,274 | 7,414 | 13,622 |
  | steady2 | 3,724 | 6,383 | 6,383 |
- **Prompt processing:** 366 tok/s average, matching the 2026-09-28 spike (330–450).
- **Generation:** 17–22 tok/s, against 30–47 in the spike, which used ~90-token prompts with no game load.
- **Background overlap:** player calls overlapped by a background call averaged **5,481 ms at 15.7 tok/s**, against **3,293 ms at 23.6 tok/s** without overlap (n = 25 / 29).
- **Cache:** cold calls (<200 cached tokens, n = 4) averaged 6,636 ms; warm calls (≥600 cached, n = 17) 3,058 ms.

### 4.2 Memory
- **VRAM peak 7,108 MB of 8,192**, made up of:
  - llama-server 2,804 MB (2,647 at load, growing under real prompts)
  - Minecraft 2,118 MB
  - everything else 2,565 MB: dwm 669, firefox 662, steamwebhelper 391, csrss 164, RadeonSoftware 127
- **Phase averages:** steady1 6,580 MB, burst 6,965 MB, steady2 7,062 MB.
- **System RAM available:** fell to 279 MB (pre-test), 284 (steady1), 369 (burst) and 542 (steady2).
- **AMD hardware:**
  - GPU utilization avg 82% / 94% / 94% by phase, board power 121 / 137 / 149 W, hotspot ≤ 76 °C
  - CPU utilization avg 49 / 36 / 33%

### 4.3 Frames (java.exe)
- **FPS (AMD rows):**
  - before the test: avg 512, min 28
  - steady1: avg 357, min 18, p5 40
  - burst: avg 311, min 48, p5 54
  - steady2: avg 392, min 46, p5 63
- **Frame times:** 322,210 frames; p50 1.53 ms, p99 22.2, p99.9 53.2, **max 2,290 ms**.
- **Hitches over 50 ms, per minute:**

  | Period | per min | worst |
  |---|---|---|
  | **Before any model call** | **30** | 2,290 ms |
  | steady1 | 40 | 915 ms |
  | burst | 11 | 442 ms |
  | steady2 | 9 | 451 ms |

  Within the test: generating 25.9/min against idle 15.4/min for frames over 50 ms, but 5.6 against 8.2 for frames over 100 ms.
- **The largest test-time freeze (915 ms, t = 38.8 s) happened with the model idle**, coinciding with CPU at 80–90%, RAM available at ~284 MB and world activity. A cold 6 s model call had run from 22 to 31 s.
- **Run 1 alone could not separate a model effect from the game's own load.** Run 2 could.

---

## 5. Run 2 results (minimal desktop, vsync 120 Hz)

### 5.1 Calls (83, 0 failures)
| Call | N | p50 ms | p95 ms | max ms | gen tok/s | avg prompt tok | cache hit |
|---|---|---|---|---|---|---|---|
| GEO prose | 16 | 4,094 | 5,905 | 5,905 | 21.5 | 1,170 | 32% |
| BACKGROUND summary | 27 | 5,651 | 9,651 | 10,210 | 18.4 | 1,156 | 45% |
| NEGOTIATE decision | 13 | 3,612 | 5,594 | 5,594 | 20.0 | 1,440 | 38% |
| NEGOTIATE prose | 13 | 3,946 | 5,711 | 5,711 | 19.4 | 1,420 | 47% |
| CHAT prose | 14 | 4,292 | 7,159 | 7,159 | 19.4 | 1,256 | 36% |

- **Player-facing latency by phase (p50 / p95 / max ms):**

  | Phase | p50 | p95 | max |
  |---|---|---|---|
  | steady1 | 3,716 | 7,159 | 7,159 |
  | burst | 4,161 | 5,497 | 5,583 |
  | steady2 | 3,646 | 6,258 | 6,258 |
- **Prompt processing:** 393 tok/s average.
- **Background overlap:** **4,544 ms at 16.2 tok/s** overlapped against **3,512 ms at 24.0 tok/s** alone (n = 28 / 28).
- **Latency was essentially unchanged from run 1** despite the lighter desktop.

### 5.2 Memory
- **VRAM peak 6,182 MB**, made up of:
  - llama-server 2,804 MB
  - Minecraft 691 (menu) → 1,601 → 1,870 MB
  - everything else ≈ 1,800 MB: dwm 542, steamwebhelper 418, RadeonSoftware 179, csrss 139, msedgewebview2 115
- **System RAM available:** 549–700 MB throughout the test.
- **Working sets:**
  - **llama-server: 3,791 MB before the test, up to 5,110 MB during it.** That's model mmap pages, the 1,024 MiB host prompt cache and runtime buffers. The breakdown is unmeasured.
  - **The dev JVM: up to 5,301 MB.** A dev run uses the JVM's default heap, which is larger than a launcher's typical 2 GB.
- **Paging (`Pages Input/sec`):**
  - idle average 55
  - prompt processing average 319
  - generation average 2,261, dominated by **one spike of 88,623/s at t = 387 s during generation**, plus 11,335 at 498 s during prompt processing
  - pagefile use ≤ 3%
- **AMD hardware by phase:**
  - GPU utilization avg 53 / 79 / 67%
  - clock 1,632 / 2,356 / 1,932 MHz
  - board power 75 / 103 / 83 W
  - CPU utilization 13–16%

### 5.3 Frames: whole test window
- **Frame times:** 66,376 frames; median 8.33 ms (vsync 120 Hz), p99 33.4, p99.9 58.6, **max 124 ms**. 648 frames over 33.4 ms, 136 over 50 ms, **3 over 100 ms**.
- **FPS (AMD rows) by model state:**

| State | avg FPS | p5 | < 90 FPS | < 60 FPS | < 45 FPS |
|---|---|---|---|---|---|
| idle | 118 | 42 | 17.7% | 13.6% | 7.3% |
| prompt processing | 104 | 43 | 20.1% | 9.9% | 5.7% |
| **token generation** | **85** | 41 | **46.0%** | **28.9%** | **13.9%** |

- **Hitch rates by state (permutation p in brackets):**

| Threshold | idle /min | prompt processing | token generation |
|---|---|---|---|
| > 25 ms | 193 | 172 (0.89×, p = 0.65) | **411 (2.13×, p = 0.011)** |
| > 33.4 ms | 42 | 50 (1.19×, p = 0.36) | **113 (2.68×, p = 0.005)** |
| > 50 ms | 8.9 | 14.6 (1.64×, p = 0.22) | 18.2 (2.04×, p = 0.135, n.s.) |

- **Lined up on the start of token generation** (80 calls; hitches over 33.4 ms per 2 s window):
  - −6 to 0 s: 1.68, 1.41, 1.63
  - 0–2 s: **3.38**
  - 2–4 s: **4.45**
  - 4–6 s: 2.46
  - 6–8 s: 0.93

  The pre-start windows include some neighbouring call activity, so this comparison is conservative.

### 5.4 The away window: the decisive comparison
A 15-second timeline (`analysis/run2_timeline.ps1`) shows a stretch from **~150 to 330 s** with steady 111–117 FPS and essentially no hitches, consistent with the user stepping away. Hitches resume at 330 s.

| Window | Model active | Hitches > 33.4 ms | Hitches > 50 ms |
|---|---|---|---|
| **Away (~150–330 s; game static)** | 63% of the time; GPU 70–83% busy | **3 in 180 s (1.0/min)** | **0** |
| **Player active (45–150, 330–600 s; building)** | 41% | 91/min overall | 19.4/min |
| active: model idle | | 56.1/min | 12.2/min |
| active: model generating | | **184.3/min (3.29×, p = 0.008)** | **31.2/min (2.55×, p = 0.034)** |

By call kind, within active windows (hitches over 33.4 ms per minute):
- idle: 56.1 (220 s)
- player-facing call only: **126.4** (79 s)
- background call only: **152.5** (36 s)
- both at once: **157.9** (40 s)

In the away window the same split was 0.9, 0, 6.6 (2 hitches in 18 s) and 0 (58 s with both kinds in flight).

---

## 6. Response quality (4B Q4_K_M; both runs read by hand)

| | Run 1 | Run 2 |
|---|---|---|
| Negotiation decisions legal | 12/12 | 13/13 |
| Prose consistent with committed outcome | 12/12 (but the NPC "sold" the hammer in ~8/12, a test prompt bug) | 13/13 roles correct after the fix; **2/13 mentioned a stray number** ("Seventeen is fair, but I'll settle for fifteen") |
| Geography, NPC knew the fact | numbers right every time, including "ninety", "one hundred and eighty", "four hundred thirty" | **3/16 number-in-words errors:** "eighteen hundred blocks" for 180 (twice, identical reply), "sixty-one blocks" for 610 |
| Geography, NPC did NOT know | **3 fabrications:** "there isn't a river", "Fishers Landing doesn't exist", "beyond the mountains" (that NPC knew the lake, but described it wrongly) | 1 miss: "I don't think we have anyone north of the old mine" |
| Background summaries | **4/23 refused or role-confused** ("I am not Elsbeth"); several embellished ("helped Haldor find his lost hammer" when the event was only *accepted the request*) | **6/27 refused or confused** |
| Traps | mayor 3/3 correct ("there ain't no mayor"), "are you an AI" in character, New York in character | no manipulation compliance observed |
| "Say numbers as digits" | ignored (number words throughout) | ignored |

---

## 7. Interpretation (as of 2026-10-02)

Confidence: **H** = strongly supported by these runs; **M** = supported but confounded or small-sample; **L** = hypothesis.

1. **(H) Tier A has no VRAM room for a bigger default model.** Our own footprint (runtime + Minecraft) is about 4.9 GB with the 4B Q4. A heavy desktop adds about 2.5 GB, leaving roughly 1 GB of 8. A minimal desktop leaves about 2 GB. A 7–9B Q4 (+2–3 GB) can't be the default, and Q6_K of the 4B (+0.8 GB) only suits lean desktops. When VRAM overflows, Windows spills into system RAM: a performance cliff.
2. **(H) The 4B holds the structurally enforced parts.** That's constrained decisions and outcome-consistent prose. Its failures are in knowledge gating (fabricating instead of "I don't know"), spelling numbers, and summarization. Those are fixed by design (Java gates knowledge and renders numbers; memories stay Java-created), not by a bigger model.
3. **(H) Background inference running alongside player-facing inference costs the player 30–66% latency.** Run 2: 3.5 → 4.5 s. Run 1: 3.3 → 5.5 s.
4. **(H) Token generation coincides with frame hitches *only when the game is also doing work*.**
   - A static scene was hitch-free with the model busy 63% of the time.
   - While building, generation tripled frames over 33 ms.
   - Prompt processing showed no significant effect.
   - The effect is frequent 33–50 ms micro-stutter for the ~2–4 s a reply generates. Long freezes (over 100 ms) were rare in run 2.
5. **(M) Mechanism.** Most likely GPU scheduling contention between llama's Vulkan work and the game's frame and update work: the game needs bursts the model then delays. A memory/paging contribution is possible, given one 88k pages/s spike during generation. Not proven.
6. **(H) A dialogue screen is the low-risk case.** A static player resembles the away window. So **player-facing conversation in a dialogue UI should feel fine on tier A, while background inference during active play should not run** without pacing.
7. **(M) Generation speed (16–24 tok/s) is roughly half the solo spike.** Plausible causes: the game's GPU load, longer contexts and slot sharing. Not separated.
8. **(H) Prefix caching is underperforming (32–51% reuse).** Warm calls ran in about half the time of cold ones. Stable per-NPC prefixes and stable memory ordering are the next latency lever.
9. **(M) System RAM is a real constraint on a 16 GB machine.** llama-server alone holds about 5 GB. Part of it is reducible (`--cache-ram`, `-np 1`, `--no-mmap` to test). The dev JVM overstates a player's game RAM.
10. **Run 1's large freezes (up to 2.3 s)** came mostly from world loading and system pressure before any model call, not from the model.

---

## 8. Decisions taken from these results

**Canon (2026-10-02, at the user's direction):**
- `04` §2.14.1: the evidence index.
- `04` §2.22.3: tier validation is whole-system, frame pacing included.
- `04` §28: gameplay-critical numbers are rendered deterministically.
- `04` §29.1: knowledge gating in Java.
- `04` §36: background inference yields to player-facing inference, and on the local single-player path doesn't run during active gameplay.
- `04` §37: frame pacing, not average FPS.
- `04` §38: tier A sits at the low end of the evaluation range.
- `03` §33: AI-written memory summaries are non-authoritative.
- `09` Phase 9: measure hitches, active-versus-static play and system RAM.
- `10` §60 Testing Strategy: experiments are recorded like this one.

**Plan (`docs/plans/IMPLEMENTATION_PLAN.md`):**
- Tier A default stays in the 4B Q4 class.
- One runtime slot, with player-facing first.
- Java-rendered numbers.
- Knowledge gating.
- Prefix-cache work.
- RAM trimming.
- Hitch mitigations to test.

---

## 9. Open questions and next experiments
1. `-np 1` plus `-DeferBackground`, with part of the run standing still and part building, to confirm that scheduling removes the background penalty and shrinks stutter windows.
2. Flash attention plus a `q8_0` KV cache: VRAM and speed.
3. 4B Q6_K, quality and VRAM, on a lean desktop.
4. Lower GPU scheduling priority for the runtime process (we own it in the managed runtime), and smaller `-b` / `-ub` batches, as hitch mitigations. Untested.
5. llama-server RAM with `--cache-ram 256/0`, `-np 1` and `--no-mmap`.
6. A launcher-like JVM heap (`-Xmx2G`) for realistic game RAM. Also GC logging (`-Xlog:gc`) if large hitches persist.
7. Prefix-cache hit rate after stabilizing the per-NPC prefix and memory order.
8. The 3060 12 GB machine as a tier B / LAN data point.

---

## 10. Files

| Path | Contents |
|---|---|
| `data/run1/run_20261002_133827.csv` | Per-call data, run 1 (**old format:** `prompt_ms` contains a thousands separator; re-parse by merging the split field) |
| `data/run1/replies_20261002_133827.txt` | Every input and reply, run 1 |
| `data/run1/gpu_20261002_133244.csv` | VRAM / RAM / CPU log, old 8-column format (the logger kept running until 14:22) |
| `data/run1/FPS.Latency.20261002-134857.CSV`, `Hardware.20261002-134858.CSV`, `20261002-134858.FrameTime` | AMD logs, run 1 |
| `data/run2/run_20261002_142502.csv`, `replies_20261002_142502.txt`, `gpu_20261002_142358.csv` | Run 2 equivalents (fixed formats; the logger has RAM and paging columns) |
| `data/run2/FPS.Latency.20261002-143534.CSV`, `Hardware.20261002-143535.CSV`, `20261002-143535.FrameTime` | AMD logs, run 2 |
| `tools/stress.ps1`, `tools/gpulog.ps1` | The scripts as used in run 2 (run 1 used an earlier stress.ps1; see §2.3) |
| `analysis/*.ps1` | The analysis scripts behind §4–§5. They contain absolute paths from the analysis machine, so adjust before re-running. |

The 2026-09-28 single-request spike that preceded these runs is recorded in the implementation plan ("Post-M1 inference spike: run 1").
