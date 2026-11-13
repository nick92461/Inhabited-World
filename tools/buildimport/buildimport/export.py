"""Export a town (a Drachenfels district, later any workshop town) to the mod's structure templates and manifest.

    python -m buildimport export d1_harbour            # reads the town's source of truth, rewrites its files

Source of truth: the town's workshop world once it exists (tools/buildimport/towns/<town>.json, "workshop"),
otherwise the original map ("original"). --from original forces the original map (only before the town ships).

What is written (resource path from the town config, e.g. towns/drachenfels/d1_harbour, then v<version>):
    data/inhabited_world/structure/<path>/v1/tile_<i>_<j>.nbt   vanilla structure templates, one per 32 x 32 columns
    data/inhabited_world/towns/<...>/v1/manifest.json           what Phase 3 and the workshop need (see README)
    data/inhabited_world/towns/<...>/v1/columns.nbt             per-column zone, floor and top (the terrain mask)

The three zones (agreed in the plan), decided per column of the template box:
    MOLD     the column holds part of the town's build: stored exactly, from its floor to its top
    SUPPORT  open ground the build stands in (streets, yards, an apron around buildings): stored the same way
    SEA      open water at sea level (harbour basin, open sea inside the box): stored, seabed to surface
    BLEND    nothing of ours: NOT stored. Only the mask says where Phase 3 blends generated terrain
A column's floor is cut per column: below the lowest of its built blocks and the surface-water run above it, down to
the terrain under that, plus SUPPORT_DEPTH more blocks. Placement contract: everything above a stored column's top
is air, and below its floor there is solid ground (the placer fills down to existing solid ground).

Buildings of neighbouring districts that reach into the box are erased (air, or water below sea level), using the
survey's building map; the district's own buildings stay whole.
"""
from __future__ import annotations

import datetime
import json
import os
import subprocess
from dataclasses import dataclass, field

import numpy as np
from scipy import ndimage as ndi

from . import markers as M, nbt, rules, template
from .nbt import T
from .world import World, BlockBox, StateTable

MOLD, SUPPORT, SEA, BLEND = 1, 2, 3, 0
ZONE_NAMES = {MOLD: "mold", SUPPORT: "support", SEA: "sea", BLEND: "blend"}

# Entities kept in templates: decoration only. Everything else (mobs, villagers, items, vehicles, projectiles...)
# is dropped: town templates must contain no vanilla hostiles or villagers (P2B), and live creatures belong to
# Phase 4's NPC system, not to the building.
DECORATIVE_ENTITIES = {
    "minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:painting", "minecraft:armor_stand",
    "minecraft:leash_knot", "minecraft:block_display", "minecraft:item_display", "minecraft:text_display",
}

TOOL_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = os.path.dirname(os.path.dirname(TOOL_DIR))
RESOURCES = os.path.join("inhabited_world_mod", "src", "main", "resources", "data", "inhabited_world")


def resolve(path: str) -> str:
    """A path from a town config: absolute, or relative to the repository root. A git worktree has no run/ saves of
    its own, so a missing relative path is also looked up in the main checkout."""
    if os.path.isabs(path):
        return path
    p = os.path.join(REPO, path)
    if os.path.exists(p):
        return p
    try:
        common = subprocess.run(["git", "-C", REPO, "rev-parse", "--git-common-dir"], capture_output=True,
                                text=True, check=True).stdout.strip()
        main = os.path.dirname(os.path.abspath(os.path.join(REPO, common)))
        q = os.path.join(main, path)
        if os.path.exists(q):
            return q
    except (OSError, subprocess.CalledProcessError):
        pass
    return p


def load_town(name: str) -> dict:
    path = name if name.endswith(".json") else os.path.join(TOOL_DIR, "towns", name + ".json")
    with open(path, encoding="utf-8") as f:
        cfg = json.load(f)
    cfg["_path"] = path
    return cfg


def resource_dirs(cfg: dict, version: int | None = None, root: str | None = None) -> tuple[str, str, str]:
    """(structure folder, towns folder, structure id prefix) for a town version. root: write under this folder
    instead of the mod's data (same layout below it)."""
    v = f"v{version or cfg['version']}"
    rp = cfg["resource_path"]
    base = root or cfg.get("output_root") or os.path.join(REPO, RESOURCES)
    sdir = os.path.join(base, "structure", *rp.split("/"), v)
    tdir = os.path.join(base, *rp.split("/"), v)
    return sdir, tdir, f"inhabited_world:{rp}/{v}/"


def workshop_present(cfg: dict) -> bool:
    w = cfg.get("workshop", {}).get("world")
    if not w:
        return False
    p = resolve(w)
    return os.path.isdir(os.path.join(p, "dimensions", "minecraft", "overworld", "region")) or \
        os.path.isdir(os.path.join(p, "region"))


# --------------------------------------------------------------------------------------------- column analysis

