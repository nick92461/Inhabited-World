"""Block classification rules: what counts as natural terrain, what counts as built. EDIT HERE to tune the survey.

Every block key (a block name, sometimes with one kept property, see anvil.KEPT_PROPERTIES) gets exactly one class:

    AIR         air and invisible map-maker blocks; treated as empty space
    FLUID       water, lava, bubble columns
    VEGETATION  natural plants: leaves that were not placed by a player, grass, flowers, kelp...
                never counted as part of a build, and never a floor or ceiling
    NATURAL     natural terrain and tree trunks: stone, dirt, sand, ores, upright logs...
                counts as part of a build only under the two "attached" rules below
    PLACED      anything else: a block type natural terrain generation does not produce
                (planks, bricks, glass, slabs, stairs, wool, terracotta, crops, rails...)
    FURNISHING  placed blocks that furnish a room (beds, chests, workstations, carpets, pots...)
    LIGHT       placed light sources (torches, lanterns, lamps)

The rule is a whitelist of NATURAL / VEGETATION / FLUID / AIR blocks; everything not listed is PLACED. That makes an
unknown or modded block count as built, which is the safe mistake for a survey (it gets a look rather than being
silently dropped).

Natural blocks count as built when ATTACHED to a build (applied in scan.py):
  rule A, enclosed:  a NATURAL block at or above the lowest placed block of its column (dirt in a roof planter,
                     a plain-stone wall course standing on a placed floor)
  rule B, attached:  a NATURAL block face-adjacent to a placed block and more than ATTACH_MIN_ABOVE_GROUND blocks
                     above the chunk's open ground (a plain-stone wall standing straight on the terrain)

And placed blocks count as natural where they are TERRAIN PAINT (rule C, also in scan.py). Map makers (WorldPainter
and similar) texture terrain with building blocks: Drachenfels' seabed and rocks are smooth sandstone, cobblestone
and mossy cobblestone. A TERRAIN_PAINT block with no other placed block above it in its column is natural when
  C1  there is water above it (seabed), or
  C2  it lies in a layer at most PAINT_MAX_THICKNESS thick directly on natural ground, with at least 3 of its 4
      horizontal neighbours solid (a surface layer, not a free-standing wall), or
  C3  natural blocks are directly above and below it (an inclusion in rock).
Cobble walls, foundations and anything under a building stay built.
"""
from __future__ import annotations

import re

AIR, FLUID, VEGETATION, NATURAL, PLACED, FURNISHING, LIGHT = range(7)
CLASS_NAMES = ["air", "fluid", "vegetation", "natural", "placed", "furnishing", "light"]
PLACED_CLASSES = (PLACED, FURNISHING, LIGHT)

ATTACH_MIN_ABOVE_GROUND = 2

TERRAIN_PAINT = {"cobblestone", "mossy_cobblestone", "smooth_sandstone"}
PAINT_MAX_THICKNESS = 2

# ---- empty space ----
AIR_BLOCKS = {"air", "cave_air", "void_air", "light", "structure_void", "barrier"}

FLUID_BLOCKS = {"water", "lava", "bubble_column"}

# ---- natural terrain (solid; may become built by the attached rules) ----
NATURAL_BLOCKS = {
    "stone", "granite", "diorite", "andesite", "deepslate", "tuff", "calcite", "bedrock",
    "dripstone_block", "pointed_dripstone", "smooth_basalt", "obsidian", "magma_block",
    "dirt", "coarse_dirt", "rooted_dirt", "podzol", "mycelium", "grass_block", "mud", "clay",
    "gravel", "sand", "red_sand", "sandstone", "red_sandstone", "suspicious_sand", "suspicious_gravel",
    "snow", "snow_block", "powder_snow", "ice", "packed_ice", "blue_ice",
    "amethyst_block", "budding_amethyst", "moss_block", "pale_moss_block",
    "sculk", "sculk_vein", "raw_iron_block", "raw_copper_block",
}
NATURAL_PATTERNS = [
    r".*_ore",                         # coal_ore, deepslate_iron_ore, ...
    r"infested_(stone|deepslate)",    # infested bricks / cobble are building blocks (placed)
    r".*_log\[axis=y\]", r".*_log",     # upright trunks; sideways logs are matched as PLACED first (below)
    r"mangrove_roots", r"muddy_mangrove_roots",
]

