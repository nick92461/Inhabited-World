"""A small, read-only NBT (Named Binary Tag) parser.

Minecraft stores chunks, level.dat and most other save data as NBT: a tree of typed, big-endian values. This module
turns NBT bytes into plain Python values:

    TAG_Byte/Short/Int/Long  -> int
    TAG_Float/Double         -> float
    TAG_String               -> str
    TAG_List                 -> list
    TAG_Compound             -> dict
    TAG_Byte/Int/Long_Array  -> numpy array (int8 / >i4 / >i8), because chunk block data is stored in long arrays

It only reads. Nothing in this tool ever writes a save.
"""
from __future__ import annotations

import gzip
import struct
import zlib

import numpy as np

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE = 0, 1, 2, 3, 4, 5, 6
TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = 7, 8, 9, 10, 11, 12

_SCALARS = {
    TAG_BYTE: struct.Struct(">b"),
    TAG_SHORT: struct.Struct(">h"),
    TAG_INT: struct.Struct(">i"),
    TAG_LONG: struct.Struct(">q"),
    TAG_FLOAT: struct.Struct(">f"),
    TAG_DOUBLE: struct.Struct(">d"),
}
_USHORT = struct.Struct(">H")
_INT = _SCALARS[TAG_INT]
_ARRAYS = {TAG_BYTE_ARRAY: (np.int8, 1), TAG_INT_ARRAY: (np.dtype(">i4"), 4), TAG_LONG_ARRAY: (np.dtype(">i8"), 8)}


class NbtError(ValueError):
    pass


class _Reader:
    __slots__ = ("buf", "pos")

    def __init__(self, buf: bytes):
        self.buf = buf
        self.pos = 0

    def string(self) -> str:
        (n,) = _USHORT.unpack_from(self.buf, self.pos)
        start = self.pos + 2
        self.pos = start + n
        # Java "modified UTF-8"; plain UTF-8 decoding is right for every block and key name Minecraft uses.
        return self.buf[start:self.pos].decode("utf-8", errors="surrogateescape")

    def payload(self, tag: int):
        buf = self.buf
        s = _SCALARS.get(tag)
        if s is not None:
            (v,) = s.unpack_from(buf, self.pos)
            self.pos += s.size
            return v
        if tag == TAG_STRING:
            return self.string()
        if tag == TAG_COMPOUND:
            out = {}
            while True:
                t = buf[self.pos]
                self.pos += 1
                if t == TAG_END:
                    return out
                name = self.string()
                out[name] = self.payload(t)
        if tag == TAG_LIST:
            t = buf[self.pos]
            (n,) = _INT.unpack_from(buf, self.pos + 1)
            self.pos += 5
            if n <= 0:
                return []
            items = [self.payload(t) for _ in range(n)]
            if t == TAG_COMPOUND:
                # Since 1.21.5 a list may mix types; Minecraft stores such a list as compounds whose only key is ""
                # (e.g. a 26.x block palette [{"": "minecraft:dirt"}, {"Name": ..., "Properties": ...}]). Unwrap them.
                items = [i[""] if len(i) == 1 and "" in i else i for i in items]
            return items
        arr = _ARRAYS.get(tag)
        if arr is not None:
            dtype, size = arr
            (n,) = _INT.unpack_from(buf, self.pos)
            self.pos += 4
            a = np.frombuffer(buf, dtype=dtype, count=n, offset=self.pos)
            self.pos += n * size
            return a
        raise NbtError(f"unknown NBT tag {tag} at byte {self.pos}")


def parse(data: bytes) -> dict:
    """Parse uncompressed NBT bytes whose root is a (named) compound; returns the compound as a dict."""
    r = _Reader(data)
    tag = data[0]
    if tag != TAG_COMPOUND:
        raise NbtError(f"root tag is {tag}, expected a compound")
    r.pos = 1
    r.string()  # the root's name, normally empty
    return r.payload(TAG_COMPOUND)


