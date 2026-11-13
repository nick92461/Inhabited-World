"""Building library groundwork: one catalog building as a standalone template plus a mini manifest.

    python -m buildimport building 113 262        catalog ids (docs/notes/2026-11-12_drachenfels_survey/catalog.csv)

The building's columns are its own columns in the survey's building map (labels.npz, frozen with the catalog), plus
loose built columns right next to it (steps, railings) whose nearest building it is. Each column is stored from one
block under its lowest built block (the foundation row) up to its top, interior air included; nothing around the
building is stored, so the template can be set down on any prepared ground. Markers inside it are read like a
town's. No town composer yet: these files are the parts a later composer (or a workshop world) will use.

Output (default): the mod's data,
    data/inhabited_world/structure/library/drachenfels/building_<id>/v1.nbt
    data/inhabited_world/library/drachenfels/building_<id>/v1.json
"""
from __future__ import annotations

import csv
import json
import os

import numpy as np
from scipy import ndimage as ndi

from . import export as X, nbt, rules, template
from .world import World

CATALOG = "docs/notes/2026-11-12_drachenfels_survey/catalog.csv"
LABELS = "docs/notes/2026-11-12_drachenfels_survey/labels.npz"
ATTACH = 2          # loose built columns within this many blocks of the building belong to it
MARGIN = 4


def catalog_row(bid: int) -> dict:
    with open(X.resolve(CATALOG), encoding="utf-8") as f:
        for r in csv.DictReader(f):
            if int(r["id"]) == bid:
                return r
    raise SystemExit(f"no building {bid} in the catalog")


