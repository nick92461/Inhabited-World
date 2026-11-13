"""Helpers that build small synthetic chunks and region files (no real save needed)."""
from __future__ import annotations

import os
import sys
import zlib

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from buildimport import nbt  # noqa: E402


def pack(indices, palette_size, spanning=False) -> np.ndarray:
    """Reference packer (the inverse of anvil.unpack_indices), written independently and slowly on purpose."""
    bits = max(4, (palette_size - 1).bit_length())
    if not spanning:
        per = 64 // bits
        n = (len(indices) + per - 1) // per
        out = []
        for i in range(n):
            v = 0
            for j, x in enumerate(indices[i * per:(i + 1) * per]):
                v |= int(x) << (j * bits)
            out.append(v)
    else:
        total = len(indices) * bits
        big = 0
        for i, x in enumerate(indices):
            big |= int(x) << (i * bits)
        out = [(big >> (64 * i)) & ((1 << 64) - 1) for i in range((total + 63) // 64)]
    signed = [v - (1 << 64) if v >= (1 << 63) else v for v in out]
    return np.array(signed, dtype=">i8")


def section_26(y: int, blocks: np.ndarray, palette: list):
    """A 1.18+/26.x section. blocks: (16, 16, 16) palette indices in y, z, x order. Palette entries may be plain
    strings (26.x writes default states that way) or {Name, Properties} dicts; lists of mixed entries are wrapped
    the way 26.x writes them: [{"": "minecraft:stone"}, {"Name": ..., "Properties": ...}]."""
    mixed = any(isinstance(p, str) for p in palette) and any(isinstance(p, dict) for p in palette)
    if mixed:
        pal = [{"": p} if isinstance(p, str) else p for p in palette]
    elif all(isinstance(p, str) for p in palette):
        pal = palette  # a list of TAG_String
    else:
        pal = palette
    bs = {"palette": pal}
    if len(palette) > 1:
        bs["data"] = (nbt.TAG_LONG_ARRAY, pack(blocks.reshape(-1), len(palette)))
    return {"Y": (nbt.TAG_BYTE, y), "block_states": bs}


def chunk_26(cx: int, cz: int, sections: list, status="minecraft:full", blending=False) -> bytes:
    root = {"DataVersion": 5023, "xPos": cx, "zPos": cz, "yPos": -4, "Status": status, "sections": sections}
    if blending:
        root["blending_data"] = {"min_section": -4, "max_section": 20}
    return nbt.encode_root(root)


def chunk_legacy(cx: int, cz: int, y: int, ids: np.ndarray) -> bytes:
    """A pre-1.13 chunk with one section of numeric block IDs (4096 in y, z, x order)."""
    sec = {"Y": (nbt.TAG_BYTE, y), "Blocks": (nbt.TAG_BYTE_ARRAY, ids.astype(np.uint8).view(np.int8)),
           "Data": (nbt.TAG_BYTE_ARRAY, np.zeros(2048, np.int8))}
    return nbt.encode_root({"DataVersion": 1343, "Level": {"xPos": cx, "zPos": cz, "Sections": [sec]}})


def region_bytes(chunks: dict) -> bytes:
    """A region file with chunks {(local_x, local_z): uncompressed NBT bytes}, zlib-compressed like Minecraft."""
    header = bytearray(8192)
    body = bytearray()
    sector = 2
    for (lx, lz), raw in chunks.items():
        data = zlib.compress(raw)
        payload = (len(data) + 1).to_bytes(4, "big") + bytes([2]) + data  # length counts the type byte
        payload += b"\0" * (-len(payload) % 4096)
        n = len(payload) // 4096
        i = lx + 32 * lz
        header[4 * i:4 * i + 4] = ((sector << 8) | n).to_bytes(4, "big")
        body += payload
        sector += n
    return bytes(header + body)


# ---- whole synthetic worlds in the 26.x format, for the exporter tests ----

def palette_entry_26(key: str):
    """'minecraft:x' -> bare string (26.x default state); 'minecraft:x[a=1]' -> {id, properties} (26.x)."""
    if "[" not in key:
        return key
    name, rest = key[:-1].split("[", 1)
    return {"id": name, "properties": dict(kv.split("=", 1) for kv in rest.split(","))}


def write_world(folder: str, cells: dict, block_entities: list = (), entities: list = (), y_sections=range(-1, 8)):
    """A world folder (26.x layout: dimensions/minecraft/overworld/{region,entities}) holding cells
    {(x, y, z): block key}; everything else air. block_entities: compounds with x, y, z, id (plain values or
    (tag, value)). entities: compounds with id and Pos [x, y, z] doubles. Every chunk with a cell is written in full."""
    region = os.path.join(folder, "dimensions", "minecraft", "overworld", "region")
    ent_dir = os.path.join(folder, "dimensions", "minecraft", "overworld", "entities")
    os.makedirs(region, exist_ok=True)
    os.makedirs(ent_dir, exist_ok=True)
    chunks = {}
    for (x, y, z), k in cells.items():
        chunks.setdefault((x >> 4, z >> 4), {})[(x & 15, y, z & 15)] = k
    by_region: dict = {}
    for (cx, cz), cc in chunks.items():
        sections = []
        for sy in y_sections:
            local = {(x, y - sy * 16, z): k for (x, y, z), k in cc.items() if sy * 16 <= y < sy * 16 + 16}
            keys = ["minecraft:air"] + sorted(set(local.values()) - {"minecraft:air"})
            idx = np.zeros((16, 16, 16), np.int64)
            for (x, y, z), k in local.items():
                idx[y, z, x] = keys.index(k)
            sections.append(section_26(sy, idx, [palette_entry_26(k) for k in keys]))
        bes = [b for b in block_entities if (b["x"] >> 4, b["z"] >> 4) == (cx, cz)]
        root = {"DataVersion": 5023, "xPos": cx, "zPos": cz, "yPos": min(y_sections), "Status": "minecraft:full",
                "sections": sections}
        if bes:
            root["block_entities"] = list(bes)
        by_region.setdefault((cx >> 5, cz >> 5), {})[(cx & 31, cz & 31)] = nbt.encode_root(root)
    for (rx, rz), cs in by_region.items():
        with open(os.path.join(region, f"r.{rx}.{rz}.mca"), "wb") as f:
            f.write(region_bytes(cs))
    ents: dict = {}
    for e in entities:
        x, _, z = e["Pos"]
        cx, cz = int(np.floor(x[1] if isinstance(x, tuple) else x)) >> 4, int(np.floor(z[1] if isinstance(z, tuple) else z)) >> 4
        ents.setdefault((cx, cz), []).append(e)
    by_region = {}
    for (cx, cz), es in ents.items():
        root = {"DataVersion": 5023, "Position": (nbt.TAG_INT_ARRAY, np.array([cx, cz])), "Entities": es}
        by_region.setdefault((cx >> 5, cz >> 5), {})[(cx & 31, cz & 31)] = nbt.encode_root(root)
    for (rx, rz), cs in by_region.items():
        with open(os.path.join(ent_dir, f"r.{rx}.{rz}.mca"), "wb") as f:
            f.write(region_bytes(cs))
    with open(os.path.join(folder, "level.dat"), "wb") as f:
        f.write(b"")