def decompress(data: bytes) -> bytes:
    """Undo gzip or zlib if present (level.dat is gzip; region chunks say their own compression)."""
    if data[:2] == b"\x1f\x8b":
        return gzip.decompress(data)
    if data[:1] == b"\x78":
        return zlib.decompress(data)
    return data


def read_file(path) -> dict:
    with open(path, "rb") as f:
        return parse(decompress(f.read()))


# ---- writing, for unit tests only (builds small NBT samples; never used on a save) ----

def _w_string(s: str) -> bytes:
    b = s.encode("utf-8")
    return _USHORT.pack(len(b)) + b


def encode(value, tag: int | None = None) -> bytes:
    """Encode a payload (no name). Ints default to TAG_Int; pass (tag, value) tuples to pick a type."""
    if isinstance(value, tuple):
        tag, value = value
    if tag is None:
        tag = _infer(value)
    if tag in _SCALARS:
        return _SCALARS[tag].pack(value)
    if tag == TAG_STRING:
        return _w_string(value)
    if tag == TAG_COMPOUND:
        out = bytearray()
        for k, v in value.items():
            t = v[0] if isinstance(v, tuple) else _infer(v)
            out += bytes([t]) + _w_string(k) + encode(v)
        return bytes(out + b"\x00")
    if tag == TAG_LIST:
        if not value:
            return bytes([TAG_END]) + _INT.pack(0)
        t = value[0][0] if isinstance(value[0], tuple) else _infer(value[0])
        return bytes([t]) + _INT.pack(len(value)) + b"".join(encode(v) for v in value)
    if tag in _ARRAYS:
        dtype, _ = _ARRAYS[tag]
        a = np.asarray(value).astype(dtype)
        return _INT.pack(len(a)) + a.tobytes()
    raise NbtError(f"cannot encode tag {tag}")


def _infer(v) -> int:
    if isinstance(v, tuple):
        return v[0]
    if isinstance(v, bool) or isinstance(v, int):
        return TAG_INT
    if isinstance(v, float):
        return TAG_DOUBLE
    if isinstance(v, str):
        return TAG_STRING
    if isinstance(v, dict):
        return TAG_COMPOUND
    if isinstance(v, list):
        return TAG_LIST
    if isinstance(v, np.ndarray):
        return TAG_LONG_ARRAY
    raise NbtError(f"cannot infer an NBT tag for {type(v)}")


def encode_root(value: dict) -> bytes:
    return bytes([TAG_COMPOUND]) + _w_string("") + encode(value, TAG_COMPOUND)


# ---- typed (lossless) reading and writing, for the exporter ----
#
# The plain parser above turns NBT into Python values and loses tag types (byte vs int, float vs double, an empty
# list's element type). Block entities and entities must be copied into templates unchanged, so the exporter uses
# this typed form: every value is a T(tag, value), where
#     compound -> T(TAG_COMPOUND, {name: T})
#     list     -> T(TAG_LIST, (element tag, [T, ...]))
#     arrays   -> T(TAG_*_ARRAY, numpy array);  scalars and strings as themselves.
# Mixed lists (1.21.5+, stored as compounds whose only key is "") are kept as they are, so writing is exact.


class T:
    __slots__ = ("tag", "value")

    def __init__(self, tag: int, value):
        self.tag = tag
        self.value = value

    def __repr__(self):
        return f"T({self.tag}, {self.value!r})"

    def __eq__(self, other):
        if not isinstance(other, T) or other.tag != self.tag:
            return False
        if self.tag in _ARRAYS:
            return np.array_equal(self.value, other.value)
        return self.value == other.value

    def __getitem__(self, key):
        return self.value[key]

    def get(self, key, default=None):
        return self.value.get(key, default) if self.tag == TAG_COMPOUND else default

    def plain(self):
        """The plain-Python form of this value (as parse() returns it)."""
        if self.tag == TAG_COMPOUND:
            return {k: v.plain() for k, v in self.value.items()}
        if self.tag == TAG_LIST:
            items = [v.plain() for v in self.value[1]]
            if self.value[0] == TAG_COMPOUND:
                items = [i[""] if isinstance(i, dict) and len(i) == 1 and "" in i else i for i in items]
            return items
        return self.value


