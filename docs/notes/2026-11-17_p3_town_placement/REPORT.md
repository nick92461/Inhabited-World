# Phase 3 part 1: the D1 Harbour placed in generated worlds

**In short:** every new RPG world now gets the D1 Harbour at the planner's town site, turned by the plan's rotation,
with the generated terrain blended up to it, its six markers persisted as semantic locations, and a read-back check
recorded in the WorldPlan. It happens once, during world creation, after pre-generation and before the player can
enter. It adds about @@TOTAL@@ seconds to creating a world.

![Top-down map of the placed Harbour on the test seed. Red: the town's bounds. Yellow: its region (the blend border's outer edge).](@@IMG_MAP@@)

*Seed 20261029, north up. The Harbour is turned a quarter anticlockwise, so its sea side faces north.*

## 1. How it works

**Mechanism: a controlled paste after pre-generation** (`town/TownPlacement`), not a structure placed during
chunk generation. Reasons:

- The site is per world: it comes from the WorldPlan, not from data-driven structure placement. A worldgen
  structure would need its placement to read runtime state from inside the generator.
- Vanilla's terrain adaptation (the beardifier) reaches about 12 blocks around a piece and only raises or carves
  around it. The Harbour needs more: a 32-block border, a seabed blend at the sea side, and a ~30-block climb from
  quay to back street.
- The export's placement contract (no updates, air above each column, solid ground below) and one-tile-at-a-time
  loading are easy to guarantee in a paste. A structure would put all 72 tiles in vanilla's template cache
  (~100 MB).
- Placing into finished chunks can be checked block by block and resumed step by step.

The cost: the chunks under the town are generated first and then rewritten (about 500 of the map's 7,744), and
creation takes a few seconds longer. Canon `02` §6 already makes creation the place for this kind of work.

**When.** At the start of level preparation (`MinecraftServerMixin`): pre-generation, then the existing terrain
check (`SiteVerifier`, which must measure the natural ground the town replaces), then placement.

**What is placed.** The list is made once, with the WorldPlan, in a new `PlacementState`
(`data/inhabited_world/structure_placement.dat` in the overworld). Each site whose catalog ID names a town in the
mod's data gets an entry, pinned to the exact build version found then (`drachenfels/d1_harbour/v1`). Nothing else
ever adds entries, so:

- worlds made before this change are never touched
- a placed town is never placed again
- the POI (placeholder catalog ID) gets nothing until part 3

**Steps** (each idempotent, so repeating one after a crash gives the same world):

1. **TILES**, one at a time: load the tile straight from the mod's data (not through vanilla's cache), then prepare
   its stored columns:
   - air above the column's top
   - stone filled below its floor
   - infested blocks under the town turned into plain stone, down to the town region's floor
   - ores within 8 blocks under a floor turned into stone

   Then drop any entity a town may not carry, and place the template turned by the site's rotation, with no
   updates and ignoring waterlogging.
2. **BLEND**: compute a new ground height for every blend column (`TownBlend`, below) and rebuild each column that
   changes. Then:
   - remove the generated animals in the site's region
   - let the light engine catch up
   - read back sampled blocks
   - record the placed structure, with its semantic locations, in the WorldPlan
3. **DONE**.

Progress is written to disk only right after a full save of the chunks and the plan. A crash can therefore make a
step repeat, but never skip one.

**Rotation** (`TownTransform`): vanilla's own template rotation, plus a shift so that the turned box fills the
site's bounds exactly. Block states turn with vanilla's `BlockState.rotate`, as in any rotated structure. Marker yaw
turns by 90° per clockwise quarter turn.

## 2. The terrain blend

![Ground heights after placement: grey for land (lighter is higher), blue for seabed, brown contour every 4 blocks.](@@IMG_HEIGHTS@@)

Blend columns are:
- the town's own blend columns inside its box: 2,488, mostly the strips where neighbouring districts were erased
- the 32-block border around the box: 37,888

Their new ground is the **harmonic interpolation** between two fixed edges:
- the ground of every stored town column (under a building, a street, or the seabed under the town's sea)
- the generated ground one block outside the region, which placement never changes

A harmonic surface has no peaks or pits of its own, so the blend can't add a cliff the edges don't already have.
It follows the quay-to-back-street climb along the edge, and blends seabed into seabed at the sea side exactly as
it blends land into land. It is solved by successive over-relaxation (about 150 sweeps, under 1 s).

A column whose ground already has the right height is left exactly as generated, plants and trees included. A
changed column gets, from the top down:
- air above it (or water up to the sea surface)
- the surface block: grass on land, sand at the shore and on a shallow seabed, gravel on a deeper one
- 3 blocks of the matching subsoil
- stone filled down through any air or fluid
- ore and infested-block cleanup under it

@@BLEND_NUMBERS@@

## 3. Semantic locations (`02` §16)

The manifest's six markers are resolved through the rotation into world positions and yaws, and saved in the
WorldPlan as part of the placed structure, together with the semantic region at each position.

**WorldPlan data version 5** adds `structures`: a list of placed structures, each with:
- site, catalog ID and exact build
- rotation
- the world position of template (0, 0, 0)
- locations
- the read-back check

Plans of versions 1 to 4 read with no placed structures and are written back as version 5. Nothing is placed into
them.

The read API is `worldstate/SemanticLocations`: `of(server)` or `of(plan)`, then `get(id)`, `all()` and
`ofSite(site)`. It works for any number of structures.

@@LOCATIONS@@

## 4. Numbers

@@NUMBERS@@

## 5. Known issues

@@ISSUES@@

## 6. Open questions

@@QUESTIONS@@