@dataclass
class Columns:
    """Per-column results over the box (arrays [z, x])."""
    zone: np.ndarray
    floor: np.ndarray       # world y of the lowest stored block (stored columns)
    top: np.ndarray         # world y of the highest stored block
    ground: np.ndarray      # world y of the terrain the column stands on (seabed for water)
    owned: np.ndarray
    foreign: np.ndarray
    erased: int = 0


def _top_index(mask: np.ndarray) -> np.ndarray:
    any_ = mask.any(0)
    return np.where(any_, mask.shape[0] - 1 - np.argmax(mask[::-1], 0), -1)


def _bottom_index(mask: np.ndarray) -> np.ndarray:
    any_ = mask.any(0)
    return np.where(any_, np.argmax(mask, 0), -1)


def _disk(r: int) -> np.ndarray:
    y, x = np.ogrid[-r:r + 1, -r:r + 1]
    return x * x + y * y <= r * r


def ownership(cfg: dict, box: BlockBox, district: str | None, labels_path: str | None) -> tuple[np.ndarray, np.ndarray]:
    """(owned, foreign) column masks. Without a building map every built column is the town's own (a workshop
    world holds only its town). With one (the original city), each built column belongs to the district of its
    building, or for loose built columns (walls, lamps, railings) to the district of the nearest building."""
    D, W = box.shape[1], box.shape[2]
    built2d = box.built.any(0)
    owned = built2d.copy()
    foreign = np.zeros_like(built2d)
    if district and labels_path:
        L = np.load(resolve(labels_path))
        lab_full = L["labels"].astype(np.int32)
        lx0, lz0 = int(L["x0"]), int(L["z0"])
        dist = json.loads(str(L["districts"]))
        lab = np.zeros((D, W), np.int32)
        za, xa = box.z0 - lz0, box.x0 - lx0
        zs0, xs0 = max(0, za), max(0, xa)
        zs1, xs1 = min(lab_full.shape[0], za + D), min(lab_full.shape[1], xa + W)
        if zs1 > zs0 and xs1 > xs0:
            lab[zs0 - za:zs1 - za, xs0 - xa:xs1 - xa] = lab_full[zs0:zs1, xs0:xs1]
        ids = np.unique(lab[lab > 0])
        mine = np.zeros(int(lab.max()) + 1, bool)
        for i in ids:
            mine[i] = dist.get(str(int(i))) == district
        labelled = lab > 0
        if labelled.any():
            _, (iz, ix) = ndi.distance_transform_edt(~labelled, return_indices=True)
            near = lab[iz, ix]
        else:
            near = lab
        own_col = mine[near]
        owned = built2d & own_col
        foreign = built2d & ~own_col
    for x0, z0, x1, z1 in cfg.get("overrides", {}).get("keep", []):
        sl = (slice(max(0, z0 - box.z0), max(0, z1 - box.z0 + 1)), slice(max(0, x0 - box.x0), max(0, x1 - box.x0 + 1)))
        owned[sl] |= built2d[sl]
        foreign[sl] = False
    for x0, z0, x1, z1 in cfg.get("overrides", {}).get("drop", []):
        sl = (slice(max(0, z0 - box.z0), max(0, z1 - box.z0 + 1)), slice(max(0, x0 - box.x0), max(0, x1 - box.x0 + 1)))
        foreign[sl] |= built2d[sl]
        owned[sl] = False
    return owned, foreign


def erase_foreign(box: BlockBox, foreign: np.ndarray, sea_level: int) -> int:
    """Remove other districts' built blocks: water where the block is at or below sea level and above the column's
    own terrain next to open water, air otherwise. Returns the number of blocks changed."""
    if not foreign.any():
        return 0
    t = box.table
    air, water = t.id("minecraft:air"), t.id("minecraft:water")
    ny = box.shape[0]
    fluid = box.cls == rules.FLUID
    near_water = ndi.binary_dilation((fluid[min(max(sea_level - box.y0, 0), ny - 1)] & ~foreign), iterations=3)
    ys = np.arange(ny)[:, None, None] + box.y0
    terr_top = _top_index(box.terrain & ~box.built)
    target = box.built & foreign[None]
    to_water = target & (ys <= sea_level) & near_water[None] & (np.arange(ny)[:, None, None] > terr_top[None])
    n = int(target.sum())
    box.states[target] = air
    box.states[to_water] = water
    box.cls[target] = rules.AIR
    box.cls[to_water] = rules.FLUID
    box.built[target] = False
    box.terrain[target] = False
    for (x, y, z) in list(box.block_entities):
        if foreign[z - box.z0, x - box.x0] and box.states[y - box.y0, z - box.z0, x - box.x0] in (air, water):
            del box.block_entities[(x, y, z)]
    return n


