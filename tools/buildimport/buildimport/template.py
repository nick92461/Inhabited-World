"""Vanilla structure template files (.nbt), the format StructureTemplateManager loads.

    {
      DataVersion: int,
      size:     [x, y, z] (int list),
      palette:  [ {id: "minecraft:oak_stairs", properties: {facing: "east", ...}}, ... ]   (26.x keys; 1.x used Name/Properties)
      blocks:   [ {pos: [x, y, z], state: <palette index>, nbt: {block entity, no x/y/z}}, ... ],
      entities: [ {pos: [x, y, z] (doubles), blockPos: [x, y, z], nbt: {entity}}, ... ]
    }

Positions are relative to the template's minimum corner. A position that is not listed is left untouched when the
template is placed (like structure_void); a listed air block clears what was there.

Writing is vectorised: millions of plain block entries are produced as one numpy byte array.
"""
from __future__ import annotations

import gzip

import numpy as np

from . import nbt
from .nbt import T

_PLAIN_HEAD = b"\x09\x00\x03pos\x03\x00\x00\x00\x03"   # TAG_List "pos" of 3 TAG_Int
_STATE_HEAD = b"\x03\x00\x05state"                     # TAG_Int "state"
_PLAIN_LEN = len(_PLAIN_HEAD) + 12 + len(_STATE_HEAD) + 4 + 1


def _w_name(tag: int, name: str) -> bytes:
    b = name.encode()
    return bytes([tag]) + len(b).to_bytes(2, "big") + b


def palette_entry(key: str) -> T:
    """26.x block-state compound {id, properties} (NbtUtils.readBlockState). Older versions used {Name, Properties};
    26.3 reads a palette entry without "id" as AIR, silently."""
    from .world import split_state
    name, props = split_state(key)
    d = {"id": nbt.s(name)}
    if props:
        d["properties"] = nbt.compound({k: nbt.s(v) for k, v in props.items()})
    return nbt.compound(d)


def palette_of(state_ids: np.ndarray, keys: list[str]) -> tuple[list[str], np.ndarray]:
    """(palette, per-block palette index) for global state ids, the palette sorted by state key so the same blocks
    give the same file whatever order the source world met its states in."""
    used, inv = np.unique(state_ids, return_inverse=True)
    names = [keys[int(u)] for u in used]
    order = sorted(range(len(names)), key=names.__getitem__)
    rank = np.empty(len(order), np.int64)
    rank[order] = np.arange(len(order))
    return [names[i] for i in order], rank[inv]


def encode(size, palette: list[str], pos: np.ndarray, state: np.ndarray, block_nbt: dict[int, T],
           entities: list[tuple], data_version: int) -> bytes:
    """Uncompressed template NBT.

    pos (n, 3) int relative positions, state (n,) palette indices, block_nbt {row index: block-entity compound},
    entities [(pos (3 floats), block_pos (3 ints), compound)]."""
    pos = np.asarray(pos, np.int64).reshape(-1, 3)
    state = np.asarray(state, np.int64).reshape(-1)
    n = len(state)
    out = bytearray([nbt.TAG_COMPOUND]) + b"\x00\x00"
    out += _w_name(nbt.TAG_LIST, "size")
    nbt.write_typed(nbt.ints(*size), out)
    out += _w_name(nbt.TAG_LIST, "palette")
    nbt.write_typed(nbt.lst(nbt.TAG_COMPOUND, [palette_entry(k) for k in palette]), out)

    out += _w_name(nbt.TAG_LIST, "blocks")
    out += bytes([nbt.TAG_COMPOUND]) + n.to_bytes(4, "big")
    has = np.zeros(n, bool)
    if block_nbt:
        has[list(block_nbt)] = True
    plain = np.nonzero(~has)[0]
    rows = np.zeros((len(plain), _PLAIN_LEN), np.uint8)
    h = np.frombuffer(_PLAIN_HEAD, np.uint8)
    rows[:, :len(h)] = h
    o = len(h)
    rows[:, o:o + 12] = pos[plain].astype(">i4").view(np.uint8).reshape(-1, 12)
    o += 12
    sh = np.frombuffer(_STATE_HEAD, np.uint8)
    rows[:, o:o + len(sh)] = sh
    o += len(sh)
    rows[:, o:o + 4] = state[plain].astype(">i4").view(np.uint8).reshape(-1, 4)
    out += rows.tobytes()   # last byte of each row is TAG_End (0)
    for r in np.nonzero(has)[0]:
        out += _PLAIN_HEAD + pos[r].astype(">i4").tobytes() + _STATE_HEAD + int(state[r]).to_bytes(4, "big", signed=True)
        out += _w_name(nbt.TAG_COMPOUND, "nbt")
        nbt.write_typed(nbt.canonical(block_nbt[int(r)]), out)
        out.append(nbt.TAG_END)

    out += _w_name(nbt.TAG_LIST, "entities")
    nbt.write_typed(nbt.lst(nbt.TAG_COMPOUND, [
        nbt.c(pos=nbt.doubles(*p), blockPos=nbt.ints(*bp), nbt=nbt.canonical(e)) for p, bp, e in entities]), out)
    out += _w_name(nbt.TAG_INT, "DataVersion") + int(data_version).to_bytes(4, "big", signed=True)
    out.append(nbt.TAG_END)
    return bytes(out)


