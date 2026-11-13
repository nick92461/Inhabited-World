"""Read Minecraft Java "Anvil" region files (.mca) and decode chunk block data.

A region file holds 32 x 32 chunks. Its first 4 KiB is a table of where each chunk's bytes start; each chunk is a
compressed NBT compound. Inside a chunk, blocks live in 16 x 16 x 16 sections; each section has a palette (the
block states that occur in it) and a packed long array of palette indices.

Two chunk layouts are read:
- 1.18 and later (DataVersion >= 2844), which includes 26.x: root keys `sections`, each with
  `block_states: {palette, data}`; indices never span two longs.
- 1.13 to 1.17 (DataVersion 1451..2843): everything under `Level`, sections with `Palette` and `BlockStates`;
  before 1.16 (DataVersion < 2529) indices DO span long boundaries.
- before 1.13 (numeric block IDs): `Level` sections with `Blocks`/`Add` byte arrays. Only the IDs plain terrain
  uses are named (rules.LEGACY_IDS); any other ID becomes `legacy:<id>`, which the rules count as placed. A world
  with real builds in this format should be opened once in modern Minecraft to upgrade it first.

Everything is read-only; the source save is opened with "rb" and never written.
"""
from __future__ import annotations

import os
import re
import zlib
from dataclasses import dataclass
from typing import Iterator

import numpy as np

from . import nbt

SECTOR = 4096
FLATTENING = 1451  # 1.13: block states with names and palettes
NO_SPAN = 2529     # 1.16 (20w17a): palette indices no longer span longs
NO_LEVEL = 2844    # 1.18 (21w43a): the `Level` wrapper went away, sections became `block_states`

_REGION_NAME = re.compile(r"^r\.(-?\d+)\.(-?\d+)\.mca$")


@dataclass
class Section:
    y: int                      # section index (block y = 16 * y ... 16 * y + 15)
    palette: list[str]          # block-state keys (see block_key)
    indices: np.ndarray | None  # 4096 palette indices in YZX order (index = y*256 + z*16 + x), or None if 1 entry


@dataclass
class Chunk:
    cx: int
    cz: int
    data_version: int
    status: str
    sections: list[Section]
    blending: bool = False      # carries blending data: an old chunk upgraded by a newer Minecraft


def region_files(region_dir) -> list[tuple[int, int, str]]:
    """(rx, rz, path) for every non-empty region file in a directory, sorted."""
    out = []
    for name in os.listdir(region_dir):
        m = _REGION_NAME.match(name)
        path = os.path.join(region_dir, name)
        if m and os.path.getsize(path) > 2 * SECTOR:
            out.append((int(m.group(1)), int(m.group(2)), path))
    return sorted(out)


def read_region(path) -> Iterator[tuple[int, int, bytes]]:
    """Yield (local_x, local_z, uncompressed chunk NBT bytes) for every chunk stored in a region file."""
    with open(path, "rb") as f:
        data = f.read()
    if len(data) < 2 * SECTOR:
        return
    for i in range(1024):
        entry = int.from_bytes(data[4 * i:4 * i + 4], "big")
        offset, count = entry >> 8, entry & 0xFF
        if offset < 2 or count == 0:
            continue
        start = offset * SECTOR
        length = int.from_bytes(data[start:start + 4], "big")
        kind = data[start + 4]
        payload = data[start + 5:start + 4 + length]
        if kind & 0x80:
            # Oversized chunk stored beside the region as c.<x>.<z>.mca. Rare; read it if present.
            payload = _external_chunk(path, i, kind)
            if payload is None:
                continue
            kind &= 0x7F
        yield i % 32, i // 32, _decompress(kind, payload)


def _external_chunk(region_path, i, kind):
    m = _REGION_NAME.match(os.path.basename(region_path))
    if not m:
        return None
    cx = int(m.group(1)) * 32 + i % 32
    cz = int(m.group(2)) * 32 + i // 32
    ext = os.path.join(os.path.dirname(region_path), f"c.{cx}.{cz}.mcc")
    if not os.path.exists(ext):
        return None
    with open(ext, "rb") as f:
        return f.read()


def _decompress(kind: int, payload: bytes) -> bytes:
    if kind == 2:
        return zlib.decompress(payload)
    if kind == 1:
        import gzip
        return gzip.decompress(payload)
    if kind == 3:
        return payload
    if kind == 4:
        raise NotImplementedError("LZ4-compressed chunks (server option) are not supported; re-save with zlib")
    raise NotImplementedError(f"unknown chunk compression {kind}")


# Block-state properties kept in a block key: only the ones the survey rules look at (see rules.py), per name
# suffix. Everything else (facing, waterlogged, half, ...) is dropped so the key set stays small.
KEPT_PROPERTIES = (("_leaves", "persistent"), ("_bed", "part"), ("_log", "axis"))


