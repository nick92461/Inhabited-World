"""Full-fidelity, read-only access to a world's chunks for the exporter.

The survey (scan.py) reduces blocks to a few classes and drops most block properties. The exporter needs every
block exactly: the full block state (all properties), block entities (chest contents, sign text, banners...) and
the entities stored in the separate `entities/` region files (item frames, paintings, armor stands).

    w = World(path)
    box = w.read_box(x0, z0, x1, z1)      # BlockBox: every block in those columns, all heights
    ents = w.entities_in(x0, z0, x1, z1)  # list of typed entity compounds

Nothing here writes: region files are opened read-only.
"""
from __future__ import annotations

import os
from dataclasses import dataclass, field

import numpy as np

from . import anvil, markers, nbt
from .scan import KeyTable, classify_volume, Classified

T = nbt.T


def state_key(entry) -> str:
    """Canonical full block-state key, 'minecraft:oak_stairs[facing=east,half=bottom,...]' (properties sorted).

    entry is a palette entry in plain form: a bare string (26.x: the block's default state), {id, properties}
    (26.x, any other state), {Name, Properties} (1.13-1.21), or a 26.x mixed-list wrapper {"": one of those}."""
    if isinstance(entry, dict) and len(entry) == 1 and "" in entry:
        entry = entry[""]
    if isinstance(entry, str):
        return entry if ":" in entry else "minecraft:" + entry
    name = entry.get("Name") or entry.get("id") or "minecraft:air"   # 26.x: {id, properties}
    if ":" not in name:
        name = "minecraft:" + name
    props = entry.get("Properties") or entry.get("properties") or {}
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={props[k]}" for k in sorted(props)) + "]"


def split_state(key: str) -> tuple[str, dict]:
    """'minecraft:x[a=1,b=2]' -> ('minecraft:x', {'a': '1', 'b': '2'})"""
    i = key.find("[")
    if i < 0:
        return key, {}
    props = {}
    for kv in key[i + 1:-1].split(","):
        k, v = kv.split("=", 1)
        props[k] = v
    return key[:i], props


def rule_key(full_key: str) -> str:
    """The reduced key rules.py classifies (anvil.block_key: only the few properties the rules look at)."""
    name, props = split_state(full_key)
    return anvil.block_key({"Name": name, "Properties": props})


class StateTable:
    """Full block-state key <-> id, plus the matching KeyTable (rule key) id for classification."""

    def __init__(self):
        self.keys: list[str] = []
        self.index: dict[str, int] = {}
        self.rule = KeyTable()
        self.to_rule = np.zeros(0, np.uint16)
        self.id("minecraft:air")

    def id(self, key: str) -> int:
        i = self.index.get(key)
        if i is not None:
            return i
        i = len(self.keys)
        self.keys.append(key)
        self.index[key] = i
        self.to_rule = np.append(self.to_rule, np.uint16(self.rule.id(rule_key(key))))
        return i


def find_dimension_dir(path: str) -> str:
    """The overworld folder holding region/ (and entities/): 26.x 'dimensions/minecraft/overworld', or the world
    root for older layouts."""
    for sub in ("dimensions/minecraft/overworld", ""):
        p = os.path.join(path, sub)
        r = os.path.join(p, "region")
        if os.path.isdir(r) and any(n.endswith(".mca") for n in os.listdir(r)):
            return p
    raise SystemExit(f"no overworld region files under {path}")


class RegionFiles:
    """Random access to the chunks of one region folder (region/ or entities/)."""

    def __init__(self, folder: str):
        self.folder = folder
        self._cache: dict[tuple[int, int], dict] = {}

    def _region(self, rx: int, rz: int) -> dict:
        key = (rx, rz)
        if key not in self._cache:
            path = os.path.join(self.folder, f"r.{rx}.{rz}.mca")
            chunks = {}
            if os.path.exists(path):
                for lx, lz, raw in anvil.read_region(path):
                    chunks[(lx, lz)] = raw
            self._cache[key] = chunks
        return self._cache[key]

    def raw(self, cx: int, cz: int) -> bytes | None:
        return self._region(cx >> 5, cz >> 5).get((cx & 31, cz & 31))


@dataclass
class BlockBox:
    """Every block of a column-aligned box. Arrays are indexed [y - y0, z - z0, x - x0]."""
    x0: int
    z0: int
    y0: int
    states: np.ndarray                    # uint16 ids into table.keys
    table: StateTable
    built: np.ndarray                     # bool, rules.py "part of a build"
    cls: np.ndarray                       # uint8 rules class
    terrain: np.ndarray                   # bool, natural ground
    block_entities: dict = field(default_factory=dict)   # (x, y, z) world -> typed compound (with id, x, y, z)
    data_version: int = 0
    markers: list = field(default_factory=list)          # markers taken out of the blocks: {id, yaw, pos (world)}

    @property
    def shape(self):
        return self.states.shape

    def key_at(self, x, y, z) -> str:
        return self.table.keys[int(self.states[y - self.y0, z - self.z0, x - self.x0])]