def write(path, data: bytes) -> int:
    """gzip (as Minecraft writes structure files) with a fixed timestamp, so identical content gives identical
    bytes and a re-export without changes leaves git clean. Returns the file size."""
    blob = gzip.compress(data, compresslevel=9, mtime=0)
    with open(path, "wb") as f:
        f.write(blob)
    return len(blob)


class Template:
    """A template read back (for tests and the round-trip check)."""

    def __init__(self, size, palette: list[str], pos: np.ndarray, state: np.ndarray, block_nbt: dict, entities: list,
                 data_version: int):
        self.size = size
        self.palette = palette
        self.pos = pos
        self.state = state
        self.block_nbt = block_nbt
        self.entities = entities
        self.data_version = data_version

    def keys(self) -> list[str]:
        return [self.palette[i] for i in self.state]


def read(path) -> Template:
    """Read a template. The plain block entries encode() writes (fixed 36-byte layout, before any entry with nbt)
    are decoded vectorised; everything else goes through the typed parser, so any template file can be read."""
    with open(path, "rb") as f:
        raw = nbt.decompress(f.read())
    from .world import state_key
    key = _w_name(nbt.TAG_LIST, "blocks") + bytes([nbt.TAG_COMPOUND])
    at = raw.find(key)
    if at < 0:
        root = nbt.parse_typed(raw)
        return _from_typed(root)
    n = int.from_bytes(raw[at + len(key):at + len(key) + 4], "big")
    start = at + len(key) + 4
    avail = min(n, (len(raw) - start) // _PLAIN_LEN)
    rows = np.frombuffer(raw, np.uint8, count=avail * _PLAIN_LEN, offset=start).reshape(avail, _PLAIN_LEN)
    head = np.frombuffer(_PLAIN_HEAD, np.uint8)
    sh = np.frombuffer(_STATE_HEAD, np.uint8)
    o = len(head) + 12
    ok = (rows[:, :len(head)] == head).all(1) & (rows[:, o:o + len(sh)] == sh).all(1) & (rows[:, -1] == 0)
    p = int(np.argmin(ok)) if not ok.all() else avail
    plain = rows[:p]
    pos = plain[:, len(head):len(head) + 12].copy().view(">i4").reshape(-1, 3).astype(np.int32)
    state = plain[:, o + len(sh):o + len(sh) + 4].copy().view(">i4").reshape(-1).astype(np.int32)
    # the rest of the document (nbt entries, entities...): splice out the plain rows and parse what is left
    rest = raw[:start - 4] + (n - p).to_bytes(4, "big") + raw[start + p * _PLAIN_LEN:]
    root = nbt.parse_typed(rest)
    t = _from_typed(root)
    t.pos = np.concatenate([pos, t.pos]) if len(t.pos) else pos
    t.state = np.concatenate([state, t.state]) if len(t.state) else state
    t.block_nbt = {k + p: v for k, v in t.block_nbt.items()}
    return t


def _from_typed(root: T) -> Template:
    from .world import state_key
    size = [v.value for v in root["size"].value[1]]
    palette = [state_key(e.plain()) for e in root["palette"].value[1]]
    blocks = root["blocks"].value[1]
    pos = np.zeros((len(blocks), 3), np.int32)
    state = np.zeros(len(blocks), np.int32)
    bn = {}
    for i, bl in enumerate(blocks):
        pos[i] = [v.value for v in bl["pos"].value[1]]
        state[i] = bl["state"].value
        if bl.get("nbt") is not None:
            bn[i] = bl["nbt"]
    ents = [([v.value for v in e["pos"].value[1]], [v.value for v in e["blockPos"].value[1]], e["nbt"])
            for e in root["entities"].value[1]]
    return Template(size, palette, pos, state, bn, ents, root.get("DataVersion", T(3, 0)).value)