class _TypedReader(_Reader):
    def typed(self, tag: int) -> T:
        buf = self.buf
        if tag == TAG_COMPOUND:
            out = {}
            while True:
                t = buf[self.pos]
                self.pos += 1
                if t == TAG_END:
                    return T(TAG_COMPOUND, out)
                name = self.string()
                out[name] = self.typed(t)
        if tag == TAG_LIST:
            t = buf[self.pos]
            (n,) = _INT.unpack_from(buf, self.pos + 1)
            self.pos += 5
            return T(TAG_LIST, (t, [self.typed(t) for _ in range(max(n, 0))]))
        v = self.payload(tag)
        if tag in _ARRAYS:
            v = np.array(v)  # own copy, not a view of the chunk buffer
        return T(tag, v)


def parse_typed(data: bytes) -> T:
    """Parse uncompressed NBT bytes (root compound) losslessly into T values."""
    if data[0] != TAG_COMPOUND:
        raise NbtError(f"root tag is {data[0]}, expected a compound")
    r = _TypedReader(data)
    r.pos = 1
    r.string()
    return r.typed(TAG_COMPOUND)


def write_typed(t: T, out: bytearray) -> None:
    """Append the payload of a typed value (no tag byte, no name)."""
    tag = t.tag
    s = _SCALARS.get(tag)
    if s is not None:
        out += s.pack(t.value)
    elif tag == TAG_STRING:
        out += _w_string(t.value)
    elif tag == TAG_COMPOUND:
        for k, v in t.value.items():
            out.append(v.tag)
            out += _w_string(k)
            write_typed(v, out)
        out.append(TAG_END)
    elif tag == TAG_LIST:
        et, items = t.value
        out.append(et if items else (et or TAG_END))
        out += _INT.pack(len(items))
        for v in items:
            write_typed(v, out)
    elif tag in _ARRAYS:
        dtype, _ = _ARRAYS[tag]
        a = np.asarray(t.value).astype(dtype)
        out += _INT.pack(len(a)) + a.tobytes()
    else:
        raise NbtError(f"cannot write tag {tag}")


def canonical(t: T) -> T:
    """A copy with every compound's keys sorted (recursively). Minecraft does not care about key order, but it
    re-saves block entities in its own order; sorting makes exports byte-identical when nothing changed."""
    if t.tag == TAG_COMPOUND:
        return T(TAG_COMPOUND, {k: canonical(t.value[k]) for k in sorted(t.value)})
    if t.tag == TAG_LIST:
        et, items = t.value
        return T(TAG_LIST, (et, [canonical(v) for v in items]))
    return t


def root_bytes(t: T) -> bytes:
    """A complete NBT document: root compound with an empty name."""
    out = bytearray([TAG_COMPOUND])
    out += _w_string("")
    write_typed(t, out)
    return bytes(out)


# small constructors for building typed values by hand
def c(**kv) -> T:
    return T(TAG_COMPOUND, dict(kv))


def compound(d: dict) -> T:
    return T(TAG_COMPOUND, dict(d))


def s(v: str) -> T:
    return T(TAG_STRING, v)


def i(v: int) -> T:
    return T(TAG_INT, int(v))


def b(v: int) -> T:
    return T(TAG_BYTE, int(v))


def d(v: float) -> T:
    return T(TAG_DOUBLE, float(v))


def ints(*vs) -> T:
    return T(TAG_LIST, (TAG_INT, [T(TAG_INT, int(v)) for v in vs]))


def doubles(*vs) -> T:
    return T(TAG_LIST, (TAG_DOUBLE, [T(TAG_DOUBLE, float(v)) for v in vs]))


def lst(elem_tag: int, items) -> T:
    return T(TAG_LIST, (elem_tag, list(items)))


def int_array(a) -> T:
    return T(TAG_INT_ARRAY, np.asarray(a, dtype=">i4"))


def byte_array(a) -> T:
    return T(TAG_BYTE_ARRAY, np.asarray(a, dtype=np.int8))