def analyse_columns(cfg: dict, box: BlockBox, owned: np.ndarray, foreign: np.ndarray, sea_level: int) -> Columns:
    depth = int(cfg.get("support_depth", 4))
    apron = int(cfg.get("support_apron", 8))
    close = int(cfg.get("courtyard_close", 12))
    ny, D, W = box.shape
    y0 = box.y0
    cls = box.cls
    nonair = cls != rules.AIR
    fluid = cls == rules.FLUID
    top = _top_index(nonair)
    has = top >= 0
    built_any = box.built.any(0)
    bb = np.where(built_any, _bottom_index(box.built), ny)

    # the surface-water run: the fluid run holding the column's highest fluid block, if that is at or above sea
    # level - 1 (the harbour, a fountain); underground water below sea level is ignored
    ftop = _top_index(fluid)
    yi = np.arange(ny)[:, None, None]
    last_solid = np.maximum.accumulate(np.where(~fluid, yi, -1), axis=0)          # highest non-fluid at or below y
    run_bot = np.where(ftop >= 0, np.take_along_axis(last_solid, np.maximum(ftop, 0)[None], 0)[0] + 1, ny)
    counts = (ftop >= 0) & (ftop + y0 >= sea_level - 1)
    fb = np.where(counts, run_bot, ny)

    lowest = np.minimum(np.minimum(bb, fb), np.where(has, top + 1, ny))
    terrain = box.terrain & ~box.built
    below = terrain & (yi < lowest[None])
    ground = _top_index(below)
    floor = np.where(ground >= 0, ground - depth, np.minimum(lowest, top))
    floor = np.clip(floor, 0, ny - 1)

    mold = built_any & owned
    surf_water = has & np.take_along_axis(fluid, np.maximum(top, 0)[None], 0)[0]
    sea = surf_water & (np.abs(top + y0 - sea_level) <= 1) & ~mold
    near = ndi.binary_dilation(mold, structure=_disk(1), iterations=apron) if apron else mold
    pad = close + 1
    enclosed = ndi.binary_closing(np.pad(mold, pad), structure=_disk(close))[pad:-pad, pad:-pad]
    enclosed = ndi.binary_fill_holes(enclosed)
    support = (near | enclosed) & ~mold & ~sea & has & ~surf_water
    zone = np.full((D, W), BLEND, np.uint8)
    zone[support] = SUPPORT
    zone[sea] = SEA
    zone[mold] = MOLD
    stored = zone != BLEND
    return Columns(zone, np.where(stored, floor + y0, 0), np.where(stored, top + y0, 0),
                   np.where(ground >= 0, ground + y0, floor + y0), owned, foreign)


def keep_untouched(cfg: dict, box: BlockBox, cols: Columns, origin_y: int, log=print) -> None:
    """Re-export from a workshop world: columns the user did not touch keep the current export's zone, floor and
    top exactly, so an unedited workshop re-exports to identical files. The survey rules see the workshop's paste
    (plain blend ground, stone under every floor) and would otherwise shift zones at the edges.

    A column is untouched when its blocks from the current floor to the current top equal the current templates,
    nothing stands above that top, and no placed block lies below that floor. Other columns use the fresh result,
    except that a blend column only joins the town when something is built on it."""
    from . import roundtrip
    man = read_manifest(cfg)
    grid, keys, _ = roundtrip.intended(cfg, man)
    prev = roundtrip.read_columns(cfg, man["version"])
    H, D, W = grid.shape
    if (D, W) != cols.zone.shape:
        log("the workshop box differs in size from the current export; every column is re-derived")
        return
    tk = {k: i for i, k in enumerate(box.table.keys)}
    gmap = np.array([tk.get(k, -1) for k in keys] + [-1], np.int64)
    ny = box.shape[0]
    oyb = origin_y - box.y0                      # box index of template y 0
    want = np.full((ny, D, W), -2, np.int64)     # -2: not stored
    ylo, yhi = max(0, -oyb), min(H, ny - oyb)
    g = grid[ylo:yhi]
    want[oyb + ylo:oyb + yhi] = np.where(g >= 0, gmap[np.where(g >= 0, g, len(keys))], -2)
    stored_prev = prev["zone"] != BLEND
    same = ((want == -2) | (want == box.states)).all(0)
    # nothing above the previous top, nothing built below the previous floor (box-relative indices)
    yi = np.arange(ny)[:, None, None]
    ptop = prev["top"] + oyb
    pfloor = prev["floor"] + oyb
    nonair_above = ((box.cls != rules.AIR) & (yi > ptop[None])).any(0)
    # placed blocks only: the stone the workshop paste put under every floor can count as "built" by rule B next to
    # a neighbour's cellar wall. (Digging below a floor without placing anything is not exported: below the floor
    # is solid ground by contract.)
    built_below = ((box.cls >= rules.PLACED) & (yi < pfloor[None])).any(0)
    untouched = stored_prev & same & ~nonair_above & ~built_below
    blend_prev = ~stored_prev
    placed_any = (box.cls >= rules.PLACED).any(0)
    blend_still = blend_prev & ~(placed_any & (cols.zone == MOLD))
    keep = untouched | blend_still
    cols.zone[keep] = prev["zone"][keep]
    cols.floor[keep] = np.where(stored_prev[keep], prev["floor"][keep] + origin_y, 0)
    cols.top[keep] = np.where(stored_prev[keep], prev["top"][keep] + origin_y, 0)
    cols.ground[keep] = np.where(stored_prev[keep], prev["ground"][keep] + origin_y, cols.ground[keep])
    edited = stored_prev & ~untouched
    new_mold = blend_prev & ~blend_still
    log(f"workshop edits: {int(edited.sum())} columns changed, {int(new_mold.sum())} blend columns now built on, "
        f"{int(untouched.sum())} untouched")


