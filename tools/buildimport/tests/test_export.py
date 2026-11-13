"""Exporter tests on a small synthetic harbour (no real save needed).

The scene, in one 32 x 32 box (four chunks), sea level 63:
  land  x 0..19: stone to y 63, grass at y 64
  sea   x 20..31: stone to y 49, a cobblestone seabed at y 50 (map-maker "terrain paint"), water y 51..63
  a hollow planks house x 4..8, z 4..8, y 65..69 with a chest (non-default state) and an entrance stair
  a cellar block at (5, 58, 5), deep under the ground
  a planks pier deck at y 64, x 20..28, z 16, over the water
  a marker sign "@test_quay" at (18, 65, 16)
  entities: an item frame on the house, a zombie and a villager
"""
import json
import os
import shutil

import numpy as np
import pytest

from conftest import write_world
from buildimport import export as X, nbt, roundtrip, template, world as Wd, library, anvil

SEA = 63
CHEST = "minecraft:chest[facing=east,type=single,waterlogged=false]"
STAIR = "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"
SIGN = "minecraft:oak_sign[rotation=12,waterlogged=false]"
QUIET = dict(log=lambda *a: None)


def scene():
    cells = {}
    for x in range(32):
        for z in range(32):
            if x < 20:
                for y in range(40, 64):
                    cells[(x, y, z)] = "minecraft:stone"
                cells[(x, 64, z)] = "minecraft:grass_block"
            else:
                for y in range(40, 50):
                    cells[(x, y, z)] = "minecraft:stone"
                cells[(x, 50, z)] = "minecraft:cobblestone"
                for y in range(51, 64):
                    cells[(x, y, z)] = "minecraft:water"
    for x in range(4, 9):
        for z in range(4, 9):
            for y in range(65, 70):
                if y in (65, 69) or x in (4, 8) or z in (4, 8):
                    cells[(x, y, z)] = "minecraft:oak_planks"
    cells[(6, 66, 6)] = CHEST
    cells[(9, 65, 6)] = STAIR
    cells[(5, 58, 5)] = "minecraft:oak_planks"
    for x in range(20, 29):
        cells[(x, 64, 16)] = "minecraft:oak_planks"
    cells[(18, 65, 16)] = SIGN
    bes = [
        {"x": 6, "y": 66, "z": 6, "id": "minecraft:chest", "Items": [
            {"Slot": (nbt.TAG_BYTE, 0), "id": "minecraft:bread", "count": 3}]},
        {"x": 18, "y": 65, "z": 16, "id": "minecraft:sign", "is_waxed": (nbt.TAG_BYTE, 0),
         "front_text": {"messages": ["@test_quay", "the quay", "", ""], "color": "black",
                        "has_glowing_text": (nbt.TAG_BYTE, 0)}},
    ]
    ents = [
        {"id": "minecraft:item_frame", "Pos": [(nbt.TAG_DOUBLE, 7.5), (nbt.TAG_DOUBLE, 67.5), (nbt.TAG_DOUBLE, 3.97)],
         "UUID": (nbt.TAG_INT_ARRAY, np.array([1, 2, 3, 4])), "Facing": (nbt.TAG_BYTE, 2),
         "block_pos": (nbt.TAG_INT_ARRAY, np.array([7, 67, 3]))},
        {"id": "minecraft:zombie", "Pos": [(nbt.TAG_DOUBLE, 12.5), (nbt.TAG_DOUBLE, 65.0), (nbt.TAG_DOUBLE, 12.5)]},
        {"id": "minecraft:villager", "Pos": [(nbt.TAG_DOUBLE, 14.5), (nbt.TAG_DOUBLE, 65.0), (nbt.TAG_DOUBLE, 14.5)]},
    ]
    return cells, bes, ents


def town_config(tmp, world):
    cfg = {
        "id": "inhabited_world:test/harbour", "name": "Test Harbour", "resource_path": "towns/test/harbour",
        "version": 1, "shipped": False, "output_root": os.path.join(tmp, "out"),
        "original": {"world": world, "box": [0, 0, 31, 31], "sea_level": SEA},
        "workshop": {"world": os.path.join(tmp, "no_workshop"), "sea_level": SEA},
        "target_sea_level": SEA, "waterfront": "east", "tile": 16, "support_depth": 2, "support_apron": 2,
        "courtyard_close": 1, "blend_border": 8, "joins": [],
        "seed_markers": [{"id": "test_square", "at": [12, 12], "yaw": 0, "note": "an open spot"}],
    }
    path = os.path.join(tmp, "harbour.json")
    with open(path, "w") as f:
        json.dump(cfg, f)
    return path