def export_building(bid: int, world_path: str, out: str | None = None, version: int = 1, log=print) -> dict:
    row = catalog_row(bid)
    x0, z0, x1, z1 = (int(row[k]) for k in ("x0", "z0", "x1", "z1"))
    x0, z0, x1, z1 = x0 - MARGIN, z0 - MARGIN, x1 + MARGIN, z1 + MARGIN
    w = World(X.resolve(world_path))
    box = w.read_box(x0, z0, x1, z1)
    L = np.load(X.resolve(LABELS))
    lab = np.zeros(box.shape[1:], np.int32)
    lx0, lz0 = int(L["x0"]), int(L["z0"])
    full = L["labels"].astype(np.int32)
    lab[:] = full[z0 - lz0:z1 - lz0 + 1, x0 - lx0:x1 - lx0 + 1]
    built2d = box.built.any(0)
    mine = lab == bid
    near_mine = np.zeros_like(mine)
    if (lab > 0).any():
        dist, (iz, ix) = ndi.distance_transform_edt(lab == 0, return_indices=True)
        near_mine = (lab == 0) & built2d & (lab[iz, ix] == bid) & (dist <= ATTACH)
    owned = mine | near_mine
    cfg = {"support_depth": 1, "support_apron": 0, "courtyard_close": 1}
    cols = X.analyse_columns(cfg, box, owned & built2d, built2d & ~owned, int(L["sea_level"]))
    cols.zone[cols.zone != X.MOLD] = X.BLEND
    st = cols.zone != X.BLEND
    # a part, not a place: cut one block under the lowest built block (the foundation row), so a ship does not
    # bring its harbour's water and seabed and a house brings one row of the ground it stood on
    bb = X._bottom_index(box.built) + box.y0
    cols.floor = np.where(st, np.maximum(cols.floor, bb - 1), cols.floor)
    zz, xx = np.nonzero(st)
    cx0, cz0, cx1, cz1 = int(xx.min()), int(zz.min()), int(xx.max()), int(zz.max())
    y0 = int(cols.floor[st].min())
    y1 = int(cols.top[st].max())
    ny = box.shape[0]
    yi = np.arange(ny)[:, None, None] + box.y0
    mask = st[None] & (yi >= cols.floor[None]) & (yi <= cols.top[None])
    mask = mask[:, cz0:cz1 + 1, cx0:cx1 + 1][y0 - box.y0:y1 - box.y0 + 1]
    states = box.states[y0 - box.y0:y1 - box.y0 + 1, cz0:cz1 + 1, cx0:cx1 + 1]
    yy, zr, xr = np.nonzero(mask)
    st_ids = states[mask]
    pal, inv = template.palette_of(st_ids, box.table.keys)
    pos = np.stack([xr, yy, zr], 1)
    index = {(int(a), int(b), int(c)): r for r, (a, b, c) in enumerate(pos)}
    bnbt = {}
    for (x, y, z), be in box.block_entities.items():
        r = index.get((x - box.x0 - cx0, y - y0, z - box.z0 - cz0))
        if r is not None:
            bnbt[r] = nbt.T(nbt.TAG_COMPOUND, {k: v for k, v in be.value.items() if k not in ("x", "y", "z", "keepPacked")})
    size = [cx1 - cx0 + 1, y1 - y0 + 1, cz1 - cz0 + 1]
    # doors (lower halves) and the ground under the footprint
    doors = []
    for r, k in enumerate(pal):
        if k.split("[")[0].endswith("_door") and "half=lower" in k:
            for p in pos[inv == r]:
                doors.append([int(p[0]), int(p[1]), int(p[2])])
    ground = cols.ground[st] - y0
    mk = [{"id": m["id"], "pos": [m["pos"][0] - box.x0 - cx0, m["pos"][1] - y0, m["pos"][2] - box.z0 - cz0],
           "yaw": m["yaw"], "authored_as": m["authored_as"]} for m in box.markers
          if owned[m["pos"][2] - box.z0, m["pos"][0] - box.x0]]
    rid = f"library/drachenfels/building_{bid}"
    if out is None:
        sdir = os.path.join(X.REPO, X.RESOURCES, "structure", *rid.split("/"))
        mdir = os.path.join(X.REPO, X.RESOURCES, *rid.split("/"))
    else:
        sdir = mdir = os.path.join(out, f"building_{bid}")
    os.makedirs(sdir, exist_ok=True)
    os.makedirs(mdir, exist_ok=True)
    data = template.encode(size, pal, pos, inv, bnbt, [], box.data_version)
    nbytes = template.write(os.path.join(sdir, f"v{version}.nbt"), data)
    footprint = st[cz0:cz1 + 1, cx0:cx1 + 1]
    man = {
        "format": 1,
        "id": f"inhabited_world:drachenfels/building_{bid}",
        "version": version,
        "template": f"inhabited_world:{rid}/v{version}",
        "catalog": {k: row[k] for k in ("id", "district", "type", "storeys", "workstations", "doors", "waterfront")},
        "source": {"world": "inhabited_world_mod/run/saves/Drachenfels", "box_min": [box.x0 + cx0, y0, box.z0 + cz0]},
        "size": size,
        "footprint": {"width_x": size[0], "depth_z": size[2], "columns": int(footprint.sum()),
                      "rows": ["".join("#" if c else "." for c in r) for r in footprint]},
        "ground": {"median": int(np.median(ground)), "range": [int(ground.min()), int(ground.max())],
                   "note": "template y of the ground the building stood on in its source"},
        "entrance": {"side": row["entrance"], "basis": row["entrance_basis"], "doors": doors},
        "markers": mk,
        "stats": {"blocks": int(len(st_ids)), "non_air": int(np.array([not k.endswith(":air") for k in pal])[inv].sum()),
                  "block_entities": len(bnbt), "bytes": nbytes},
    }
    with open(os.path.join(mdir, f"v{version}.json"), "w", encoding="utf-8", newline="\n") as f:
        f.write(X.dump_manifest(man))
    log(f"building {bid} ({row['type']}, {row['district']}): {size[0]} x {size[2]}, {size[1]} high, "
        f"{man['stats']['non_air']:,} blocks, {len(bnbt)} block entities, entrance {row['entrance']} "
        f"({len(doors)} doors), {nbytes / 1e3:.0f} kB -> {sdir}")
    return man


def run(ids, out=None, world=None, log=print):
    cfg = X.load_town("d1_harbour")
    wp = world or cfg["original"]["world"]
    return [export_building(i, wp, out, log=log) for i in ids]
