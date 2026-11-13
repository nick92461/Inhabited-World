"""Scan a world's region files once and reduce every block column to a few numbers.

The full 3D world is far too big to hold (a 2048 x 2560 save is ~2 billion blocks), so the scan streams chunk by
chunk, classifies each block with rules.py, and keeps per column (x, z):

    surface_y     highest non-air block (anything: roofs, trees, water surface)
    surface_id    the block key id there (for the top-down colour map)
    ground_y      highest natural terrain block that is not part of a build (the ground under / around a build)
    water_top     highest fluid block, water_n the number of fluid blocks (water depth, in open water)
    built_top     highest built block, built_bottom lowest built block (the per-column floor cut-off)
    placed_n      placed blocks, built_n built blocks (placed + attached natural)
    interior_n    air inside the build (in room slices, below), rooms_n room slices
    furnish_n, beds, doors, work, lights   counts of those blocks in the column

and one row per ROOM SLICE: a vertical run of air (or of beds, carpets, torches...) at least 2 blocks tall, with a
solid floor under it and a built ceiling over it. Room slices sharing a building and a floor height make a floor of
that building; the survey uses them for storeys, interior area and "empty upper floors".

The result is one compressed .npz file; survey.py and everything after read only that.
"""
from __future__ import annotations

import json
import sys
import time
from dataclasses import dataclass, field

import numpy as np

from . import anvil, rules

NONE = np.int16(-32768)

COLUMN_ARRAYS = {
    "surface_y": np.int16, "surface_id": np.uint16, "ground_y": np.int16, "water_top": np.int16,
    "water_n": np.int16, "built_top": np.int16, "built_bottom": np.int16, "placed_n": np.int16,
    "built_n": np.int16, "interior_n": np.int16, "rooms_n": np.int16, "furnish_n": np.int16, "beds": np.int16,
    "bed_blocks": np.int16,
    "doors": np.int16, "work": np.int16, "lights": np.int16,
}
HEIGHT_ARRAYS = ("surface_y", "ground_y", "water_top", "built_top", "built_bottom")

# chunk provenance codes
CHUNK_MISSING, CHUNK_PROTO, CHUNK_UPGRADED, CHUNK_NEW, CHUNK_LEGACY = 0, 1, 2, 3, 4


class KeyTable:
    """Block key <-> small integer id, with per-id lookup tables built from rules.py."""

    def __init__(self):
        self.keys: list[str] = []
        self.index: dict[str, int] = {}
        self.cls = np.zeros(0, np.uint8)
        self.passable = np.zeros(0, bool)
        self.terrain = np.zeros(0, bool)
        self.bed = np.zeros(0, bool)
        self.bed_any = np.zeros(0, bool)
        self.door = np.zeros(0, bool)
        self.work = np.zeros(0, bool)
        self.paint = np.zeros(0, bool)
        self.id("minecraft:air")

    def id(self, key: str) -> int:
        i = self.index.get(key)
        if i is not None:
            return i
        i = len(self.keys)
        self.keys.append(key)
        self.index[key] = i
        c = rules.classify(key)
        bed, bed_any, door, work = rules.flags(key)
        name = rules.base_name(key)
        terrain = c == rules.NATURAL and not name.endswith("_log")
        self.cls = np.append(self.cls, np.uint8(c))
        self.passable = np.append(self.passable, rules.is_passable(key))
        self.terrain = np.append(self.terrain, terrain)
        self.bed = np.append(self.bed, bed)
        self.bed_any = np.append(self.bed_any, bed_any)
        self.door = np.append(self.door, door)
        self.work = np.append(self.work, work)
        self.paint = np.append(self.paint, rules.is_paint(key))
        return i


def _top(mask: np.ndarray) -> np.ndarray:
    """Highest index along axis 0 where mask is true, -1 where none."""
    any_ = mask.any(0)
    return np.where(any_, mask.shape[0] - 1 - np.argmax(mask[::-1], 0), -1)


def _bottom(mask: np.ndarray) -> np.ndarray:
    any_ = mask.any(0)
    return np.where(any_, np.argmax(mask, 0), -1)


@dataclass
class ChunkResult:
    columns: dict            # name -> (16*16,) array, column index = z*16 + x
    runs: np.ndarray         # (n, 6) int16: local col, floor_y, height, furnish, lights, ceiling_y