@pytest.fixture()
def exported(tmp_path):
    tmp = str(tmp_path)
    world = os.path.join(tmp, "src")
    cells, bes, ents = scene()
    write_world(world, cells, bes, ents)
    cfg_path = town_config(tmp, world)
    man = X.run(cfg_path, **QUIET)
    return tmp, cfg_path, man, cells


def test_palette_26_formats():
    assert Wd.state_key({"id": "minecraft:oak_stairs", "properties": {"half": "top", "facing": "east"}}) == \
        "minecraft:oak_stairs[facing=east,half=top]"
    assert Wd.state_key({"": "minecraft:dirt"}) == "minecraft:dirt"
    assert Wd.state_key({"Name": "minecraft:oak_log", "Properties": {"axis": "x"}}) == "minecraft:oak_log[axis=x]"
    # the survey's reduced keys: 26.x {id, properties} entries were read as air before 2026-11-13
    assert anvil.block_key({"id": "minecraft:oak_log", "properties": {"axis": "x"}}) == "minecraft:oak_log[axis=x]"
    assert anvil.block_key({"id": "minecraft:oak_stairs", "properties": {"facing": "east"}}) == "minecraft:oak_stairs"


def test_typed_nbt_is_lossless():
    t = nbt.c(b=nbt.b(3), empty=nbt.lst(nbt.TAG_END, []), l=nbt.ints(1, 2), a=nbt.int_array([1, -2]), d=nbt.d(0.5))
    raw = nbt.root_bytes(t)
    assert nbt.parse_typed(raw) == t and nbt.root_bytes(nbt.parse_typed(raw)) == raw
    assert list(nbt.canonical(t).value) == ["a", "b", "d", "empty", "l"]


def test_template_roundtrip_and_26_palette(tmp_path):
    keys = ["minecraft:stone", STAIR, "minecraft:air"]
    pal, inv = template.palette_of(np.array([1, 0, 2, 1]), keys)
    assert pal == sorted(keys) and [pal[i] for i in inv] == [STAIR, "minecraft:stone", "minecraft:air", STAIR]
    data = template.encode([2, 2, 2], pal, np.array([[0, 0, 0], [1, 0, 0], [0, 1, 0], [1, 1, 1]]), inv,
                           {1: nbt.c(z=nbt.i(1), id=nbt.s("minecraft:chest"))}, [], 5023)
    root = nbt.parse_typed(data)
    entry = root["palette"].value[1][pal.index(STAIR)]
    assert set(entry.value) == {"id", "properties"}     # 26.3 reads {Name, Properties} as AIR
    blocks = root["blocks"].value[1]
    assert list(blocks[-1]["nbt"].value) == ["id", "z"]  # block-entity keys sorted
    p = tmp_path / "t.nbt"
    template.write(p, data)
    t = template.read(p)
    order = np.lexsort(t.pos.T[::-1])
    assert [t.keys()[i] for i in order] == [STAIR, "minecraft:air", "minecraft:stone", STAIR]
    (r,) = t.block_nbt
    assert t.pos[r].tolist() == [1, 0, 0]


def test_zones_floors_and_paint(exported):
    tmp, cfg_path, man, _ = exported
    cfg = X.load_town(cfg_path)
    cols = roundtrip.read_columns(cfg)
    y0 = man["source"]["box_min"][1]

    def col(x, z):
        return int(cols["zone"][z, x]), int(cols["floor"][z, x]) + y0, int(cols["top"][z, x]) + y0

    assert col(6, 6) == (X.MOLD, 64 - 2, 69)
    assert col(5, 5)[:2] == (X.MOLD, 57 - 2)               # cut per column, under the cellar block
    assert col(25, 25) == (X.SEA, 50 - 2, SEA)             # the painted seabed counts as ground
    # pier: the water under it down to the seabed; the cobble seabed right under the deck has a placed block
    # above it, so the survey's paint rule counts it as built and the floor goes one lower
    assert col(24, 16) == (X.MOLD, 49 - 2, 64)
    assert col(10, 6) == (X.SUPPORT, 62, 64)
    assert col(1, 30)[0] == X.BLEND
    assert y0 == 47 and man["size"] == [32, 69 - 47 + 1, 32]
    assert man["waterfront"]["quay_line"] == 20 and man["levels"]["sea_level"] == SEA - 47


