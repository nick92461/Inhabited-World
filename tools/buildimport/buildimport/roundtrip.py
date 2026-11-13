"""Round-trip check: a town pasted into a world, compared block for block with its templates and its source.

    python -m buildimport roundtrip d1_harbour <world with the pasted town> [--origin x,y,z] [--source <world>]

1. templates vs pasted: every stored block of every tile must be in the pasted world exactly (full block state).
   Block entities: same type at the same place, and same data apart from position.
2. source vs pasted (when the source world is readable): inside the stored columns, every block of the source must
   be in the pasted world, except the blocks the exporter changed on purpose: neighbouring districts' buildings it
   erased, and marker signs it took out.
Marker signs in the pasted world are read like the exporter reads them and must match the manifest's markers.
"""
from __future__ import annotations

import json
import os
from collections import Counter

import numpy as np

from . import export as X, nbt, template
from .world import World


def _be_data(be: nbt.T) -> dict:
    return {k: v for k, v in be.value.items() if k not in ("x", "y", "z", "keepPacked")}


def read_columns(cfg: dict, version=None) -> dict:
    _, tdir, _ = X.resource_dirs(cfg, version)
    with open(os.path.join(tdir, "columns.nbt"), "rb") as f:
        root = nbt.parse_typed(nbt.decompress(f.read()))
    W, D = root["width"].value, root["depth"].value
    return {k: np.asarray(root[k].value).astype(np.int32).reshape(D, W) for k in ("zone", "floor", "top", "ground")}


def intended(cfg: dict, man: dict) -> tuple[np.ndarray, list, dict]:
    """Template content in template space: (H, D, W) array of palette-key ids (-1 = not stored), key list,
    {(x, y, z): block-entity data}."""
    W, H, D = man["size"]
    sdir, _, sid = X.resource_dirs(cfg, man["version"])
    keys, index = [], {}
    grid = np.full((H, D, W), -1, np.int32)
    bes = {}
    for t in man["tiles"]:
        name = t["id"].rsplit("/", 1)[1]
        tp = template.read(os.path.join(sdir, name + ".nbt"))
        ox, oy, oz = t["offset"]
        ids = np.array([index.setdefault(k, len(index)) for k in tp.palette], np.int32)
        for k in tp.palette:
            if len(keys) < len(index):
                keys.extend([None] * (len(index) - len(keys)))
            keys[index[k]] = k
        p = tp.pos
        grid[p[:, 1] + oy, p[:, 2] + oz, p[:, 0] + ox] = ids[tp.state]
        for r, be in tp.block_nbt.items():
            x, y, z = (int(v) for v in p[r])
            bes[(x + ox, y + oy, z + oz)] = _be_data(be)
    return grid, keys, bes


def compare(grid, keys, bes, box, oy_box: int, label: str, limit=20) -> dict:
    """Compare template-space `grid` with a BlockBox whose [0, 0] column is template (0, 0) and whose y index of
    template y = 0 is oy_box."""
    H, D, W = grid.shape
    stored = grid >= 0
    world_keys = box.table.keys
    sub = np.full(grid.shape, -2, np.int64)
    ylo, yhi = max(0, -oy_box), min(H, box.shape[0] - oy_box)
    sub[ylo:yhi] = box.states[oy_box + ylo:oy_box + yhi, :D, :W]
    # map both sides to strings via a shared table
    k2i = {k: i for i, k in enumerate(world_keys)}
    gmap = np.array([k2i.get(k, -3) for k in keys] + [-4], np.int64)
    want = np.where(stored, gmap[np.where(stored, grid, len(keys))], -5)
    bad = stored & (sub != want)
    n_bad = int(bad.sum())
    pairs = Counter()
    examples = []
    for y, z, x in zip(*np.nonzero(bad)):
        a = keys[grid[y, z, x]]
        b = world_keys[sub[y, z, x]] if sub[y, z, x] >= 0 else "(outside the read box)"
        pairs[(a, b)] += 1
        if len(examples) < limit:
            examples.append({"pos": [int(x), int(y), int(z)], "template": a, "world": b})
    # block entities
    be_missing, be_diff = 0, 0
    be_examples = []
    for (x, y, z), data in bes.items():
        wbe = box.block_entities.get((box.x0 + x, box.y0 + oy_box + y, box.z0 + z))
        if wbe is None:
            be_missing += 1
            if len(be_examples) < limit:
                be_examples.append({"pos": [x, y, z], "problem": "missing", "id": str(data.get("id").value)})
        elif _be_data(wbe) != data:
            be_diff += 1
            if len(be_examples) < limit:
                diff = sorted(k for k in set(data) | set(_be_data(wbe)) if data.get(k) != _be_data(wbe).get(k))
                be_examples.append({"pos": [x, y, z], "problem": "data differs", "keys": diff,
                                    "id": str(data.get("id").value)})
    return {"label": label, "compared_blocks": int(stored.sum()), "mismatched_blocks": n_bad,
            "match_rate": 1 - n_bad / max(1, int(stored.sum())),
            "top_mismatches": [{"template": a, "world": b, "count": c} for (a, b), c in pairs.most_common(limit)],
            "examples": examples, "block_entities": len(bes), "block_entities_missing": be_missing,
            "block_entities_differing": be_diff, "block_entity_examples": be_examples}


