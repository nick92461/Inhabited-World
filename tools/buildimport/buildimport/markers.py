"""Semantic markers (canon 02 section 16) authored in a workshop world.

How a marker is authored in game (creative mode):
  * a SIGN (any wood, standing, wall or hanging) whose first line starts with "@": "@smithy", "@d1_quay".
    The rest of line 1 is the marker id. Lines 2-4 are an optional note (ignored by the game, kept in the
    manifest). The sign's position is the marker's position; the direction its text faces is the marker's yaw
    (an NPC standing there looks that way).
  * or a STRUCTURE BLOCK in Data mode whose "Custom Data Tag Name" is the marker id (vanilla's own template marker).

Markers are not blocks of the town: the exporter removes them from the templates (air, or water if the sign was
waterlogged) and lists them in the manifest relative to the template origin. The workshop paste puts a sign back
for every marker, so a workshop world always holds all its markers.

Marker ids: lower-case letters, digits and underscores (anything else is turned into "_").
"""
from __future__ import annotations

import json
import re

from .nbt import T

PREFIX = "@"
_ID_BAD = re.compile(r"[^a-z0-9_]+")

# yaw in Minecraft degrees: 0 = facing south (+z), 90 = west, 180 = north, 270 = east
FACING_YAW = {"south": 0.0, "west": 90.0, "north": 180.0, "east": 270.0}


def component_text(v) -> str:
    """Plain text of a stored text component: an NBT string (plain or legacy JSON), a compound {text, extra}, or a
    list of components."""
    if isinstance(v, T):
        v = v.plain()
    if isinstance(v, str):
        st = v.strip()
        if st[:1] in ('{', '[', '"'):
            try:
                return component_text(json.loads(st))
            except ValueError:
                pass
        return v
    if isinstance(v, list):
        return "".join(component_text(x) for x in v)
    if isinstance(v, dict):
        if "" in v and len(v) == 1:
            return component_text(v[""])
        out = str(v.get("text", v.get("translate", "")))
        for e in v.get("extra", []) or []:
            out += component_text(e)
        return out
    return ""


def sign_lines(be: T) -> list[str]:
    """The four front lines of a sign block entity (26.x 'front_text.messages'; old 'Text1'..'Text4')."""
    ft = be.get("front_text")
    if ft is not None and ft.get("messages") is not None:
        return [component_text(m) for m in ft["messages"].value[1]]
    return [component_text(be.get(f"Text{i}", T(8, ""))) for i in range(1, 5)]


def normalise_id(s: str) -> str:
    return _ID_BAD.sub("_", s.strip().lower()).strip("_")


def yaw_of(state_key: str) -> float:
    from .world import split_state
    _, props = split_state(state_key)
    if "rotation" in props:
        return (int(props["rotation"]) * 22.5) % 360.0
    if "facing" in props:
        return FACING_YAW.get(props["facing"], 0.0)
    return 0.0


def marker_of(be: T, state_key: str) -> dict | None:
    """The marker a block entity carries, or None."""
    bid = str(be.get("id", T(8, "")).value)
    if bid in ("minecraft:sign", "minecraft:hanging_sign"):
        lines = sign_lines(be)
        if lines and lines[0].strip().startswith(PREFIX):
            mid = normalise_id(lines[0].strip()[len(PREFIX):])
            if not mid:
                return None
            note = " ".join(l.strip() for l in lines[1:] if l.strip())
            m = {"id": mid, "yaw": yaw_of(state_key), "authored_as": "sign"}
            if note:
                m["note"] = note
            return m
        return None
    if bid == "minecraft:structure_block":
        mode = str(be.get("mode", T(8, "")).value).upper()
        meta = str(be.get("metadata", T(8, "")).value)
        if mode == "DATA" and meta.strip():
            mid = normalise_id(meta.strip().lstrip(PREFIX))
            if mid:
                return {"id": mid, "yaw": 0.0, "authored_as": "structure_block"}
    return None


def is_waterlogged(state_key: str) -> bool:
    return "waterlogged=true" in state_key
