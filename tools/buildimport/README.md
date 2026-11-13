# buildimport: survey and export imported builds (dev only)

Reads Minecraft Java worlds straight from their region files. Two jobs:
- **survey** a downloaded map: preview maps, a building catalog, districts (Track B1 job 1, the Drachenfels survey:
  `docs/notes/2026-11-12_drachenfels_survey/REPORT.md`)
- **export** a town into the mod: structure templates plus a manifest, and the **workshop loop** that lets you edit
  the town in game and export it again (Track B1 job 2, D1 Harbour)

It never writes to a world it reads. Nothing here ships with the mod or runs in the game; only the files it writes
into `inhabited_world_mod/src/main/resources` do.

## Editing a town (the workshop loop)
Each town has a **workshop world**: a flat sea world with only that town in it, which you edit in creative mode.
After the town's first export, the workshop world is the town's source of truth.

**1. Open the workshop.** Double-click `workshop.bat` in the workspace folder (or run `workshop.bat d1_harbour`).
- The first time, it builds the mod, starts the game and creates the world `workshop-d1_harbour` with the
  Harbour in it. You start on the quay. Time, weather and mob spawning are off.
- Every later time it opens that same world again. It never deletes or recreates it.

**2. Edit.** Build, break and furnish as in any creative world.
- Everything inside the town's box counts: x 0 to 271, z 0 to 255 (`F3` shows your position). The sea is east.
- Ground you add on the bare grass at the box's edges becomes part of the town only if you build on it with placed
  blocks (planks, bricks, ...). The plain grass there is only a stand-in for the generated terrain the town will
  meet in a real world.
- Under each column of the town the paste put plain stone. Digging into it is not exported: below the town is
  solid ground. To make a cellar deeper, build its walls and floor with placed blocks.

**3. Add or move markers** (named places that NPCs and quests refer to, canon `02` §16).
- Place a sign (any wood; standing, wall or hanging) where the place is. On its **first line write `@` and the
  name**, for example `@smithy`, `@inn`, `@d1_west_gate`. Use letters, digits and `_`.
  Lines 2 to 4 are an optional note.
- The direction the text faces is the direction an NPC standing there faces.
- To move a marker, move its sign. To delete it, break the sign.
- The six markers the Harbour starts with already have signs: `@d1_quay`, `@d1_pier_north`, `@d1_pier_middle`,
  `@d1_pier_south`, `@d1_market_square`, `@d1_main_gate`. `/inhabited town markers d1_harbour` lists them with
  their positions.
- (A structure block in Data mode, with the name as its "Custom Data Tag Name", works too.)

**4. Save and quit.** Use *Save and Quit to Title* or close the game. The export refuses to read a world that is
still open.

**5. Export.** Double-click `export-town.bat` (or run `export-town.bat d1_harbour`).
- It rewrites the town's templates, columns file and manifest in `inhabited_world_mod/src/main/resources`.
- It prints how many columns changed. A re-export of an unedited workshop writes identical files, so `git status`
  then shows nothing.

**6. See the change.** Start any test world (`test-launch.bat`) and paste the town somewhere open:
`/inhabited town paste d1_harbour ~ ~ ~`. Pasting takes a few seconds; it pastes the templates now in the mod.
(Placing the town into generated worlds is Phase 3.)

**7. Keep it.** Commit the changed files under `inhabited_world_mod/src/main/resources/data/inhabited_world/`
(towns/, structure/towns/). The workshop world itself lives in `run/saves` and is not in git: export after each
editing session so the templates hold your work.

Before a town ships, each export overwrites its current version (`v1`). Once a town has shipped (`"shipped": true`
in `towns/<town>.json`), exports refuse to overwrite it; `--new-version` writes `v2` and so on (canon `02` §6).