class World:
    def __init__(self, path: str):
        self.path = path
        self.dim = find_dimension_dir(path)
        self.regions = RegionFiles(os.path.join(self.dim, "region"))
        self.entity_regions = RegionFiles(os.path.join(self.dim, "entities"))

    def chunk(self, cx: int, cz: int) -> T | None:
        raw = self.regions.raw(cx, cz)
        return None if raw is None else nbt.parse_typed(raw)

    def read_box(self, x0: int, z0: int, x1: int, z1: int, table: StateTable | None = None,
                 y_range: tuple[int, int] | None = None, take_markers: bool = True) -> BlockBox:
        """All blocks of columns x0..x1, z0..z1 (inclusive), classified chunk by chunk with the survey's rules.

        Classification always sees whole chunks (rule B uses a per-chunk ground level), so a box that cuts a chunk
        still gets the same classes the survey gave those blocks."""
        table = table or StateTable()
        cx0, cx1, cz0, cz1 = x0 >> 4, x1 >> 4, z0 >> 4, z1 >> 4
        parsed = {}
        ylo, yhi = 10 ** 9, -10 ** 9
        dv = 0
        for cz in range(cz0, cz1 + 1):
            for cx in range(cx0, cx1 + 1):
                ch = self.chunk(cx, cz)
                if ch is None:
                    continue
                secs = [s for s in ch.get("sections", T(nbt.TAG_LIST, (0, []))).value[1]
                        if s.get("block_states") is not None and s["block_states"].get("palette") is not None]
                if not secs:
                    continue
                dv = max(dv, int(ch.get("DataVersion", T(3, 0)).value))
                parsed[(cx, cz)] = (ch, secs)
                ys = [int(s["Y"].value) for s in secs]
                ylo, yhi = min(ylo, min(ys) * 16), max(yhi, max(ys) * 16 + 15)
        if not parsed:
            raise SystemExit(f"no chunks with blocks in x {x0}..{x1}, z {z0}..{z1} of {self.path}")
        if y_range:
            ylo, yhi = y_range
        ny = yhi - ylo + 1
        W, D = x1 - x0 + 1, z1 - z0 + 1
        states = np.zeros((ny, D, W), np.uint16)
        built = np.zeros((ny, D, W), bool)
        cls = np.zeros((ny, D, W), np.uint8)
        terrain = np.zeros((ny, D, W), bool)
        bes = {}
        marker_list = []
        for (cx, cz), (ch, secs) in parsed.items():
            sy0 = min(int(s["Y"].value) for s in secs)
            sy1 = max(int(s["Y"].value) for s in secs)
            cny = (sy1 - sy0 + 1) * 16
            vol = np.zeros((cny, 256), np.uint16)
            for s in secs:
                bs = s["block_states"]
                pal = [state_key(e.plain() if isinstance(e, T) else e) for e in bs["palette"].value[1]]
                ids = np.fromiter((table.id(k) for k in pal), np.uint16, len(pal))
                r0 = (int(s["Y"].value) - sy0) * 16
                data = bs.get("data")
                if len(ids) == 1 or data is None or not len(data.value):
                    vol[r0:r0 + 16] = ids[0]
                else:
                    idx = anvil.unpack_indices(data.value, len(ids))
                    vol[r0:r0 + 16] = ids[idx.astype(np.intp)].reshape(16, 256)
            taken = set()
            if take_markers:
                for be in ch.get("block_entities", T(nbt.TAG_LIST, (0, []))).value[1]:
                    x, y, z = int(be["x"].value), int(be["y"].value), int(be["z"].value)
                    ly = y - sy0 * 16
                    if not (0 <= ly < cny):
                        continue
                    ci = (z - cz * 16) * 16 + (x - cx * 16)
                    key = table.keys[int(vol[ly, ci])]
                    m = markers.marker_of(be, key)
                    if m is None:
                        continue
                    vol[ly, ci] = table.id("minecraft:water[level=0]" if markers.is_waterlogged(key) else "minecraft:air")
                    taken.add((x, y, z))
                    if x0 <= x <= x1 and z0 <= z <= z1:
                        marker_list.append({**m, "pos": [x, y, z], "block": key})
            k: Classified = classify_volume(table.to_rule[vol], table.rule)
            # copy the part of this chunk inside the box
            bx0, bz0 = cx * 16, cz * 16
            lx0, lx1 = max(x0, bx0) - bx0, min(x1, bx0 + 15) - bx0
            lz0, lz1 = max(z0, bz0) - bz0, min(z1, bz0 + 15) - bz0
            cy0 = sy0 * 16
            ya, yb = max(ylo, cy0), min(yhi, cy0 + cny - 1)
            if ya > yb:
                continue
            sl_c = slice(ya - cy0, yb - cy0 + 1)
            sl_b = slice(ya - ylo, yb - ylo + 1)
            for name, src, dst in (("s", vol, states), ("b", k.built, built), ("c", k.cls, cls),
                                   ("t", k.terrain, terrain)):
                v = src[sl_c].reshape(-1, 16, 16)[:, lz0:lz1 + 1, lx0:lx1 + 1]
                dst[sl_b, bz0 + lz0 - z0:bz0 + lz1 - z0 + 1, bx0 + lx0 - x0:bx0 + lx1 - x0 + 1] = v
            for be in ch.get("block_entities", T(nbt.TAG_LIST, (0, []))).value[1]:
                x, y, z = int(be["x"].value), int(be["y"].value), int(be["z"].value)
                if x0 <= x <= x1 and z0 <= z <= z1 and ylo <= y <= yhi and (x, y, z) not in taken:
                    bes[(x, y, z)] = be
        return BlockBox(x0, z0, ylo, states, table, built, cls, terrain, bes, dv, marker_list)

    def entities_in(self, x0: int, z0: int, x1: int, z1: int) -> list[T]:
        """Typed compounds of every entity whose position lies in columns x0..x1, z0..z1."""
        out = []
        for cz in range(z0 >> 4, (z1 >> 4) + 1):
            for cx in range(x0 >> 4, (x1 >> 4) + 1):
                raw = self.entity_regions.raw(cx, cz)
                if raw is None:
                    continue
                root = nbt.parse_typed(raw)
                for e in root.get("Entities", T(nbt.TAG_LIST, (0, []))).value[1]:
                    pos = [p.value for p in e["Pos"].value[1]]
                    if x0 <= pos[0] < x1 + 1 and z0 <= pos[2] < z1 + 1:
                        out.append(e)
        return out