def standing_spot(box: BlockBox, x: int, z: int, top_y: int) -> int:
    """The highest y in a column where one can stand: solid block below, air at y (the marker sign goes there, so it
    must not replace anything) and a passable block above."""
    t = box.table.rule
    col = box.states[:, z - box.z0, x - box.x0]
    rk = box.table.to_rule[col]
    passable = t.passable[rk] & (t.cls[rk] != rules.FLUID)
    solid = ~t.passable[rk] & (t.cls[rk] != rules.FLUID)
    air = t.cls[rk] == rules.AIR
    for yi in range(min(top_y - box.y0 + 1, len(col) - 2), 0, -1):
        if solid[yi - 1] and air[yi] and passable[yi + 1]:
            return yi + box.y0
    return top_y + 1


# --------------------------------------------------------------------------------------------------- export run

@dataclass
class Export:
    cfg: dict
    source_kind: str
    world: str
    box: BlockBox
    cols: Columns
    y0: int                 # world y of template y = 0
    height: int
    sea_level_src: int
    markers: list
    entities: list
    dropped_entities: dict
    stats: dict = field(default_factory=dict)


def source_of(cfg: dict, prefer: str | None, origin: list | None = None) -> dict:
    """The source to read: 'workshop' when its world exists (it is the source of truth), else 'original'.
    origin overrides the workshop origin (x, y, z of template space in that world)."""
    kind = prefer or ("workshop" if workshop_present(cfg) else "original")
    if kind == "workshop":
        man = read_manifest(cfg)
        if man is None:
            raise SystemExit("the workshop world exists but the town has no manifest yet; export from the original first")
        ox, oz = (origin[0], origin[2]) if origin else (man["workshop"]["origin"][0], man["workshop"]["origin"][2])
        w, _, d = man["size"]
        oy = origin[1] if origin else man["workshop"]["origin"][1]
        return {"kind": "workshop", "world": cfg["workshop"]["world"], "box": [ox, oz, ox + w - 1, oz + d - 1],
                "origin_y": oy,
                "sea_level": int(cfg["workshop"].get("sea_level", cfg.get("target_sea_level", 63))),
                "district": None, "labels": None}
    o = cfg["original"]
    return {"kind": "original", "world": o["world"], "box": o["box"], "sea_level": int(o["sea_level"]),
            "district": o.get("district"), "labels": o.get("labels")}


def world_is_open(path: str) -> bool:
    """True while Minecraft holds the world's session.lock (it locks the file for as long as the world is open)."""
    lock = os.path.join(path, "session.lock")
    if not os.path.exists(lock):
        return False
    try:
        with open(lock, "rb") as f:
            f.read(1)
        if os.name == "nt":
            import msvcrt
            with open(lock, "r+b") as f:
                msvcrt.locking(f.fileno(), msvcrt.LK_NBLCK, 1)
                msvcrt.locking(f.fileno(), msvcrt.LK_UNLCK, 1)
        return False
    except OSError:
        return True


