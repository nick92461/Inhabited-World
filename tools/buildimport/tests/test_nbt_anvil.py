import gzip

import numpy as np
import pytest

from conftest import chunk_26, chunk_legacy, pack, region_bytes, section_26
from buildimport import anvil, nbt


def test_nbt_round_trip_types():
    value = {
        "b": (nbt.TAG_BYTE, -3), "s": (nbt.TAG_SHORT, 1234), "i": 70000, "l": (nbt.TAG_LONG, -(1 << 40)),
        "f": (nbt.TAG_FLOAT, 1.5), "d": 2.25, "str": "minecraft:oak_planks", "list": ["a", "b"],
        "nested": {"x": 1}, "longs": (nbt.TAG_LONG_ARRAY, np.array([1, -1], ">i8")),
        "ints": (nbt.TAG_INT_ARRAY, np.array([7, 8, 9], ">i4")), "empty": [],
    }
    out = nbt.parse(nbt.encode_root(value))
    assert out["b"] == -3 and out["s"] == 1234 and out["i"] == 70000 and out["l"] == -(1 << 40)
    assert out["f"] == 1.5 and out["d"] == 2.25 and out["str"] == "minecraft:oak_planks"
    assert out["list"] == ["a", "b"] and out["nested"] == {"x": 1} and out["empty"] == []
    assert list(out["longs"]) == [1, -1] and list(out["ints"]) == [7, 8, 9]


def test_nbt_unwraps_mixed_lists_and_gzip():
    raw = nbt.encode_root({"palette": [{"": "minecraft:dirt"}, {"Name": "minecraft:oak_log"}]})
    assert nbt.parse(nbt.decompress(gzip.compress(raw)))["palette"] == ["minecraft:dirt", {"Name": "minecraft:oak_log"}]


@pytest.mark.parametrize("palette_size", [2, 16, 17, 33, 300])
@pytest.mark.parametrize("spanning", [False, True])
def test_unpack_indices_matches_reference_packer(palette_size, spanning):
    rng = np.random.default_rng(palette_size)
    idx = rng.integers(0, palette_size, 4096)
    got = anvil.unpack_indices(pack(idx, palette_size, spanning), palette_size, spanning=spanning)
    assert np.array_equal(got, idx)


def test_block_key_keeps_only_survey_properties():
    assert anvil.block_key("minecraft:stone") == "minecraft:stone"
    assert anvil.block_key("oak_planks") == "minecraft:oak_planks"
    assert anvil.block_key({"Name": "minecraft:oak_leaves", "Properties": {"persistent": "true", "distance": "7"}}) \
        == "minecraft:oak_leaves[persistent=true]"
    assert anvil.block_key({"Name": "minecraft:red_bed", "Properties": {"part": "head", "facing": "east"}}) \
        == "minecraft:red_bed[part=head]"
    assert anvil.block_key({"Name": "minecraft:spruce_log", "Properties": {"axis": "x"}}) == "minecraft:spruce_log[axis=x]"
    assert anvil.block_key({"Name": "minecraft:oak_stairs", "Properties": {"facing": "east"}}) == "minecraft:oak_stairs"


def test_region_file_with_modern_and_legacy_chunks(tmp_path):
    blocks = np.zeros((16, 16, 16), int)
    blocks[0] = 1                    # stone floor at section y 0
    blocks[1, 3, 5] = 2              # one log above it at x=5, z=3
    sec = section_26(4, blocks, ["minecraft:air", "minecraft:stone",
                                 {"Name": "minecraft:oak_log", "Properties": {"axis": "z"}}])
    legacy_ids = np.zeros(4096, int)
    legacy_ids[:256] = 7             # bedrock layer
    legacy_ids[256:512] = 2          # grass
    data = region_bytes({(1, 2): chunk_26(33, 66, [sec], blending=True), (0, 0): chunk_legacy(32, 64, 0, legacy_ids)})
    path = tmp_path / "r.1.2.mca"
    path.write_bytes(data)
    assert anvil.region_files(tmp_path) == [(1, 2, str(path))]
    chunks = {(c.cx, c.cz): c for c in anvil.iter_chunks(tmp_path)}
    modern = chunks[(33, 66)]
    assert modern.data_version == 5023 and modern.blending and modern.status == "minecraft:full"
    s = modern.sections[0]
    assert s.y == 4
    names = [s.palette[i] for i in s.indices]
    assert names[0] == "minecraft:stone"
    assert names[1 * 256 + 3 * 16 + 5] == "minecraft:oak_log[axis=z]"
    assert names[1 * 256 + 3 * 16 + 6] == "minecraft:air"
    legacy = chunks[(32, 64)]
    ls = legacy.sections[0]
    lnames = [ls.palette[i] for i in ls.indices]
    assert lnames[0] == "minecraft:bedrock" and lnames[300] == "minecraft:grass_block" and lnames[600] == "minecraft:air"


def test_unknown_legacy_id_is_marked():
    assert anvil.legacy_key(5) == "legacy:5"
    assert anvil.legacy_key(3) == "minecraft:dirt"
