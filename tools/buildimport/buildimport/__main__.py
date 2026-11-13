"""Command line: python -m buildimport <command> ...

    scan   <world folder or region folder> <scan.npz>        read a world once (slow part, ~10 s per region file)
    survey <scan.npz> <output folder> [--districts FILE]      measurements, images, catalog (fast, re-runnable)

The world folder may be any Java world: a downloaded city, or a per-town workshop world edited in creative mode.
"""
from __future__ import annotations

import argparse
import os
import sys


def find_region_dir(path: str) -> str:
    for sub in ("dimensions/minecraft/overworld/region", "region", ""):
        p = os.path.join(path, sub)
        if os.path.isdir(p) and any(n.endswith(".mca") for n in os.listdir(p)):
            return p
    raise SystemExit(f"no overworld region files under {path}")


def main(argv=None):
    ap = argparse.ArgumentParser(prog="buildimport")
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("scan")
    s.add_argument("world")
    s.add_argument("out")
    v = sub.add_parser("survey")
    v.add_argument("scan")
    v.add_argument("outdir")
    v.add_argument("--districts", default=None)
    v.add_argument("--labels", default=None, help="also write the building map (labels.npz) the exporter reads")
    e = sub.add_parser("export", help="write a town's templates + manifest from its source of truth")
    e.add_argument("town", help="town config name in towns/ (e.g. d1_harbour) or a path to one")
    e.add_argument("--from", dest="prefer", choices=["workshop", "original"], default=None,
                   help="force a source (default: the workshop world if it exists, else the original map)")
    e.add_argument("--world", default=None, help="read this world folder instead of the configured one")
    e.add_argument("--new-version", action="store_true", help="after shipping: write the next version")
    e.add_argument("--preview", default=None, help="also draw a top-down zone picture (PNG) here")
    e.add_argument("--origin", default=None, help="x,y,z of the town in the workshop world (default: the manifest's)")
    e.add_argument("--out", default=None, help="write under this folder instead of the mod's data (for checks)")
    r = sub.add_parser("roundtrip", help="compare a pasted copy of a town with its templates and its source")
    r.add_argument("town")
    r.add_argument("pasted", help="world folder holding the pasted town (a workshop world or the game-test world)")
    r.add_argument("--origin", default=None, help="x,y,z of template origin in the pasted world (default: manifest)")
    r.add_argument("--source", default=None, help="also compare with this source world (default: the manifest's)")
    r.add_argument("--report", default=None, help="write the mismatch report (JSON) here")
    bl = sub.add_parser("building", help="export one catalog building as a standalone template + mini manifest")
    bl.add_argument("ids", nargs="+", type=int)
    bl.add_argument("--out", default=None, help="output folder (default: the mod's library resources)")
    a = ap.parse_args(argv)
    if a.cmd == "scan":
        from . import scan
        region = find_region_dir(a.world)
        print(f"scanning {region}")
        r = scan.scan(region)
        r.save(a.out)
        print(f"wrote {a.out}: {r.meta}")
    elif a.cmd == "survey":
        from . import survey
        survey.run(a.scan, a.outdir, a.districts, labels_path=a.labels)
    elif a.cmd == "export":
        from . import export
        export.run(a.town, a.prefer, a.world, a.new_version, preview_path=a.preview,
                   origin=[int(v) for v in a.origin.split(',')] if a.origin else None, out=a.out)
    elif a.cmd == "roundtrip":
        from . import roundtrip
        ok = roundtrip.run(a.town, a.pasted, a.origin, a.source, a.report)
        return 0 if ok else 1
    elif a.cmd == "building":
        from . import library
        library.run(a.ids, a.out)


if __name__ == "__main__":
    sys.exit(main())