# ---- natural plants (never built) ----
VEGETATION_BLOCKS = {
    "short_grass", "grass", "tall_grass", "fern", "large_fern", "dead_bush", "bush", "firefly_bush",
    "short_dry_grass", "tall_dry_grass", "leaf_litter", "wildflowers", "pink_petals", "cactus_flower",
    "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower",
    "lily_of_the_valley", "sunflower", "lilac", "rose_bush", "peony", "open_eyeblossom", "closed_eyeblossom",
    "vine", "glow_lichen", "kelp", "kelp_plant", "seagrass", "tall_seagrass", "sea_pickle", "lily_pad",
    "sugar_cane", "bamboo", "cactus", "brown_mushroom", "red_mushroom",
    "brown_mushroom_block", "red_mushroom_block", "mushroom_stem", "sweet_berry_bush",
    "cave_vines", "cave_vines_plant", "hanging_roots", "spore_blossom", "big_dripleaf", "big_dripleaf_stem",
    "small_dripleaf", "azalea", "flowering_azalea", "moss_carpet", "pale_moss_carpet", "pale_hanging_moss",
    "pumpkin", "melon", "bee_nest", "cocoa", "amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud",
    "small_amethyst_bud", "resin_clump",
}
VEGETATION_PATTERNS = [
    r".*_leaves\[persistent=false\]", r".*_leaves",   # player-placed leaves are matched as PLACED first
    r"(red|orange|white|pink)_tulip", r"(dead_)?(tube|brain|bubble|fire|horn)_coral(_block|_fan|_wall_fan)?",   # reefs (dead_ variants included)
]

# ---- placed, checked BEFORE the natural lists (overrides) ----
PLACED_OVERRIDES = [
    r".*_leaves\[persistent=true\]",   # leaves placed by a player (hedges, tree decoration)
    r".*_log\[axis=[xz]\]",            # sideways logs: beams
    r"stripped_.*",                    # stripped logs and wood never generate
]

# ---- placed sub-classes, for room and capacity statistics ----
FURNISHING_PATTERNS = [
    r".*_bed(\[.*\])?", r"(trapped_)?chest", r"ender_chest", r"barrel", r".*shulker_box",
    r"crafting_table", r"furnace", r"blast_furnace", r"smoker", r"(chiseled_)?bookshelf", r"lectern",
    r"(chipped_|damaged_)?anvil", r".*cauldron", r"brewing_stand", r"enchanting_table", r"loom",
    r"cartography_table", r"fletching_table", r"smithing_table", r"grindstone", r"stonecutter", r"composter",
    r"flower_pot", r"potted_.*", r".*_carpet", r".*_banner", r".*_wall_banner", r".*_sign", r".*_hanging_sign",
    r".*_wall_sign", r".*_wall_hanging_sign", r"(?!piston).*_head", r".*_skull", r".*_wall_head", r".*_wall_skull",
    r"bell", r"jukebox", r"note_block", r"decorated_pot", r".*cake", r"beehive", r"crafter",
    r"armor_stand",
]
LIGHT_PATTERNS = [
    r"(soul_|redstone_|copper_)?(wall_)?torch", r"(soul_)?lantern", r".*copper_lantern", r"end_rod",
    r".*candle", r"(soul_)?campfire", r"glowstone", r"sea_lantern", r"redstone_lamp", r".*froglight",
    r"shroomlight", r"jack_o_lantern", r"copper_bulb", r".*_copper_bulb",
]
# Furnishing / light blocks that are not full cubes: a room's air passes through them (a bed does not end a room).
PASSABLE_PLACED = FURNISHING_PATTERNS + LIGHT_PATTERNS[:5]