## Commands
Run from this folder, with Python 3.11+, numpy, scipy and Pillow (pytest for the tests).
```
python -m buildimport export d1_harbour                 # write the town from its source of truth
python -m buildimport export d1_harbour --from original # force the original map (before shipping only)
python -m buildimport export d1_harbour --preview zones.png
python -m buildimport roundtrip d1_harbour <world> --origin x,y,z   # compare a pasted copy, block for block
python -m buildimport building 113 262                  # catalog buildings as standalone library templates
python -m buildimport scan  "<world folder>" <scratch>/world_scan.npz
python -m buildimport survey <scratch>/world_scan.npz <output folder> --districts <districts.json> [--labels labels.npz]
python -m pytest -q -p no:cacheprovider tests           # unit tests
```
The source of truth is chosen automatically: the town's workshop world when it exists (`towns/<town>.json`,
`"workshop"`), otherwise the original map (`"original"`: Drachenfels, read from the main checkout's `run/saves`).

## What an export writes
For D1 Harbour (`towns/d1_harbour.json`), version 1:

| file | what it is |
|---|---|
| `data/inhabited_world/structure/towns/drachenfels/d1_harbour/v1/tile_<i>_<j>.nbt` | vanilla structure templates, 32 × 32 columns each, full height; load with `StructureTemplateManager` as `inhabited_world:towns/drachenfels/d1_harbour/v1/tile_<i>_<j>` |
| `data/inhabited_world/towns/drachenfels/d1_harbour/v1/manifest.json` | id and version; size; footprint; anchor and rotation pivot; sea level and the y offset to ours; waterfront side and quay line; joins to the neighbouring districts; the blend edge; tiles; markers; workshop origin; stats |
| `data/inhabited_world/towns/drachenfels/d1_harbour/v1/columns.nbt` | the terrain mask: per column (index `z * width + x`) its zone, floor, top and ground |

**Template space:** x east, z south; the origin (0, 0, 0) is the box's north-west corner at its lowest stored block.
Every tile is placed at origin + its `offset`. Place template y 0 at world y `target_sea_level - sea_level` (the
manifest's `levels.y_offset`) so the town's sea meets ours at y 63. Rotating the town turns the whole set of tiles
about `anchor.rotation_pivot`.

**Zones** (per column of the box):
- **mold**: part of the build (any block the survey rules count as built: placed blocks, and natural blocks
  attached to them). Stored exactly from its floor to its top: interiors, air and water included.
- **support**: open ground the build stands in (streets, yards, an 8-block apron around buildings, enclosed squares).
  Stored the same way.
- **sea**: open water at sea level (the harbour basin and the open sea inside the box). Stored from below the
  seabed up to the surface, so the template brings its own harbour depth.
- **blend**: nothing of the town; not stored. Phase 3 lets generated terrain meet the town here.

**Floors are cut per column:** below the lowest of the column's built blocks and the surface water above it, down to
the terrain under that, plus 4 blocks. A cellar column goes down with its cellar, a pier column down to the seabed,
a street column 4 blocks under the paving.

**Placement contract** (what the workshop paste does, and what Phase 3 must do): above a stored column's top, air;
the column itself, exactly the template's blocks; below its floor, solid ground (fill air and water down to the
first solid block). Set the blocks with `Block.UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SKIP_ON_PLACE`, and
`LiquidSettings.IGNORE_WATERLOGGING`: otherwise rails re-shape, fire goes out and stairs pick up water. With these,
a paste matches the templates block for block (game test `townRoundTrip`).

**The sea and the waterfront.** The waterfront is the box's east side. The quay line (median over the rows that
reach open water) is at template x 164 (Drachenfels x 292). East of it lie the harbour basin (water down to a
painted seabed 14–28 deep), three piers and two moored ships, all stored. The box ends about 108 blocks out, at
x 271. Phase 3's job there: our generated sea
continues from that edge, and its seabed should blend toward the template's seabed heights along the east edge
(the `sea` entries of `blend.edge`). The planner already requires open sea beyond the waterfront.