def test_templates_hold_the_source(exported):
    tmp, cfg_path, man, cells = exported
    cfg = X.load_town(cfg_path)
    grid, keys, bes = roundtrip.intended(cfg, man)
    y0 = man["source"]["box_min"][1]
    n = 0
    for (yy, zz, xx) in zip(*np.nonzero(grid >= 0)):
        p = (int(xx), int(yy) + y0, int(zz))
        want = "minecraft:air" if p == (18, 65, 16) else cells.get(p, "minecraft:air")   # the marker is taken out
        assert keys[grid[yy, zz, xx]] == want, p
        n += 1
    assert n == man["stats"]["blocks"]
    chest = bes[(6, 66 - y0, 6)]
    assert chest["id"].value == "minecraft:chest" and "x" not in chest
    assert chest["Items"].value[1][0]["count"].value == 3


def test_markers_entities_and_manifest(exported):
    tmp, cfg_path, man, _ = exported
    y0 = man["source"]["box_min"][1]
    mk = {m["id"]: m for m in man["markers"]}
    assert mk["test_quay"]["pos"] == [18, 65 - y0, 16] and mk["test_quay"]["yaw"] == 270.0
    assert mk["test_quay"]["note"] == "the quay" and mk["test_quay"]["authored_as"] == "sign"
    assert mk["test_square"]["authored_as"] == "seed" and mk["test_square"]["pos"] == [12, 65 - y0, 12]
    assert man["stats"]["entities"] == 1
    assert man["stats"]["entities_dropped"] == {"minecraft:zombie": 1, "minecraft:villager": 1}
    assert len(man["tiles"]) == 3 and man["footprint"]["chunks_x"] == 2   # the all-blend tile is not written
    assert any(e[4] == "sea" for e in man["blend"]["edge"])
    sdir, _, _ = X.resource_dirs(X.load_town(cfg_path))
    ents = [e for t in os.listdir(sdir) for e in template.read(os.path.join(sdir, t)).entities]
    (pos, bpos, e), = ents
    assert e["id"].value == "minecraft:item_frame" and "UUID" not in e.value and bpos == [7, 67 - y0, 3]


