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
    a = ap.parse_args(argv)
    if a.cmd == "scan":
        from . import scan
        region = find_region_dir(a.world)
        print(f"scanning {region}")
        r = scan.scan(region)
        r.save(a.out)
        print(f"wrote {a.out}: {r.meta}")
    else:
        from . import survey
        survey.run(a.scan, a.outdir, a.districts)


if __name__ == "__main__":
    sys.exit(main())