**Neighbouring districts.** Drachenfels is one city, so the box also clips the edges of D2, D3, D8 and D9. Their
buildings are erased (air, or water below sea level) using the survey's building map
(`docs/notes/2026-11-12_drachenfels_survey/labels.npz`); D1's own buildings stay whole. `joins` in the manifest
says where each neighbour would attach.

**Entities.** Only decoration is kept: item frames, paintings, armor stands, leash knots and display entities.
Mobs, villagers, items and vehicles are left out (Phase 2B: town templates hold no vanilla hostiles or villagers).
Drachenfels' D1 has no entities at all.

**Map-maker terrain paint.** Drachenfels' seabed and rocks are painted with cobblestone, mossy cobblestone and
smooth sandstone. The exporter classifies blocks with exactly the survey's rules (`rules.py`, rule C), so that
paint counts as ground, not as build.

## Building library
`python -m buildimport building <catalog id> ...` writes one catalog building (`catalog.csv`) as a standalone
template plus a mini manifest:
- `data/inhabited_world/structure/library/drachenfels/building_<id>/v1.nbt`
- `data/inhabited_world/library/drachenfels/building_<id>/v1.json`: footprint (with its outline), size, ground
  height, entrance side and door positions, markers, source position

The building's columns come from the survey's building map, plus loose built columns right next to it (steps,
railings). Each column is cut one block under its lowest built block, so a ship comes without its harbour water.
Samples: house 113 and the moored ship 262. There is no town composer yet.

## Checks
- `tests/` (pytest): rules and scan; the 26.x palette formats; lossless NBT; template writing; zones, floors and
  terrain paint; markers and entity filtering; an offline paste and round trip; a byte-identical re-export from an
  unedited workshop; a workshop edit being exported; the shipped-version guard; the building export.
- Game tests (`gradlew build`, `TownTemplateGameTests`): one tile loads through vanilla's template manager and
  places exactly; the whole town pasted with the workshop paste matches every template block and block entity; a
  workshop paste puts a sign on every marker.
- Full round trip (manual, needs the Drachenfels save): after `gradlew runGameTest`,
  `python -m buildimport roundtrip d1_harbour inhabited_world_mod/build/run/gameTest/world --origin 3072,2,3072`
  compares the game's paste with the templates and with Drachenfels itself.

## How it works
| file | what it does |
|---|---|
| `nbt.py` | reads NBT, plain or typed (lossless, for copying block entities), and writes it |
| `anvil.py` | reads region files and decodes chunk block palettes (26.x `{id, properties}` and bare strings, 1.13–1.21 `{Name, Properties}`, pre-1.13 numeric IDs) |
| `rules.py` | **the classification rules, in one readable list**: natural / vegetation / placed / furnishing / light, plus the "attached" and "terrain paint" rules. Tune here |
| `scan.py` | streams every chunk, classifies it (`classify_volume`, shared with the exporter) and reduces each column to numbers; lists room slices |
| `analysis.py` | finds the authored area, ground, the city outline, buildings, floors, entrances and the coast |
| `survey.py` | districts, images, catalog CSV and contact sheets, `summary.json`, the building map |
| `world.py` | full-fidelity block reading for the exporter: every block state, block entities, entities |
| `markers.py` | marker signs and data structure blocks |
| `template.py` | writes and reads vanilla structure template files |
| `export.py` | the town export: ownership, zones, floors, tiles, columns, manifest; the workshop re-export |
| `roundtrip.py` | the block-for-block comparison |
| `library.py` | single-building export |
| `towns/<town>.json` | a town's export settings: sources, box, seed markers, joins, tunables |

All thresholds are named constants at the top of `analysis.py` and `rules.py`, or settings in `towns/<town>.json`.

## Why Python
- The job is offline array maths over a whole save. numpy and scipy do it in seconds to minutes with no build step,
  and Pillow draws the maps.
- Keeping it outside the Gradle project means no extra source set, dependency or build time for the mod.
- Structure templates are plain NBT, so Python writes them directly. Minecraft's own code only needs to *read*
  them, which the game tests check.