@dataclass
class Classified:
    """Per-block classes of one chunk volume (y, 256), after the terrain-paint and attached rules."""
    cls: np.ndarray       # rules class per block (terrain paint already turned NATURAL)
    fluid: np.ndarray
    placed: np.ndarray    # PLACED / FURNISHING / LIGHT
    natural: np.ndarray
    terrain: np.ndarray   # natural ground (not tree trunks): what "the ground" is made of
    built: np.ndarray     # part of a build: placed, or natural attached by rule A or B


def classify_volume(vol: np.ndarray, keys: KeyTable) -> Classified:
    """Apply rules.py to one chunk's blocks. vol is (y, 256) of KeyTable ids, column index z*16 + x.

    The survey and the exporter both call this, so a block is "built" for the export exactly when the survey
    counted it as built. Rule B uses a per-chunk ground level, so results depend on chunk boundaries: exports keep
    the source's chunk alignment."""
    ny = vol.shape[0]
    cls = keys.cls[vol].copy()
    fluid = cls == rules.FLUID
    _terrain_paint(cls, keys.paint[vol], fluid)
    placed = cls >= rules.PLACED
    natural = cls == rules.NATURAL
    yidx = np.arange(ny)[:, None]

    # rule A: natural at or above the lowest placed block of its column
    low_placed = _bottom(placed)
    rule_a = natural & (low_placed[None, :] >= 0) & (yidx >= low_placed[None, :])
    # rule B: natural, face-adjacent to placed, and above the chunk's open ground
    p3 = placed.reshape(ny, 16, 16)
    adj = np.zeros_like(p3)
    adj[1:] |= p3[:-1]
    adj[:-1] |= p3[1:]
    adj[:, 1:] |= p3[:, :-1]
    adj[:, :-1] |= p3[:, 1:]
    adj[:, :, 1:] |= p3[:, :, :-1]
    adj[:, :, :-1] |= p3[:, :, 1:]
    adj = adj.reshape(ny, 256)
    terrain = keys.terrain[vol] | (natural & keys.paint[vol])
    open_cols = low_placed < 0
    nat_top = _top(terrain)
    if open_cols.any() and (nat_top[open_cols] >= 0).any():
        chunk_ground = np.median(nat_top[open_cols & (nat_top >= 0)])
    elif (low_placed >= 0).any():
        chunk_ground = np.median(low_placed[low_placed >= 0]) - 1
    else:
        chunk_ground = -1
    rule_b = natural & adj & (yidx > chunk_ground + rules.ATTACH_MIN_ABOVE_GROUND)
    built = placed | rule_a | rule_b
    return Classified(cls, fluid, placed, natural, terrain, built)


