"""Approximate map colours for block keys (top-down preview only; not exact Minecraft map colours)."""
from __future__ import annotations

import numpy as np

from . import rules

_DYES = {
    "white": (233, 236, 236), "light_gray": (142, 142, 134), "gray": (62, 68, 71), "black": (20, 21, 25),
    "brown": (114, 71, 40), "red": (160, 39, 34), "orange": (240, 118, 19), "yellow": (248, 197, 39),
    "lime": (112, 185, 25), "green": (84, 109, 27), "cyan": (21, 137, 145), "light_blue": (58, 175, 217),
    "blue": (53, 57, 157), "purple": (121, 42, 172), "magenta": (189, 68, 179), "pink": (237, 141, 172),
}
_TERRACOTTA = {
    "white": (209, 178, 161), "light_gray": (135, 107, 98), "gray": (57, 42, 35), "black": (37, 22, 16),
    "brown": (77, 51, 35), "red": (143, 61, 46), "orange": (161, 83, 37), "yellow": (186, 133, 35),
    "lime": (103, 117, 52), "green": (76, 83, 42), "cyan": (86, 91, 91), "light_blue": (113, 108, 137),
    "blue": (74, 59, 91), "purple": (118, 70, 86), "magenta": (149, 88, 108), "pink": (161, 78, 78),
}
_WOOD = {
    "oak": (162, 130, 78), "spruce": (114, 84, 48), "birch": (196, 179, 123), "jungle": (160, 115, 80),
    "acacia": (168, 90, 50), "dark_oak": (66, 43, 20), "mangrove": (117, 54, 48), "cherry": (226, 178, 172),
    "bamboo": (193, 173, 80), "crimson": (101, 48, 70), "warped": (43, 104, 99), "pale_oak": (220, 210, 205),
}
_EXACT = {
    "water": (52, 92, 196), "lava": (207, 92, 20), "grass_block": (106, 152, 62), "dirt": (134, 96, 67),
    "coarse_dirt": (119, 85, 59), "podzol": (91, 63, 24), "dirt_path": (148, 122, 65), "farmland": (110, 75, 45),
    "stone": (125, 125, 125), "cobblestone": (122, 122, 122), "mossy_cobblestone": (110, 118, 94),
    "stone_bricks": (122, 121, 122), "gravel": (131, 127, 126), "sand": (219, 207, 163),
    "sandstone": (216, 203, 155), "snow": (249, 254, 254), "snow_block": (249, 254, 254), "ice": (145, 183, 253),
    "packed_ice": (141, 180, 250), "clay": (160, 166, 179), "bricks": (150, 97, 83), "glass": (200, 220, 230),
    "quartz_block": (235, 229, 222), "bedrock": (85, 85, 85), "andesite": (136, 136, 137),
    "diorite": (188, 188, 188), "granite": (149, 103, 85), "netherrack": (97, 38, 38), "hay_block": (166, 136, 38),
    "terracotta": (152, 94, 67), "smooth_stone": (158, 158, 158), "deepslate": (80, 80, 82),
    "prismarine": (99, 156, 151), "dark_prismarine": (51, 91, 75), "iron_block": (220, 220, 220),
    "gold_block": (246, 208, 61), "lily_pad": (32, 128, 48), "nether_bricks": (44, 21, 26),
    "end_stone": (219, 222, 158), "red_sand": (190, 102, 33), "mud": (60, 57, 61), "moss_block": (89, 109, 45),
    "blackstone": (42, 36, 41), "polished_andesite": (132, 135, 133), "polished_diorite": (192, 193, 194),
    "polished_granite": (154, 106, 89), "obsidian": (15, 10, 24),
}
_KEYWORDS = [
    ("leaves", (60, 104, 40)), ("grass", (95, 140, 55)), ("fern", (88, 130, 50)), ("flower", (180, 160, 60)),
    ("tulip", (200, 90, 80)), ("kelp", (40, 110, 60)), ("seagrass", (40, 110, 60)), ("glass", (190, 210, 220)),
    ("stone_brick", (122, 121, 122)), ("cobblestone", (122, 122, 122)), ("deepslate", (78, 78, 82)),
    ("quartz", (232, 227, 218)), ("sandstone", (216, 203, 155)), ("brick", (150, 97, 83)),
    ("andesite", (136, 136, 137)), ("diorite", (188, 188, 188)), ("granite", (149, 103, 85)),
    ("stone", (125, 125, 125)), ("prismarine", (90, 150, 140)), ("copper", (190, 110, 80)),
    ("iron", (200, 200, 200)), ("torch", (250, 200, 80)), ("lantern", (250, 200, 80)), ("rail", (120, 110, 90)),
]


def color_of(key: str) -> tuple[int, int, int]:
    name = rules.base_name(key)
    if name in _EXACT:
        return _EXACT[name]
    for dye, rgb in _DYES.items():
        if name.startswith(dye + "_"):
            rest = name[len(dye) + 1:]
            if "terracotta" in rest:
                return _TERRACOTTA[dye]
            return rgb
    for wood, rgb in sorted(_WOOD.items(), key=lambda t: -len(t[0])):
        if name.startswith(wood + "_") or name.startswith("stripped_" + wood + "_"):
            if name.endswith("_leaves"):
                return (60, 104, 40)
            if name.endswith("_log") or name.endswith("_wood"):
                return tuple(int(c * 0.75) for c in rgb)
            return rgb
    for word, rgb in _KEYWORDS:
        if word in name:
            return rgb
    c = rules.classify(key)
    return {rules.VEGETATION: (80, 130, 50), rules.NATURAL: (128, 118, 100), rules.FLUID: (52, 92, 196)}.get(
        c, (170, 150, 130))


def palette(keys: list[str]) -> np.ndarray:
    return np.array([color_of(k) for k in keys], np.uint8)