def paste_world(folder, cfg, man, origin, base, edits=None):
    """What the workshop paste does, done offline: base world; each stored column cleared above its top and
    filled with stone below its floor; the template blocks and block entities; a sign for every marker."""
    ox, oy, oz = origin
    grid, keys, bes = roundtrip.intended(cfg, man)
    cols = roundtrip.read_columns(cfg)
    cells = dict(base)
    H, D, W = grid.shape
    for zz in range(D):
        for xx in range(W):
            if cols["zone"][zz, xx] == 0:
                continue
            for y in range(oy + int(cols["top"][zz, xx]) + 1, 128):
                cells.pop((ox + xx, y, oz + zz), None)
            for y in range(38, oy + int(cols["floor"][zz, xx])):
                cells[(ox + xx, y, oz + zz)] = "minecraft:stone"
    for (yy, zz, xx) in zip(*np.nonzero(grid >= 0)):
        cells[(ox + int(xx), oy + int(yy), oz + int(zz))] = keys[grid[yy, zz, xx]]
    be_list = []
    for (x, y, z), data in bes.items():
        d = {k: (v.tag, v.plain()) for k, v in data.items() if k != "Items"}
        if "Items" in data:
            d["Items"] = [{"Slot": (nbt.TAG_BYTE, 0), "id": "minecraft:bread", "count": 3}]
        be_list.append({**d, "x": ox + x, "y": oy + y, "z": oz + z})
    for m in man["markers"]:
        x, y, z = m["pos"]
        rotation = int(m["yaw"] / 22.5 + 0.5) & 15                      # as TownPaste places it
        cells[(ox + x, oy + y, oz + z)] = f"minecraft:oak_sign[rotation={rotation},waterlogged=false]"
        be_list.append({"x": ox + x, "y": oy + y, "z": oz + z, "id": "minecraft:sign",
                        "front_text": {"messages": ["@" + m["id"], m.get("note", "")[:15], "", ""]}})
    ents = []
    sdir, _, _ = X.resource_dirs(cfg, man["version"])
    for t in man["tiles"]:
        tx, _, tz = t["offset"]
        for p, bp, e in template.read(os.path.join(sdir, t["id"].rsplit("/", 1)[1] + ".nbt")).entities:
            ent = {k: v for k, v in e.value.items() if k != "block_pos"}
            ent["Pos"] = nbt.lst(nbt.TAG_DOUBLE, [nbt.d(ox + tx + p[0]), nbt.d(oy + p[1]), nbt.d(oz + tz + p[2])])
            ent["block_pos"] = nbt.int_array([ox + tx + bp[0], oy + bp[1], oz + tz + bp[2]])
            ents.append(nbt.compound(ent))
    cells.update(edits or {})
    write_world(folder, cells, be_list, [])
    # entities as typed compounds, written beside the blocks
    if ents:
        ent_dir = os.path.join(folder, "dimensions", "minecraft", "overworld", "entities")
        by_chunk = {}
        for e in ents:
            x, _, z = (v.value for v in e["Pos"].value[1])
            by_chunk.setdefault((int(x) >> 4, int(z) >> 4), []).append(e)
        from conftest import region_bytes
        chunks = {}
        for (cx, cz), es in by_chunk.items():
            root = nbt.c(DataVersion=nbt.i(5023), Position=nbt.int_array([cx, cz]), Entities=nbt.lst(nbt.TAG_COMPOUND, es))
            chunks.setdefault((cx >> 5, cz >> 5), {})[(cx & 31, cz & 31)] = nbt.root_bytes(root)
        for (rx, rz), cs in chunks.items():
            with open(os.path.join(ent_dir, f"r.{rx}.{rz}.mca"), "wb") as f:
                f.write(region_bytes(cs))


def base_sea(x0):
    return {(x, y, z): ("minecraft:water" if y > 40 else "minecraft:stone")
            for x in range(x0, x0 + 32) for z in range(0, 32) for y in range(38, SEA + 1)}


def test_roundtrip_and_byte_identical_reexport(exported):
    tmp, cfg_path, man, _ = exported
    cfg = X.load_town(cfg_path)
    pasted = os.path.join(tmp, "workshop")
    origin = [64, man["source"]["box_min"][1], 0]          # chunk-aligned like a workshop paste
    paste_world(pasted, cfg, man, origin, base_sea(64))
    report = os.path.join(tmp, "rt.json")
    assert roundtrip.run(cfg_path, pasted, ",".join(map(str, origin)), report=report, **QUIET)
    res = json.load(open(report))
    assert res["templates_vs_pasted"]["mismatched_blocks"] == 0
    assert res["source_vs_pasted"]["mismatched_blocks"] == 0
    assert res["markers"]["found"] == 2 and not res["markers"]["moved"] and not res["markers"]["missing"]

    # the pasted copy as the workshop: an unedited re-export writes identical files
    out = os.path.join(tmp, "again")
    X.run(cfg_path, prefer="workshop", world=pasted, origin=origin, out=out, **QUIET)
    a = os.path.join(tmp, "out")
    for sub in ("structure/towns/test/harbour/v1", "towns/test/harbour/v1"):
        for f in os.listdir(os.path.join(a, sub)):
            if f.endswith(".nbt"):
                assert open(os.path.join(a, sub, f), "rb").read() == open(os.path.join(out, sub, f), "rb").read(), f
    m2 = json.load(open(os.path.join(out, "towns/test/harbour/v1/manifest.json")))
    assert [dict(m, authored_as="") for m in m2["markers"]] == [dict(m, authored_as="") for m in man["markers"]]


