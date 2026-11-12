# buildimport: offline world survey tool (dev only)

Reads a Minecraft Java world straight from its region files and measures what was built in it. It writes
preview maps, a building catalog and a summary; nothing here ships with the mod or runs in the game. Track B1's
first job, the Drachenfels survey, was made with it (`docs/notes/2026-11-12_drachenfels_survey/REPORT.md`).

It never writes to the world it reads. The world can be:
- a downloaded map (Drachenfels)
- a per-town workshop world the user edits in creative mode

## Why Python
- The job is offline array maths over a whole save: about 14k chunks, 2048 × 2560 columns. numpy and scipy do that
  in a couple of minutes with no build step, and Pillow draws the maps.
- Keeping it outside the Gradle project means no extra source set, dependency or build time for the mod. Nothing
  in it can leak into the shipped jar.
- The later exporter, which writes our structure templates, may want Minecraft's own template code. It can be a
  Java dev task that reads this tool's outputs (the catalog, district outlines and keep masks).

## Use
Run heavy steps at Idle priority on 2 cores (user rule):
```powershell
$me = Get-Process -Id $PID; $me.ProcessorAffinity = [IntPtr]0xF00; $me.PriorityClass = 'Idle'
cd tools/buildimport
python -m buildimport scan  "<world folder>" <scratch>/world_scan.npz        # slow part, ~100 s for Drachenfels
python -m buildimport survey <scratch>/world_scan.npz <output folder> --districts <districts.json>   # ~1 min
python -m pytest -q -p no:cacheprovider tests                                 # unit tests
```
Needs Python 3.11+ with numpy, scipy and Pillow (pytest for the tests).

## How it works
| file | what it does |
|---|---|
| `nbt.py` | reads NBT, Minecraft's binary data format (and writes small samples for the tests) |
| `anvil.py` | reads region files and decodes chunk block palettes. Handles 26.x (bare-string palette entries for default states, mixed lists wrapped as `{"": value}`), 1.13–1.17, and plain pre-1.13 terrain |
| `rules.py` | **the classification rules, in one readable list**: natural / vegetation / placed / furnishing / light, plus the "attached" and "terrain paint" rules. Tune here |
| `scan.py` | streams every chunk and reduces each block column to numbers: surface, ground, water, highest and lowest built block, furnishings. It also lists every "room slice" (air under a built ceiling) |
| `analysis.py` | finds the authored area, ground, the city outline, buildings (split into houses along terraces), floors, entrances and the coast |
| `survey.py` | districts, images, catalog CSV and contact sheets, `summary.json` |
| `colors.py` | approximate block colours for the top-down map |

All thresholds are named constants at the top of `analysis.py` and `rules.py`.