def analyse_chunk(chunk: anvil.Chunk, keys: KeyTable) -> ChunkResult | None:
    """Classify one chunk's blocks and reduce them to column statistics and room slices."""
    secs = [s for s in chunk.sections]
    if not secs:
        return None
    sy0 = min(s.y for s in secs)
    sy1 = max(s.y for s in secs)
    ny = (sy1 - sy0 + 1) * 16
    ybase = sy0 * 16
    vol = np.zeros((ny, 256), np.uint16)  # air id is 0
    any_block = False
    for s in secs:
        pal = np.fromiter((keys.id(k) for k in s.palette), np.uint16, len(s.palette))
        r0 = (s.y - sy0) * 16
        if s.indices is None:
            if pal[0] != 0:
                vol[r0:r0 + 16] = pal[0]
                any_block = True
        else:
            vol[r0:r0 + 16] = pal[s.indices.astype(np.intp)].reshape(16, 256)
            any_block = True
    if not any_block:
        return None

    k = classify_volume(vol, keys)
    cls, fluid, placed, natural, terrain, built = k.cls, k.fluid, k.placed, k.natural, k.terrain, k.built
    nonair = cls != rules.AIR
    yidx = np.arange(ny)[:, None]

    passable = keys.passable[vol]
    floor_ok = ~passable & ~fluid
    ceiling_ok = built & ~passable
    furn = cls == rules.FURNISHING
    light = cls == rules.LIGHT

    surface = _top(nonair)
    col = np.arange(256)
    cols = {
        "surface_y": np.where(surface >= 0, surface + ybase, NONE),
        "surface_id": np.where(surface >= 0, vol[np.maximum(surface, 0), col], 0),
        "ground_y": _shift(_top(terrain & ~built), ybase),
        "water_top": _shift(_top(fluid), ybase),
        "water_n": fluid.sum(0),
        "built_top": _shift(_top(built), ybase),
        "built_bottom": _shift(_bottom(built), ybase),
        "placed_n": placed.sum(0),
        "built_n": built.sum(0),
        "furnish_n": furn.sum(0),
        "beds": (keys.bed[vol] & built).sum(0),
        "bed_blocks": (keys.bed_any[vol] & built).sum(0),
        "doors": (keys.door[vol]).sum(0),
        "work": (keys.work[vol]).sum(0),
        "lights": light.sum(0),
    }

    # room slices
    room = passable & ~fluid
    has_built = built.any(0)
    room &= has_built[None, :]
    pad = np.zeros((ny + 2, 256), np.int8)
    pad[1:-1] = room
    d = np.diff(pad, axis=0)
    sc, sy = np.nonzero(d.T == 1)
    ec, ey = np.nonzero(d.T == -1)
    runs = np.zeros((0, 6), np.int16)
    if len(sc):
        h = ey - sy
        ok = (sy >= 1) & (ey <= ny - 1) & (h >= 2)
        sy, ey, sc, h = sy[ok], ey[ok], sc[ok], h[ok]
        ok = floor_ok[sy - 1, sc] & ceiling_ok[np.minimum(ey, ny - 1), sc]
        sy, ey, sc, h = sy[ok], ey[ok], sc[ok], h[ok]
        fz = np.zeros((ny + 1, 256), np.int32)
        np.cumsum(furn, axis=0, out=fz[1:])
        lz = np.zeros((ny + 1, 256), np.int32)
        np.cumsum(light, axis=0, out=lz[1:])
        f = fz[ey, sc] - fz[sy, sc]
        li = lz[ey, sc] - lz[sy, sc]
        runs = np.stack([sc, sy + ybase, h, f, li, ey + ybase], axis=1).astype(np.int16)
    interior = np.bincount(runs[:, 0], weights=runs[:, 2], minlength=256) if len(runs) else np.zeros(256)
    nrooms = np.bincount(runs[:, 0], minlength=256) if len(runs) else np.zeros(256)
    cols["interior_n"] = interior
    cols["rooms_n"] = nrooms
    return ChunkResult(cols, runs)


def _terrain_paint(cls: np.ndarray, paint: np.ndarray, fluid: np.ndarray) -> None:
    """Rule C (rules.py): reclassify terrain-paint blocks as NATURAL in place. cls is (y, 256)."""
    paint = paint & (cls >= rules.PLACED)
    if not paint.any():
        return
    other = (cls >= rules.PLACED) & ~paint
    # any non-paint placed block above (exclusive), per cell
    above_other = np.zeros_like(other)
    above_other[:-1] = np.maximum.accumulate(other[::-1], axis=0)[::-1][1:]
    above_fluid = np.zeros_like(fluid)
    above_fluid[:-1] = np.maximum.accumulate(fluid[::-1], axis=0)[::-1][1:]
    natural = cls == rules.NATURAL
    cand = paint & ~above_other
    c1 = cand & above_fluid
    # C2: thin layer on natural ground; run position counted upward from the natural block below the run
    pos = np.zeros(cls.shape, np.int16)
    ny = cls.shape[0]
    for y in range(1, ny):
        pos[y] = np.where(paint[y], np.where(natural[y - 1], 1, np.where(pos[y - 1] > 0, pos[y - 1] + 1, 0)), 0)
    solid = (cls != rules.AIR) & (cls != rules.FLUID) & (cls != rules.VEGETATION)
    s3 = solid.reshape(ny, 16, 16)
    nb = np.zeros(s3.shape, np.int8)
    nb[:, 1:] += s3[:, :-1]
    nb[:, :-1] += s3[:, 1:]
    nb[:, :, 1:] += s3[:, :, :-1]
    nb[:, :, :-1] += s3[:, :, 1:]
    nb = nb.reshape(ny, 256)
    # the whole run must be thin: take the run's top position as its thickness
    thick = pos.copy()
    for y in range(ny - 2, -1, -1):
        thick[y] = np.where((pos[y] > 0) & (pos[y + 1] == pos[y] + 1), thick[y + 1], pos[y])
    c2 = cand & (pos > 0) & (thick <= rules.PAINT_MAX_THICKNESS) & (nb >= 3)
    c3 = np.zeros_like(cand)
    c3[1:-1] = cand[1:-1] & natural[:-2] & natural[2:]
    cls[c1 | c2 | c3] = rules.NATURAL