def read_manifest(cfg: dict) -> dict | None:
    _, tdir, _ = resource_dirs(cfg)
    p = os.path.join(tdir, "manifest.json")
    if not os.path.exists(p):
        return None
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def build(cfg: dict, prefer: str | None = None, world_path: str | None = None, log=print,
          origin: list | None = None) -> Export:
    src = source_of(cfg, prefer, origin)
    wpath = resolve(world_path or src["world"])
    x0, z0, x1, z1 = src["box"]
    if src["kind"] == "workshop" and world_is_open(wpath):
        raise SystemExit(f"{wpath} is open in Minecraft. Use Save and Quit to Title (or close the game) first: "
                         "the game writes its region files when it saves, so an open world reads stale.")
    log(f"reading {src['kind']} world {wpath}, columns x {x0}..{x1}, z {z0}..{z1}")
    w = World(wpath)
    box = w.read_box(x0, z0, x1, z1)
    sea = src["sea_level"]
    owned, foreign = ownership(cfg, box, src["district"], src["labels"])
    erased = erase_foreign(box, foreign, sea)
    cols = analyse_columns(cfg, box, owned, foreign, sea)
    cols.erased = erased
    if src["kind"] == "workshop":
        keep_untouched(cfg, box, cols, src["origin_y"], log)
    stored = cols.zone != BLEND
    y0 = int(cols.floor[stored].min())
    y1 = int(cols.top[stored].max())

    # markers: the ones authored in the world, plus (from the original map only) the seeds in the town config
    mk = []
    old_notes = {m["id"]: m.get("note", "") for m in (read_manifest(cfg) or {}).get("markers", [])}
    for m in box.markers:
        x, y, z = m["pos"]
        note = m.get("note", "")
        # a sign shows only the start of a long note: keep the full note when the sign still matches it
        full = old_notes.get(m["id"], "")
        if full and full.replace(" ", "").startswith(note.replace(" ", "")):
            note = full
        mk.append({"id": m["id"], "pos": [x - x0, y - y0, z - z0], "yaw": m["yaw"], "authored_as": m["authored_as"],
                   **({"note": note} if note else {})})
    if src["kind"] == "original":
        for s in cfg.get("seed_markers", []):
            x, z = s["at"]
            zi, xi = z - z0, x - x0
            y = s.get("y") or standing_spot(box, x, z, int(cols.top[zi, xi]) if stored[zi, xi] else y1)
            mk.append({"id": s["id"], "pos": [x - x0, y - y0, z - z0], "yaw": float(s.get("yaw", 0)),
                       "authored_as": "seed", **({"note": s["note"]} if s.get("note") else {})})
    ids = [m["id"] for m in mk]
    dup = {i for i in ids if ids.count(i) > 1}
    if dup:
        log(f"WARNING: duplicate marker ids {sorted(dup)}; keeping the first of each")
        seen, mk2 = set(), []
        for m in mk:
            if m["id"] not in seen:
                seen.add(m["id"])
                mk2.append(m)
        mk = mk2
    mk.sort(key=lambda m: m["id"])

    keep, dropped = [], {}
    for e in w.entities_in(x0, z0, x1, z1):
        eid = str(e["id"].value)
        p = [v.value for v in e["Pos"].value[1]]
        xi, zi = int(np.floor(p[0])) - x0, int(np.floor(p[2])) - z0
        if eid in DECORATIVE_ENTITIES and stored[zi, xi] and y0 <= p[1] <= y1 + 1 and not foreign[zi, xi]:
            keep.append(e)
        else:
            dropped[eid] = dropped.get(eid, 0) + 1
    ex = Export(cfg, src["kind"], wpath, box, cols, y0, y1 - y0 + 1, sea, mk, keep, dropped)
    ex.stats = {
        "columns": {ZONE_NAMES[k]: int((cols.zone == k).sum()) for k in (MOLD, SUPPORT, SEA, BLEND)},
        "foreign_blocks_erased": erased,
        "foreign_columns": int(foreign.sum()),
    }
    return ex


def stored_blocks(ex: Export):
    """(y, z, x) index arrays of every stored block (box-relative), in y, z, x order."""
    cols = ex.cols
    ny = ex.box.shape[0]
    yi = np.arange(ny)[:, None, None] + ex.box.y0
    mask = (cols.zone != BLEND)[None] & (yi >= cols.floor[None]) & (yi <= cols.top[None])
    return mask