def block_key(entry) -> str:
    """'minecraft:oak_leaves[persistent=true]' style key for a palette entry; namespace kept, most properties dropped.

    A palette entry is a compound {Name, Properties} (1.13-1.21), or in 26.x saves either a plain string for a
    block's default state or a compound {id, properties} for any other state.
    """
    if isinstance(entry, str):
        return entry if ":" in entry else "minecraft:" + entry
    if len(entry) == 1 and "" in entry:
        return block_key(entry[""])
    # 26.x writes {id, properties}; 1.13-1.21 wrote {Name, Properties}
    name = entry.get("Name") or entry.get("id") or "minecraft:air"
    if ":" not in name:
        name = "minecraft:" + name
    props = entry.get("Properties") or entry.get("properties")
    if props:
        kept = [f"{k}={props[k]}" for suffix, k in KEPT_PROPERTIES if name.endswith(suffix) and k in props]
        if kept:
            return f"{name}[{','.join(kept)}]"
    return name


def unpack_indices(longs: np.ndarray, palette_size: int, count: int = 4096, spanning: bool = False) -> np.ndarray:
    """Unpack `count` palette indices from a packed long array.

    Bits per index = max(4, ceil(log2(palette_size))). Non-spanning (1.16+): each long holds floor(64 / bits)
    indices, low bits first, leftover high bits unused. Spanning (1.13-1.15): one continuous little-endian bit
    stream across longs.
    """
    bits = max(4, int(palette_size - 1).bit_length())
    u = np.asarray(longs).astype(">i8").view(">u8").astype(np.uint64)
    mask = np.uint64((1 << bits) - 1)
    if not spanning:
        per = 64 // bits
        shifts = (np.arange(per, dtype=np.uint64) * np.uint64(bits))
        out = ((u[:, None] >> shifts[None, :]) & mask).reshape(-1)[:count]
        return out.astype(np.uint16)
    # Spanning: expand to a bit array (little-endian within each long, longs in order), then regroup.
    raw = u.astype("<u8").tobytes()
    bitarr = np.unpackbits(np.frombuffer(raw, dtype=np.uint8), bitorder="little")
    need = count * bits
    bitarr = bitarr[:need].reshape(count, bits)
    weights = (1 << np.arange(bits)).astype(np.uint32)
    return (bitarr.astype(np.uint32) @ weights).astype(np.uint16)


def legacy_key(block_id: int) -> str:
    from .rules import LEGACY_IDS
    name = LEGACY_IDS.get(block_id)
    return "minecraft:" + name if name else f"legacy:{block_id}"


def parse_chunk(raw: bytes) -> Chunk:
    root = nbt.parse(raw)
    dv = int(root.get("DataVersion", 0))
    level = root if dv >= NO_LEVEL or "Level" not in root else root["Level"]
    cx = int(level.get("xPos", 0))
    cz = int(level.get("zPos", 0))
    status = str(level.get("Status", ""))
    sections = []
    if "sections" in level:
        for s in level["sections"]:
            bs = s.get("block_states")
            if not bs or "palette" not in bs:
                continue
            palette = [block_key(e) for e in bs["palette"]]
            data = bs.get("data")
            idx = None
            if len(palette) > 1 and data is not None and len(data):
                idx = unpack_indices(data, len(palette))
            sections.append(Section(int(s["Y"]), palette, idx))
    elif "Sections" in level and dv < FLATTENING:
        for s in level["Sections"]:
            if "Blocks" not in s:
                continue
            ids = np.asarray(s["Blocks"]).view(np.uint8).astype(np.uint16)
            if "Add" in s:
                add = np.asarray(s["Add"]).view(np.uint8)
                nib = np.empty(4096, dtype=np.uint16)
                nib[0::2] = add & 0x0F
                nib[1::2] = add >> 4
                ids |= nib << 8
            uniq, inv = np.unique(ids, return_inverse=True)
            palette = [legacy_key(int(i)) for i in uniq]
            idx = inv.astype(np.uint16) if len(palette) > 1 else None
            sections.append(Section(int(s["Y"]), palette, idx))
    elif "Sections" in level:
        spanning = dv < NO_SPAN
        for s in level["Sections"]:
            if "Palette" not in s:
                continue
            palette = [block_key(e) for e in s["Palette"]]
            data = s.get("BlockStates")
            idx = None
            if len(palette) > 1 and data is not None and len(data):
                idx = unpack_indices(data, len(palette), spanning=spanning)
            sections.append(Section(int(s["Y"]), palette, idx))
    return Chunk(cx, cz, dv, status, sections, "blending_data" in root or "blending_data" in level)


def iter_chunks(region_dir) -> Iterator[Chunk]:
    for _, _, path in region_files(region_dir):
        for _, _, raw in read_region(path):
            yield parse_chunk(raw)