def _shift(top: np.ndarray, ybase: int) -> np.ndarray:
    return np.where(top >= 0, top + ybase, NONE)


@dataclass
class ScanResult:
    x0: int
    z0: int
    columns: dict = field(default_factory=dict)
    runs: np.ndarray | None = None   # (n, 7) int32: x, z, floor_y, height, furnish, lights, ceiling_y
    chunk_kind: np.ndarray | None = None
    keys: list = field(default_factory=list)
    meta: dict = field(default_factory=dict)

    def save(self, path):
        arrays = {k: v for k, v in self.columns.items()}
        np.savez_compressed(path, runs=self.runs, chunk_kind=self.chunk_kind,
                            keys=np.array(json.dumps(self.keys)),
                            meta=np.array(json.dumps({**self.meta, "x0": self.x0, "z0": self.z0})), **arrays)

    @staticmethod
    def load(path) -> "ScanResult":
        z = np.load(path)
        meta = json.loads(str(z["meta"]))
        r = ScanResult(meta["x0"], meta["z0"])
        r.meta = meta
        r.keys = json.loads(str(z["keys"]))
        r.runs = z["runs"]
        r.chunk_kind = z["chunk_kind"]
        r.columns = {k: z[k] for k in COLUMN_ARRAYS}
        return r


def scan(region_dir, log=print) -> ScanResult:
    regions = anvil.region_files(region_dir)
    if not regions:
        raise SystemExit(f"no region files in {region_dir}")
    rx0 = min(r[0] for r in regions)
    rx1 = max(r[0] for r in regions)
    rz0 = min(r[1] for r in regions)
    rz1 = max(r[1] for r in regions)
    x0, z0 = rx0 * 512, rz0 * 512
    w, h = (rx1 - rx0 + 1) * 512, (rz1 - rz0 + 1) * 512
    res = ScanResult(x0, z0)
    for name, dt in COLUMN_ARRAYS.items():
        fill = NONE if name in HEIGHT_ARRAYS else 0
        res.columns[name] = np.full((h, w), fill, dt)
    res.chunk_kind = np.zeros((h // 16, w // 16), np.uint8)
    keys = KeyTable()
    run_parts = []
    versions: dict[int, int] = {}
    t0 = time.time()
    nchunks = 0
    for i, (rx, rz, path) in enumerate(regions):
        for _, _, raw in anvil.read_region(path):
            chunk = anvil.parse_chunk(raw)
            nchunks += 1
            versions[chunk.data_version] = versions.get(chunk.data_version, 0) + 1
            ci, cj = chunk.cz - z0 // 16, chunk.cx - x0 // 16
            if not (0 <= ci < h // 16 and 0 <= cj < w // 16):
                continue
            if chunk.data_version < anvil.FLATTENING:
                kind = CHUNK_LEGACY
            elif chunk.status not in ("minecraft:full", "full"):
                kind = CHUNK_PROTO
            else:
                kind = CHUNK_UPGRADED if chunk.blending else CHUNK_NEW
            res.chunk_kind[ci, cj] = kind
            if kind == CHUNK_PROTO:
                continue
            out = analyse_chunk(chunk, keys)
            if out is None:
                continue
            zs, xs = ci * 16, cj * 16
            for name, arr in out.columns.items():
                res.columns[name][zs:zs + 16, xs:xs + 16] = np.asarray(arr).reshape(16, 16)
            if len(out.runs):
                r = out.runs.astype(np.int32)
                lc = r[:, 0]
                xz = np.stack([x0 + xs + lc % 16, z0 + zs + lc // 16], axis=1)
                run_parts.append(np.concatenate([xz, r[:, 1:]], axis=1))
        log(f"  region {i + 1}/{len(regions)} r.{rx}.{rz}  {nchunks} chunks, {len(keys.keys)} block keys, "
            f"{time.time() - t0:.0f}s")
    res.runs = np.concatenate(run_parts) if run_parts else np.zeros((0, 7), np.int32)
    res.keys = keys.keys
    res.meta = {"region_dir": str(region_dir), "chunks": nchunks, "data_versions": versions,
                "seconds": round(time.time() - t0, 1)}
    return res


if __name__ == "__main__":
    r = scan(sys.argv[1])
    r.save(sys.argv[2])
