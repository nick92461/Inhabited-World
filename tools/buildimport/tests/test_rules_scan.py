import numpy as np
import pytest

from buildimport import anvil, rules
from buildimport.scan import KeyTable, analyse_chunk, NONE


@pytest.mark.parametrize("key,cls", [
    ("minecraft:stone", rules.NATURAL), ("minecraft:grass_block", rules.NATURAL),
    ("minecraft:oak_log", rules.NATURAL), ("minecraft:oak_log[axis=x]", rules.PLACED),
    ("minecraft:oak_leaves", rules.VEGETATION), ("minecraft:oak_leaves[persistent=true]", rules.PLACED),
    ("minecraft:short_grass", rules.VEGETATION), ("minecraft:red_tulip", rules.VEGETATION),
    ("minecraft:potted_red_tulip", rules.FURNISHING), ("minecraft:oak_planks", rules.PLACED),
    ("minecraft:stone_bricks", rules.PLACED), ("minecraft:infested_stone", rules.NATURAL),
    ("minecraft:infested_stone_bricks", rules.PLACED), ("minecraft:red_bed[part=head]", rules.FURNISHING),
    ("minecraft:chest", rules.FURNISHING), ("minecraft:white_carpet", rules.FURNISHING),
    ("minecraft:lantern", rules.LIGHT), ("minecraft:wall_torch", rules.LIGHT), ("minecraft:piston_head", rules.PLACED),
    ("minecraft:water", rules.FLUID), ("minecraft:cave_air", rules.AIR), ("minecraft:barrier", rules.AIR),
    ("legacy:5", rules.PLACED), ("somemod:fancy_block", rules.PLACED), ("minecraft:cobblestone", rules.PLACED),
])
def test_classify(key, cls):
    assert rules.classify(key) == cls


def test_flags_and_passable():
    assert rules.flags("minecraft:red_bed[part=head]") == (True, True, False, False)
    assert rules.flags("minecraft:red_bed") == (False, True, False, False)
    assert rules.flags("minecraft:spruce_door")[2]
    assert rules.flags("minecraft:smithing_table")[3]
    assert rules.is_passable("minecraft:air") and rules.is_passable("minecraft:white_carpet")
    assert not rules.is_passable("minecraft:oak_planks") and not rules.is_passable("minecraft:water")


# --- a synthetic chunk: grass ground at y 64, a small planks house, a cobble seabed under water, a cobble wall ---

def make_chunk(cells: dict) -> anvil.Chunk:
    """cells: {(x, y, z): key}; y from 0 to 127 (sections 0..7); everything else air."""
    keys = ["minecraft:air"] + sorted(set(cells.values()))
    sections = []
    for sy in range(8):
        idx = np.zeros(4096, np.uint16)
        for (x, y, z), k in cells.items():
            if sy * 16 <= y < sy * 16 + 16:
                idx[(y - sy * 16) * 256 + z * 16 + x] = keys.index(k)
        sections.append(anvil.Section(sy, keys, idx))
    return anvil.Chunk(0, 0, 5023, "minecraft:full", sections, True)


def scene():
    c = {}
    for x in range(16):
        for z in range(16):
            for y in range(60, 64):
                c[(x, y, z)] = "minecraft:dirt"
            c[(x, 64, z)] = "minecraft:grass_block"
    # house: x 2..6, z 2..6, floor at y 65, walls y 66..69, roof y 70; dirt planter on the roof (rule A)
    for x in range(2, 7):
        for z in range(2, 7):
            c[(x, 65, z)] = "minecraft:oak_planks"
            c[(x, 70, z)] = "minecraft:oak_planks"
            if x in (2, 6) or z in (2, 6):
                for y in range(66, 70):
                    c[(x, y, z)] = "minecraft:stone_bricks"
    c[(4, 71, 4)] = "minecraft:dirt"
    c[(3, 66, 3)] = "minecraft:red_bed[part=head]"
    # seabed: x 10..15, z 10..15: cobble at y 64 with water above up to y 66 (rule C1)
    for x in range(10, 16):
        for z in range(10, 16):
            c[(x, 64, z)] = "minecraft:cobblestone"
            c[(x, 65, z)] = "minecraft:water"
            c[(x, 66, z)] = "minecraft:water"
    # free-standing cobble wall x 10..15 at z 2, y 65..68: stays placed
    for x in range(10, 16):
        for y in range(65, 69):
            c[(x, y, 2)] = "minecraft:cobblestone"
    # a cobble road one block thick at z 7..8, x 9..15 (rule C2: becomes terrain)
    for x in range(9, 16):
        for z in (7, 8):
            c[(x, 64, z)] = "minecraft:cobblestone"
    return c


def col(res, name, x, z):
    return int(np.asarray(res.columns[name]).reshape(16, 16)[z, x])


def test_analyse_chunk_house_rooms_and_rules():
    keys = KeyTable()
    res = analyse_chunk(make_chunk(scene()), keys)
    # house centre: built from the floor (65) to the roof planter (71: dirt above placed, rule A)
    assert col(res, "built_bottom", 4, 4) == 65
    assert col(res, "built_top", 4, 4) == 71
    assert col(res, "ground_y", 4, 4) == 64
    # one room slice in the centre column: floor y 66, 4 high, under the roof
    runs = res.runs
    centre = runs[runs[:, 0] == 4 * 16 + 4]
    assert len(centre) == 1 and centre[0, 1] == 66 and centre[0, 2] == 4
    assert col(res, "interior_n", 4, 4) == 4 and col(res, "rooms_n", 4, 4) == 1
    # the bed column's room includes the bed (passable) and counts it as furnishing
    bedrun = runs[runs[:, 0] == 3 * 16 + 3]
    assert bedrun[0, 3] == 1 and col(res, "beds", 3, 3) == 1
    # open ground: nothing built, ground at the grass
    assert col(res, "built_n", 12, 5) == 0 and col(res, "ground_y", 12, 5) == 64
    # seabed cobble under water is terrain (C1); water recorded
    assert col(res, "placed_n", 12, 12) == 0 and col(res, "water_top", 12, 12) == 66 and col(res, "water_n", 12, 12) == 2
    # the cobble wall stays built; the thin cobble road becomes terrain (C2)
    assert col(res, "built_top", 12, 2) == 68
    assert col(res, "placed_n", 12, 7) == 0 and col(res, "ground_y", 12, 7) == 64
    # surface: the roof planter in the house, water on the sea
    assert col(res, "surface_y", 4, 4) == 71 and col(res, "surface_y", 12, 12) == 66


def test_empty_chunk_returns_none():
    ch = anvil.Chunk(0, 0, 5023, "minecraft:full", [anvil.Section(0, ["minecraft:air"], None)])
    assert analyse_chunk(ch, KeyTable()) is None


def test_no_ceiling_means_no_room():
    c = {(x, 64, z): "minecraft:grass_block" for x in range(16) for z in range(16)}
    for y in range(65, 70):
        c[(5, y, 5)] = "minecraft:oak_fence"   # a post, no roof: the air beside it is not a room
    res = analyse_chunk(make_chunk(c), KeyTable())
    assert len(res.runs) == 0
    assert col(res, "built_top", 5, 5) == 69 and col(res, "built_n", 6, 6) == 0