def run(town: str, pasted: str, origin: str | None = None, source: str | None = None, report: str | None = None,
        log=print) -> bool:
    cfg = X.load_town(town)
    man = X.read_manifest(cfg)
    if man is None:
        raise SystemExit("no manifest: export the town first")
    W, H, D = man["size"]
    if origin:
        ox, oy, oz = (int(v) for v in origin.split(","))
    else:
        ox, oy, oz = man["workshop"]["origin"]
    grid, keys, bes = intended(cfg, man)
    log(f"templates: {int((grid >= 0).sum()):,} stored blocks, {len(bes)} block entities")
    w = World(X.resolve(pasted))
    box = w.read_box(ox, oz, ox + W - 1, oz + D - 1)
    found = {m["id"]: [m["pos"][0] - ox, m["pos"][1] - oy, m["pos"][2] - oz] for m in box.markers}
    res = {"town": man["id"], "version": man["version"], "pasted_world": pasted, "origin": [ox, oy, oz]}
    res["templates_vs_pasted"] = compare(grid, keys, bes, box, oy - box.y0, "templates vs pasted")
    want = {m["id"]: m["pos"] for m in man["markers"]}
    res["markers"] = {"manifest": len(want), "found": len(found),
                      "missing": sorted(set(want) - set(found)), "extra": sorted(set(found) - set(want)),
                      "moved": sorted(i for i in set(want) & set(found) if want[i] != found[i])}
    ok = res["templates_vs_pasted"]["mismatched_blocks"] == 0 and res["templates_vs_pasted"]["block_entities_missing"] == 0

    src_kind = man["source"]["kind"]
    spath = source or (man["original"]["world"] if src_kind == "original" else man["source"]["world"])
    sp = X.resolve(spath)
    if os.path.isdir(sp):
        sx, sy, sz = man["source"]["box_min"] if src_kind == "original" or source is None else man["original"]["box_min"]
        sbox = w2 = None
        w2 = World(sp)
        sbox = w2.read_box(sx, sz, sx + W - 1, sz + D - 1)
        # what the exporter changed on purpose: foreign buildings erased
        if src_kind == "original":
            owned, foreign = X.ownership(cfg, sbox, cfg["original"].get("district"), cfg["original"].get("labels"))
            changed = sbox.built & foreign[None]
        else:
            changed = np.zeros(sbox.shape, bool)
        # source content as template-space grid
        sgrid = np.full(grid.shape, -1, np.int32)
        so = sy - sbox.y0
        stored = grid >= 0
        sk = sbox.table.keys
        ylo, yhi = max(0, -so), min(H, sbox.shape[0] - so)
        sgrid[ylo:yhi] = sbox.states[so + ylo:so + yhi].astype(np.int32)
        exp_changed = np.zeros(grid.shape, bool)
        exp_changed[ylo:yhi] = changed[so + ylo:so + yhi]
        cmp_mask = stored & ~exp_changed
        sub = np.full(grid.shape, -1, np.int64)
        oyb = oy - box.y0
        ylo2, yhi2 = max(0, -oyb), min(H, box.shape[0] - oyb)
        sub[ylo2:yhi2] = box.states[oyb + ylo2:oyb + yhi2, :D, :W]
        skeys = np.array(sk, dtype=object)
        pkeys = np.array(box.table.keys, dtype=object)
        a = skeys[np.where(cmp_mask, sgrid, 0)]
        b = pkeys[np.where(cmp_mask & (sub >= 0), sub, 0)]
        bad = cmp_mask & (a != b)
        pairs = Counter(zip(a[bad].tolist(), b[bad].tolist()))
        sbes = {(x - sx, y - sy, z - sz): _be_data(v) for (x, y, z), v in sbox.block_entities.items()}
        be_missing = sum(1 for p, d in sbes.items()
                         if 0 <= p[1] < H and cmp_mask[p[1], p[2], p[0]]
                         and (box.x0 + p[0], box.y0 + oyb + p[1], box.z0 + p[2]) not in box.block_entities)
        res["source_vs_pasted"] = {
            "source_world": spath, "compared_blocks": int(cmp_mask.sum()),
            "mismatched_blocks": int(bad.sum()), "match_rate": 1 - int(bad.sum()) / max(1, int(cmp_mask.sum())),
            "erased_on_purpose": int((stored & exp_changed).sum()),
            "top_mismatches": [{"source": k[0], "pasted": k[1], "count": c} for k, c in pairs.most_common(20)],
            "source_block_entities_missing": int(be_missing)}
        ok = ok and res["source_vs_pasted"]["mismatched_blocks"] == 0
    else:
        res["source_vs_pasted"] = {"skipped": f"source world not found: {spath}"}

    t = res["templates_vs_pasted"]
    log(f"templates vs pasted: {t['compared_blocks']:,} blocks, {t['mismatched_blocks']:,} mismatched "
        f"({100 * t['match_rate']:.4f}% match); block entities {t['block_entities']}, missing "
        f"{t['block_entities_missing']}, data differs {t['block_entities_differing']}")
    s = res["source_vs_pasted"]
    if "compared_blocks" in s:
        log(f"source vs pasted: {s['compared_blocks']:,} blocks, {s['mismatched_blocks']:,} mismatched "
            f"({100 * s['match_rate']:.4f}% match); {s['erased_on_purpose']:,} erased on purpose "
            f"(neighbouring districts); source block entities missing {s['source_block_entities_missing']}")
    else:
        log(f"source vs pasted: {s['skipped']}")
    m = res["markers"]
    log(f"markers: {m['found']} found of {m['manifest']} (missing {m['missing']}, extra {m['extra']}, "
        f"moved {m['moved']})")
    for row in t["top_mismatches"][:8]:
        log(f"  template {row['template']}  ->  world {row['world']}  x{row['count']}")
    if "top_mismatches" in s:
        for row in s["top_mismatches"][:8]:
            log(f"  source {row['source']}  ->  pasted {row['pasted']}  x{row['count']}")
    if report:
        with open(report, "w", encoding="utf-8") as f:
            json.dump(res, f, indent=1, default=str)
    log("ROUND TRIP OK" if ok else "ROUND TRIP: MISMATCHES (see above)")
    return ok