def write(ex: Export, version: int | None = None, log=print, root: str | None = None) -> dict:
    cfg = ex.cfg
    sdir, tdir, sid = resource_dirs(cfg, version, root)
    os.makedirs(sdir, exist_ok=True)
    os.makedirs(tdir, exist_ok=True)
    for f in os.listdir(sdir):
        if f.startswith("tile_") and f.endswith(".nbt"):
            os.remove(os.path.join(sdir, f))
    box, cols = ex.box, ex.cols
    ny, D, W = box.shape
    tile = int(cfg.get("tile", 32))
    mask = stored_blocks(ex)
    oy = ex.y0 - box.y0          # box index of template y = 0
    H = ex.height
    keys = box.table.keys
    tiles = []
    total_bytes = 0
    total_blocks = 0
    for tj in range((D + tile - 1) // tile):
        for ti in range((W + tile - 1) // tile):
            xs, zs = slice(ti * tile, min(W, ti * tile + tile)), slice(tj * tile, min(D, tj * tile + tile))
            m = mask[oy:oy + H, zs, xs]
            if not m.any():
                continue
            yy, zz, xx = np.nonzero(m)
            st = box.states[oy:oy + H, zs, xs][m]
            pal, inv = template.palette_of(st, keys)
            pos = np.stack([xx, yy, zz], 1)
            bnbt = {}
            index = {(int(a), int(b), int(c)): r for r, (a, b, c) in enumerate(pos)}
            for (x, y, z), be in box.block_entities.items():
                rx, ry, rz = x - box.x0 - ti * tile, y - ex.y0, z - box.z0 - tj * tile
                r = index.get((rx, ry, rz))
                if r is None:
                    continue
                v = {k: vv for k, vv in be.value.items() if k not in ("x", "y", "z", "keepPacked")}
                bnbt[r] = T(nbt.TAG_COMPOUND, v)
            ents = []
            for e in ex.entities:
                p = [v.value for v in e["Pos"].value[1]]
                rx, rz = p[0] - box.x0 - ti * tile, p[2] - box.z0 - tj * tile
                if not (0 <= rx < xs.stop - xs.start and 0 <= rz < zs.stop - zs.start):
                    continue
                bp = e.get("block_pos")
                bpos = ([int(bp.value[0]), int(bp.value[1]), int(bp.value[2])] if bp is not None and bp.tag == nbt.TAG_INT_ARRAY
                        else [int(np.floor(p[0])), int(np.floor(p[1])), int(np.floor(p[2]))])
                rel_b = [bpos[0] - box.x0 - ti * tile, bpos[1] - ex.y0, bpos[2] - box.z0 - tj * tile]
                ev = {k: vv for k, vv in e.value.items() if k not in ("UUID", "Pos")}
                if "block_pos" in ev:   # vanilla overwrites it on placement; relative keeps the file world-independent
                    ev["block_pos"] = nbt.int_array(rel_b)
                ents.append(([rx, p[1] - ex.y0, rz], rel_b, T(nbt.TAG_COMPOUND, ev)))
            size = [xs.stop - xs.start, H, zs.stop - zs.start]
            data = template.encode(size, pal, pos, inv, bnbt, ents, box.data_version)
            name = f"tile_{ti}_{tj}"
            nbytes = template.write(os.path.join(sdir, name + ".nbt"), data)
            total_bytes += nbytes
            total_blocks += len(st)
            tiles.append({"id": sid + name, "offset": [ti * tile, 0, tj * tile], "size": size, "blocks": int(len(st)),
                          "non_air": int(np.array([not k.endswith(":air") for k in pal])[inv].sum()),
                          "block_entities": len(bnbt), "entities": len(ents), "bytes": nbytes})
    log(f"wrote {len(tiles)} tiles, {total_blocks:,} blocks, {total_bytes / 1e6:.1f} MB")
    columns_bytes = write_columns(ex, os.path.join(tdir, "columns.nbt"))
    man = manifest(ex, tiles, sid, version, total_bytes, columns_bytes)
    with open(os.path.join(tdir, "manifest.json"), "w", encoding="utf-8", newline="\n") as f:
        f.write(dump_manifest(man))
    return man


def write_columns(ex: Export, path: str) -> int:
    """The terrain mask, one entry per column (index z * width + x, template space): zone, floor, top, ground."""
    c = ex.cols
    D, W = c.zone.shape
    rel = lambda a: (np.where(c.zone != BLEND, a - ex.y0, -1)).astype(">i4").reshape(-1)
    root = nbt.c(
        format=nbt.i(1), width=nbt.i(W), depth=nbt.i(D),
        zones=nbt.s("0 blend (not stored), 1 mold, 2 support, 3 sea"),
        zone=nbt.byte_array(c.zone.reshape(-1).astype(np.int8)),
        floor=nbt.int_array(rel(c.floor)), top=nbt.int_array(rel(c.top)),
        ground=nbt.int_array(np.where(c.zone != BLEND, c.ground - ex.y0, -1).astype(">i4").reshape(-1)),
    )
    return template.write(path, nbt.root_bytes(root))


def edge_list(ex: Export) -> list:
    """Columns on the edge of the stored area (a 4-neighbour is not stored, or the box ends there):
    [x, z, ground y, top y, zone] in template space. Phase 3 blends generated terrain toward these."""
    c = ex.cols
    st = c.zone != BLEND
    p = np.pad(st, 1, constant_values=False)
    inner = p[:-2, 1:-1] & p[2:, 1:-1] & p[1:-1, :-2] & p[1:-1, 2:]
    edge = st & ~inner
    zz, xx = np.nonzero(edge)
    return [[int(x), int(z), int(c.ground[z, x] - ex.y0), int(c.top[z, x] - ex.y0), ZONE_NAMES[int(c.zone[z, x])]]
            for z, x in zip(zz, xx)]


def manifest(ex: Export, tiles: list, sid: str, version, total_bytes: int, columns_bytes: int) -> dict:
    cfg = ex.cfg
    c = ex.cols
    D, W = c.zone.shape
    st = c.zone != BLEND
    zz, xx = np.nonzero(st)
    sea_t = ex.sea_level_src - ex.y0
    target = int(cfg.get("target_sea_level", 63))
    land = c.zone == MOLD
    ground_land = c.ground[(c.zone == SUPPORT) | land] - ex.y0
    old = read_manifest(cfg) or {}
    if ex.source_kind == "original":
        sx, sz = cfg["original"]["box"][:2]
        # x/z keep the original's chunk alignment; y puts the original's sea level at the workshop's
        workshop_origin = [sx & 15, ex.y0 + (target - ex.sea_level_src), sz & 15]
        original = {"world": cfg["original"]["world"], "box_min": [sx, ex.y0, sz], "sea_level": ex.sea_level_src}
    else:
        wo = old.get("workshop", {}).get("origin", [0, 0, 0])
        workshop_origin = [wo[0], ex.y0, wo[2]]
        original = old.get("original")
    joins = []
    for j in cfg.get("joins", []):
        a, b = j["along"]
        if j["side"] in ("north", "south"):
            a, b = a - cfg["original"]["box"][0], b - cfg["original"]["box"][0]
        else:
            a, b = a - cfg["original"]["box"][1], b - cfg["original"]["box"][1]
        joins.append({**j, "along": [max(0, a), min((W if j["side"] in ("north", "south") else D) - 1, b)]})
    return {
        "format": 1,
        "id": cfg["id"],
        "version": int(version or cfg["version"]),
        "name": cfg["name"],
        "shipped": bool(cfg.get("shipped", False)),
        "about": "Made by tools/buildimport (python -m buildimport export). Template space: x east, z south, "
                 "origin (0,0,0) = the box's north-west corner at its lowest stored y. Rotation turns the whole "
                 "town about rotation_pivot; every tile shares the town's origin (tile.offset is its corner).",
        "source": {"kind": ex.source_kind, "world": cfg[ex.source_kind]["world"],
                   "box_min": [ex.box.x0, ex.y0, ex.box.z0], "sea_level": ex.sea_level_src,
                   "data_version": ex.box.data_version,
                   "exported": datetime.date.today().isoformat()},
        "original": original,
        "size": [W, ex.height, D],
        "footprint": {
            "width_x": W, "depth_z": D, "chunks_x": (W + 15) // 16, "chunks_z": (D + 15) // 16,
            "stored_bounds": [int(xx.min()), int(zz.min()), int(xx.max()), int(zz.max())],
            "blend_border": int(cfg.get("blend_border", 32)),
        },
        "anchor": {"origin": [0, 0, 0], "rotation_pivot": [W // 2, 0, D // 2],
                   "note": "place template y = 0 at world y = target_sea_level - sea_level"},
        "levels": {"sea_level": sea_t, "target_sea_level": target, "y_offset": target - sea_t,
                   "ground_median": int(np.median(ground_land)) if len(ground_land) else sea_t,
                   "ground_range": [int(ground_land.min()), int(ground_land.max())] if len(ground_land) else [sea_t, sea_t],
                   "top_max": int(c.top[st].max() - ex.y0)},
        "waterfront": {"side": cfg.get("waterfront", "east"), "required": True,
                       **waterfront_info(ex)},
        "joins": joins,
        "zones": {"columns": f"data/inhabited_world/{cfg['resource_path']}/v{version or cfg['version']}/columns.nbt",
                  "legend": {"0": "blend: not stored; Phase 3 blends generated terrain here",
                             "1": "mold: the build, stored exactly from floor to top",
                             "2": "support: ground the build stands in, stored from floor to top",
                             "3": "sea: open water at sea level, stored from seabed floor to the surface"},
                  "contract": "above a stored column's top: air. Below its floor: solid ground (the placer fills "
                              "down to existing solid ground). Support depth below terrain: "
                              f"{int(cfg.get('support_depth', 4))} blocks.",
                  "counts": ex.stats["columns"]},
        "blend": {"border": int(cfg.get("blend_border", 32)),
                  "edge_format": "[x, z, ground y, top y, zone]", "edge": edge_list(ex)},
        "tiles": tiles,
        "markers": ex.markers,
        "workshop": {"world": cfg.get("workshop", {}).get("world"), "origin": workshop_origin,
                     "sea_level": int(cfg.get("workshop", {}).get("sea_level", target)),
                     "note": "the workshop world holds template space at this origin; its x/z keep the original's "
                             "chunk alignment so the survey rules classify blocks the same way"},
        "stats": {**ex.stats, "tiles": len(tiles), "blocks": int(sum(t["blocks"] for t in tiles)),
                  "non_air_blocks": int(sum(t["non_air"] for t in tiles)),
                  "block_entities": int(sum(t["block_entities"] for t in tiles)),
                  "entities": int(sum(t["entities"] for t in tiles)),
                  "entities_dropped": ex.dropped_entities,
                  "template_bytes": total_bytes, "columns_bytes": columns_bytes},
    }


def waterfront_info(ex: Export) -> dict:
    """Where the land meets open water on the waterfront side (template space). quay_line: median, over the rows
    that reach the sea, of the first sea column counted from the land side; piers and moored ships stand in the
    sea strip beyond it."""
    c = ex.cols
    side = ex.cfg.get("waterfront", "east")
    if side not in ("east", "west", "north", "south"):
        return {}
    sea = c.zone == SEA
    if side in ("north", "south"):
        sea = sea.T
    if side in ("west", "north"):
        sea = sea[:, ::-1]
    W = sea.shape[1]
    firsts = [int(np.argmax(r)) for r in sea if r.any()]
    if not firsts:
        return {}
    q = int(np.median(firsts))
    quay = q if side in ("east", "south") else W - 1 - q
    return {"quay_line": quay, "sea_strip": W - q, "sea_columns": int((c.zone == SEA).sum())}


def preview(ex: Export, path: str, scale: int = 3) -> None:
    """Top-down picture: block colours, zones tinted, blend columns grey, markers as dots."""
    from PIL import Image, ImageDraw
    from . import colors
    box, c = ex.box, ex.cols
    ny, D, W = box.shape
    st = c.zone != BLEND
    top = np.where(st, c.top, ex.y0 + ex.height - 1) - box.y0
    top = np.clip(top, 0, ny - 1)
    ids = np.take_along_axis(box.states, top[None], 0)[0]
    pal = colors.palette([box.table.rule.keys[int(i)] for i in box.table.to_rule])
    rgb = pal[ids].astype(float)
    tint = {MOLD: (255, 120, 60), SUPPORT: (90, 200, 90), SEA: (60, 120, 255)}
    for z, col in tint.items():
        m = c.zone == z
        rgb[m] = rgb[m] * 0.75 + np.array(col) * 0.25
    rgb[~st] = rgb[~st] * 0.25 + 60
    im = Image.fromarray(rgb.clip(0, 255).astype(np.uint8)).resize((W * scale, D * scale), Image.NEAREST)
    d = ImageDraw.Draw(im)
    for m in ex.markers:
        x, _, z = m["pos"]
        d.ellipse([x * scale - 5, z * scale - 5, x * scale + 5, z * scale + 5], outline=(255, 255, 0), width=2)
        d.text((x * scale + 7, z * scale - 6), m["id"], fill=(255, 255, 0))
    im.save(path)


def dump_manifest(man: dict) -> str:
    """JSON with one line per edge entry / marker / tile, so diffs stay readable."""
    s = json.dumps(man, indent=2)
    import re
    s = re.sub(r"\[\s+(-?\d+),\s+(-?\d+),\s+(-?\d+),\s+(-?\d+),\s+\"(\w+)\"\s+\]",
               r'[\1, \2, \3, \4, "\5"]', s)
    s = re.sub(r"\[\s+(-?[\d.]+),\s+(-?[\d.]+),\s+(-?[\d.]+)\s+\]", r"[\1, \2, \3]", s)
    s = re.sub(r"\[\s+(-?\d+),\s+(-?\d+),\s+(-?\d+),\s+(-?\d+)\s+\]", r"[\1, \2, \3, \4]", s)
    s = re.sub(r"\[\s+(-?\d+),\s+(-?\d+)\s+\]", r"[\1, \2]", s)
    return s + "\n"


def run(town: str, prefer: str | None = None, world: str | None = None, new_version: bool = False, log=print,
        preview_path: str | None = None, origin: list | None = None, out: str | None = None) -> dict:
    cfg = load_town(town)
    version = cfg["version"]
    if cfg.get("shipped") and not new_version:
        raise SystemExit(f"{cfg['id']} v{version} has shipped and must not change (canon 02 section 6). "
                         f"Re-run with --new-version to write v{version + 1}.")
    if new_version:
        version += 1
    if prefer == "original" and workshop_present(cfg):
        log("NOTE: a workshop world exists and is this town's source of truth; exporting from the original map "
            "overwrites the templates with the original city (workshop edits are not read).")
    if out and new_version:
        raise SystemExit("--out and --new-version do not go together")
    ex = build(cfg, prefer, world, log, origin)
    man = write(ex, version, log, out)
    if preview_path:
        preview(ex, preview_path)
        log(f"preview: {preview_path}")
    if new_version and not out:
        cfg2 = {k: v for k, v in cfg.items() if not k.startswith("_")}
        cfg2["version"] = version
        cfg2["shipped"] = False
        with open(cfg["_path"], "w", encoding="utf-8", newline="\n") as f:
            json.dump(cfg2, f, indent=2)
            f.write("\n")
    s = man["stats"]
    log(f"{man['id']} v{man['version']} from the {ex.source_kind} world: size {man['size']}, "
        f"{s['tiles']} tiles, {s['blocks']:,} blocks ({s['non_air_blocks']:,} not air), "
        f"{s['block_entities']} block entities, {s['entities']} entities, {len(man['markers'])} markers, "
        f"{(s['template_bytes'] + s['columns_bytes']) / 1e6:.1f} MB")
    log(f"zones (columns): {s['columns']}; foreign blocks erased: {s['foreign_blocks_erased']:,}")
    if s["entities_dropped"]:
        log(f"entities left out: {s['entities_dropped']}")
    return man