# Flags used for counting
BED_HEAD = r".*_bed\[part=head\]"
BED_ANY = r".*_bed(\[.*\])?"
DOOR = r".*_door"
WORKSTATION = [
    r"blast_furnace", r"smoker", r"cartography_table", r"brewing_stand", r"composter", r"barrel",
    r"fletching_table", r".*cauldron", r"lectern", r"stonecutter", r"loom", r"smithing_table", r"grindstone",
    r"crafting_table", r"furnace", r"(chipped_|damaged_)?anvil",
]

# ---- 1.12 and older numeric block IDs (only what flat or plain terrain needs; others become legacy:<id>) ----
LEGACY_IDS = {
    0: "air", 1: "stone", 2: "grass_block", 3: "dirt", 7: "bedrock", 8: "water", 9: "water", 10: "lava",
    11: "lava", 12: "sand", 13: "gravel", 14: "gold_ore", 15: "iron_ore", 16: "coal_ore", 17: "oak_log",
    18: "oak_leaves", 21: "lapis_ore", 24: "sandstone", 31: "short_grass", 32: "dead_bush", 37: "dandelion",
    38: "poppy", 39: "brown_mushroom", 40: "red_mushroom", 56: "diamond_ore", 73: "redstone_ore",
    78: "snow", 79: "ice", 80: "snow_block", 81: "cactus", 82: "clay", 83: "sugar_cane", 86: "pumpkin",
    106: "vine", 110: "mycelium", 111: "lily_pad", 129: "emerald_ore", 161: "acacia_leaves", 162: "acacia_log",
    174: "packed_ice", 175: "sunflower",
}


def _full(patterns):
    return re.compile("^(?:" + "|".join(patterns) + ")$")


_PLACED_OVERRIDE = _full(PLACED_OVERRIDES)
_NATURAL = _full(NATURAL_PATTERNS)
_VEGETATION = _full(VEGETATION_PATTERNS)
_FURNISHING = _full(FURNISHING_PATTERNS)
_LIGHT = _full(LIGHT_PATTERNS)
_PASSABLE = _full(PASSABLE_PLACED)
_BED_HEAD = _full([BED_HEAD])
_BED_ANY = _full([BED_ANY])
_DOOR = _full([DOOR])
_WORKSTATION = _full(WORKSTATION)


def short(key: str) -> str:
    """'minecraft:oak_log[axis=y]' -> 'oak_log[axis=y]'; other namespaces are kept."""
    return key[10:] if key.startswith("minecraft:") else key


def base_name(key: str) -> str:
    s = short(key)
    i = s.find("[")
    return s if i < 0 else s[:i]


def classify(key: str) -> int:
    """The class of one block key. Pure function of the key; the lists above are the whole rule set."""
    s = short(key)
    name = base_name(key)
    if name in AIR_BLOCKS:
        return AIR
    if name in FLUID_BLOCKS:
        return FLUID
    if s.startswith("legacy:"):
        return PLACED
    if _PLACED_OVERRIDE.match(s):
        return PLACED
    if name in VEGETATION_BLOCKS or _VEGETATION.match(s):
        return VEGETATION
    if name in NATURAL_BLOCKS or _NATURAL.match(s):
        return NATURAL
    if _LIGHT.match(name):
        return LIGHT
    if _FURNISHING.match(s) or _FURNISHING.match(name):
        return FURNISHING
    return PLACED


def is_passable(key: str) -> bool:
    """True for air, plants, and placed blocks a room's air runs through (beds, carpets, torches...)."""
    c = classify(key)
    if c in (AIR, VEGETATION):
        return True
    return c in (FURNISHING, LIGHT) and bool(_PASSABLE.match(base_name(key)))


def flags(key: str) -> tuple[bool, bool, bool, bool]:
    """(bed head, any bed block, door block, workstation)."""
    s = short(key)
    name = base_name(key)
    return (bool(_BED_HEAD.match(s)), bool(_BED_ANY.match(s)), bool(_DOOR.match(name)),
            bool(_WORKSTATION.match(name)))


def is_paint(key: str) -> bool:
    return base_name(key) in TERRAIN_PAINT