def test_workshop_edit_is_exported(exported):
    tmp, cfg_path, man, _ = exported
    cfg = X.load_town(cfg_path)
    pasted = os.path.join(tmp, "workshop")
    origin = [64, man["source"]["box_min"][1], 0]
    # the user's edits: a planks hut on a blend column (template x 1, z 30), and a new marker sign
    hut = {(65, y, 30): "minecraft:oak_planks" for y in range(65, 68)}
    paste_world(pasted, cfg, man, origin, base_sea(64), edits=hut)
    out = os.path.join(tmp, "edited")
    man2 = X.run(cfg_path, prefer="workshop", world=pasted, origin=origin, out=out, **QUIET)
    cfg2 = dict(cfg, output_root=out)
    cols = roundtrip.read_columns(cfg2)
    assert cols["zone"][30, 1] == X.MOLD
    grid, keys, _ = roundtrip.intended(cfg2, man2)
    assert keys[grid[66 - man2["source"]["box_min"][1], 30, 1]] == "minecraft:oak_planks"
    assert man2["stats"]["columns"]["mold"] == man["stats"]["columns"]["mold"] + 1


def test_shipped_town_is_not_overwritten(exported):
    tmp, cfg_path, man, _ = exported
    cfg = json.load(open(cfg_path))
    cfg["shipped"] = True
    json.dump(cfg, open(cfg_path, "w"))
    with pytest.raises(SystemExit):
        X.run(cfg_path, **QUIET)
    X.run(cfg_path, new_version=True, **QUIET)
    assert os.path.exists(os.path.join(tmp, "out", "towns/test/harbour/v2/manifest.json"))
    assert json.load(open(cfg_path))["version"] == 2


def test_library_building(tmp_path, monkeypatch):
    tmp = str(tmp_path)
    world = os.path.join(tmp, "src")
    cells, bes, ents = scene()
    write_world(world, cells, bes, ents)
    lab = np.zeros((32, 32), np.uint16)
    lab[4:9, 4:9] = 7
    np.savez(os.path.join(tmp, "labels.npz"), labels=lab, x0=0, z0=0, sea_level=SEA,
             districts=np.array(json.dumps({"7": "T"})))
    with open(os.path.join(tmp, "catalog.csv"), "w") as f:
        f.write("id,district,type,x0,z0,x1,z1,storeys,workstations,doors,waterfront,entrance,entrance_basis\n"
                "7,T,house,4,4,8,8,1,0,0,0,E,street\n")
    monkeypatch.setattr(library, "CATALOG", os.path.join(tmp, "catalog.csv"))
    monkeypatch.setattr(library, "LABELS", os.path.join(tmp, "labels.npz"))
    man = library.export_building(7, world, os.path.join(tmp, "lib"), **QUIET)
    # the house plus its entrance stair (a loose built column next to it); the cellar column starts under its block
    assert man["size"][0] == 6 and man["size"][2] == 5 and man["footprint"]["columns"] == 26
    assert man["entrance"]["side"] == "E" and man["stats"]["block_entities"] == 1
    t = template.read(os.path.join(tmp, "lib", "building_7", "v1.nbt"))
    assert CHEST in t.keys() and STAIR in t.keys() and "minecraft:water" not in t.keys()


def test_marker_text_forms():
    from buildimport import markers as M
    assert M.component_text('{"text":"@a","extra":["b"]}') == "@ab"
    assert M.component_text({"text": "", "extra": ["@quay"]}) == "@quay"
    be = nbt.c(id=nbt.s("minecraft:sign"), front_text=nbt.c(messages=nbt.lst(nbt.TAG_STRING, [
        nbt.s("@Main Gate"), nbt.s("west"), nbt.s(""), nbt.s("")])))
    m = M.marker_of(be, "minecraft:oak_wall_sign[facing=east,waterlogged=false]")
    assert m == {"id": "main_gate", "yaw": 270.0, "authored_as": "sign", "note": "west"}
    sb = nbt.c(id=nbt.s("minecraft:structure_block"), mode=nbt.s("DATA"), metadata=nbt.s("inn"))
    assert M.marker_of(sb, "minecraft:structure_block[mode=data]")["id"] == "inn"
    plain = nbt.c(id=nbt.s("minecraft:sign"), front_text=nbt.c(messages=nbt.lst(nbt.TAG_STRING, [nbt.s("Lager")])))
    assert M.marker_of(plain, "minecraft:oak_sign") is None
