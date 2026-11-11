"""Generates the worldgen data of inhabited_world:rpg_overworld_v2 (P2A step 4: the island and its ocean).

The output is versioned configuration (canon 02 section 6): once shipped, it is never regenerated or edited in place.
A change makes rpg_overworld_v3 with a new script. This script is kept to show exactly how v2 was made.

usage:  python -I make_rpg_overworld_v2.py <vanilla worldgen dir> <mod resources dir>
  <vanilla worldgen dir>: data/minecraft/worldgen extracted from the Minecraft 26.3 jar
  <mod resources dir>:    inhabited_world_mod/src/main/resources

What it makes (all vanilla 26.3 density-function types; no custom code):
  - boundary_distance: a rounded-square ("squircle", power 4) distance from the world origin in blocks, roughened by
    a coast noise of +-48 blocks so the coastline is irregular
  - continents: vanilla continentalness, with low ground raised to low land (max with -0.05) so seeds whose middle is
    sea still get an island, then faded with a smoothstep to deep ocean (-0.75) between 300 and 490 blocks; beyond 490
    it is deep ocean outright, without sampling the noise
  - copies of vanilla's overworld offset, factor, jaggedness, depth, sloped_cheese, final_density,
    preliminary_surface_level and chunk_surface_level that read the shaped continents instead of vanilla's
  - the noise settings: our rpg_overworld (a verbatim vanilla copy) with its router and spawn targets pointed at the
    shaped functions, so terrain, biomes and the spawn search all see the same island
"""
import json
import os
import sys

NS = "inhabited_world"
PREFIX = "rpg_overworld_v2"

HALF_SIZE = 512          # the playable area's half width (PlayableArea.V0_1); distances are normalised by it
FADE_START = 300.0       # vanilla continentalness (lifted) up to here
FADE_END = 490.0         # deep ocean from here
COAST_NOISE_BLOCKS = 48.0
LIFT = -0.05             # "near inland": low land, never beach or sea
OCEAN = -0.75            # deep ocean, well above mushroom fields (< -1.05)

SHAPED = ["continents", "offset", "factor", "jaggedness", "depth", "sloped_cheese",
          "final_density", "preliminary_surface_level", "chunk_surface_level"]

vanilla, resources = sys.argv[1:3]


def ref(name):
    return "%s:%s/%s" % (NS, PREFIX, name)


def rename(text):
    for name in SHAPED:
        text = text.replace('"minecraft:overworld/%s"' % name, '"%s"' % ref(name))
    return text


def write(rel, obj):
    path = os.path.join(resources, "data", NS, "worldgen", rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def add(a, b):
    return {"type": "minecraft:add", "left": a, "right": b}


def mul(a, b):
    return {"type": "minecraft:mul", "left": a, "right": b}


def axis(name):
    # The block coordinate itself (clamped at +-1,000,000, far beyond the world border's limit).
    return {"type": "minecraft:gradient", "axis": name, "from_coordinate": -1000000, "to_coordinate": 1000000,
            "from_value": -1000000.0, "to_value": 1000000.0}


def pow4(value):
    return {"type": "minecraft:pow", "base": value, "exponent": 4.0}


write("noise/%s/coast.json" % PREFIX, {"base_octave": -7, "octave_count": 3, "amplitude_modifiers": [1.0, 0.5, 0.25]})

for name in SHAPED[1:]:
    with open(os.path.join(vanilla, "density_function", "overworld", name + ".json"), encoding="utf-8") as f:
        write("density_function/%s/%s.json" % (PREFIX, name), json.loads(rename(f.read())))

u = {"type": "minecraft:abs", "input": mul(axis("x"), 1.0 / HALF_SIZE)}
v = {"type": "minecraft:abs", "input": mul(axis("z"), 1.0 / HALF_SIZE)}
squircle = mul({"type": "minecraft:pow", "base": add(pow4(u), pow4(v)), "exponent": 0.25}, float(HALF_SIZE))
coast_noise = {"type": "minecraft:noise", "noise": ref("coast"), "xz_scale": 1.0, "y_scale": 0.0}
write("density_function/%s/boundary_distance.json" % PREFIX,
      {"type": "minecraft:cache", "input": add(squircle, mul(coast_noise, COAST_NOISE_BLOCKS))})

write("density_function/%s/coast_fade.json" % PREFIX, {
    "type": "minecraft:clamp",
    "input": mul(add(ref("boundary_distance"), -FADE_START), 1.0 / (FADE_END - FADE_START)),
    "min": 0.0, "max": 1.0})
smoothstep = mul(mul(ref("coast_fade"), ref("coast_fade")), add(3.0, mul(ref("coast_fade"), -2.0)))
write("density_function/%s/continents.json" % PREFIX, {"type": "minecraft:cache", "input": {
    "type": "minecraft:range_choice",
    "input": ref("boundary_distance"),
    "min_inclusive": -1000000.0,
    "max_exclusive": FADE_END,
    "when_in_range": {
        "type": "minecraft:lerp",
        "alpha": smoothstep,
        "first": {"type": "minecraft:max", "left": "minecraft:overworld/continents", "right": LIFT},
        "second": OCEAN},
    "when_out_of_range": OCEAN}})

with open(os.path.join(resources, "data", NS, "worldgen", "noise_settings", "rpg_overworld.json"), encoding="utf-8") as f:
    write("noise_settings/%s.json" % PREFIX, json.loads(rename(f.read())))
print("wrote", PREFIX)
