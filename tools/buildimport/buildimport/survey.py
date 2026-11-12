"""Survey a scanned world: measurements, preview images, districts and the building catalog.

    python -m buildimport survey <scan.npz> <output folder> [--districts districts.json]

Writes into the output folder:
    summary.json            every number the report quotes
    catalog.csv             one row per building or building group
    catalog/sheet_*.png     thumbnails, one contact sheet per district (IDs printed on each)
    overview.png            top-down map with districts outlined and labelled, waterfront marked
    grid.png                top-down map with a 64-block coordinate grid (for drawing district outlines)
    density.png height.png zones.png seams.png water.png buildings.png

districts.json (optional) lists districts as polygons in world block coordinates:
    {"districts": [{"id": "D1", "name": "Harbour", "polygon": [[x, z], ...]}, ...]}
"""
from __future__ import annotations

import csv
import json
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage as ndi

from . import analysis as A
from . import colors
from .scan import NONE, ScanResult

MARGIN = 48  # blocks of context around the authored area in images
MAJOR_STREET = 12  # open corridors at least this wide (blocks) are major streets or squares on the seams map


def save(im: Image.Image, path: str) -> None:
    """Save a map image as a 256-colour PNG (a third of the size, no visible loss for these maps)."""
    im.convert("RGB").quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).save(
        path, optimize=True)


def font(size: int):
    for name in ("arial.ttf", "DejaVuSans.ttf", "LiberationSans-Regular.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def bold(size: int):
    for name in ("arialbd.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return font(size)


# ---------------------------------------------------------------- districts

def load_districts(path, sv: A.Survey) -> list[dict]:
    if not path:
        return []
    with open(path, encoding="utf-8") as f:
        spec = json.load(f)
    h, w = sv.labels.shape
    out = []
    for d in spec["districts"]:
        img = Image.new("L", (w, h), 0)
        poly = [(x - sv.scan.x0, z - sv.scan.z0) for x, z in d["polygon"]]
        ImageDraw.Draw(img).polygon(poly, fill=1, outline=1)
        out.append({**d, "mask": np.array(img, bool)})
    return out


def district_of_buildings(sv: A.Survey, districts: list[dict]) -> None:
    for b in sv.buildings:
        m = sv.labels[b.z0 - sv.scan.z0:b.z1 - sv.scan.z0 + 1, b.x0 - sv.scan.x0:b.x1 - sv.scan.x0 + 1] == b.id
        zz, xx = np.nonzero(m)
        cz, cx = int(zz.mean()) + b.z0 - sv.scan.z0, int(xx.mean()) + b.x0 - sv.scan.x0
        b.district = ""
        for d in districts:
            if d["mask"][cz, cx]:
                b.district = d["id"]
                break
        if not b.district:
            b.district = "city" if sv.envelope[cz, cx] else "outskirts"


def district_stats(sv: A.Survey, districts: list[dict], co: dict) -> list[dict]:
    c = sv.scan.columns
    wf = co["waterfront_mask"]
    stats = []
    for d in districts:
        m = d["mask"] & sv.area
        zz, xx = np.nonzero(m)
        bs = [b for b in sv.buildings if b.district == d["id"]]
        real = [b for b in bs if b.type not in ("small structure",)]
        built = m & sv.built
        tops = c["built_top"][built & (c["built_top"] != NONE)]
        grounds = sv.ground[m]
        interior = sum(b.interior_area for b in bs)
        upper = sum(b.upper_area for b in bs)
        empty = sum(b.upper_empty_area for b in bs)
        fitout = sum(b.fitout_area for b in bs)
        homes = sum(1 for b in bs if b.type in HOME_TYPES)
        wf_len = int((wf & ndi.binary_dilation(m, iterations=2)).sum())
        sea_near = int((sv.sea & ndi.binary_dilation(m, iterations=A.WATERFRONT_REACH)).sum())
        stats.append({
            "id": d["id"], "name": d["name"], "note": d.get("note", ""),
            "bounds": [int(xx.min() + sv.scan.x0), int(zz.min() + sv.scan.z0),
                       int(xx.max() + sv.scan.x0), int(zz.max() + sv.scan.z0)],
            "size": [int(xx.max() - xx.min() + 1), int(zz.max() - zz.min() + 1)],
            "footprint_m2": int(m.sum()),
            "built_m2": int(built.sum()),
            "buildings": len(real), "small_structures": len(bs) - len(real),
            "types": _count([b.type for b in bs]),
            "ground_y": [int(np.percentile(grounds, 5)), int(np.percentile(grounds, 95))],
            "roof_y": [int(np.percentile(tops, 5)), int(tops.max())] if len(tops) else None,
            "vertical_range": [int(min(b.bottom for b in bs)), int(max(b.top for b in bs))] if bs else None,
            "floor_m2": int(interior), "upper_floor_m2": int(upper), "empty_upper_m2": int(empty),
            "fitout_m2": int(fitout),
            "bed_blocks": int(sum(b.beds for b in bs)), "workstations": int(sum(b.work for b in bs)),
            "home_or_workplace_buildings": homes,
            "significant_npcs": homes // 2,
            "residents_now": int(round(interior / A.M2_PER_RESIDENT)),
            "residents_with_fitout": int(round((interior + fitout) / A.M2_PER_RESIDENT)),
            "waterfront_m": wf_len, "touches_waterfront": wf_len >= 16 or sea_near > 200,
        })
    return stats


# Building types that can be someone's home or workplace (a significant NPC needs one of each).
HOME_TYPES = ("house", "large building", "hall", "tower", "building block")


def _count(items):
    out = {}
    for i in items:
        out[i] = out.get(i, 0) + 1
    return dict(sorted(out.items(), key=lambda kv: -kv[1]))


# ---------------------------------------------------------------- images

class Canvas:
    """Image helpers for a crop of the scanned rectangle (world coordinates in, pixels out)."""

    def __init__(self, sv: A.Survey, scale: int = 1):
        zz, xx = np.nonzero(sv.area)
        self.r0 = max(int(zz.min()) - MARGIN, 0)
        self.c0 = max(int(xx.min()) - MARGIN, 0)
        self.r1 = min(int(zz.max()) + MARGIN + 1, sv.area.shape[0])
        self.c1 = min(int(xx.max()) + MARGIN + 1, sv.area.shape[1])
        self.sv = sv
        self.scale = scale

    def crop(self, a):
        return a[self.r0:self.r1, self.c0:self.c1]

    def px(self, x, z):
        return ((x - self.sv.scan.x0 - self.c0) * self.scale, (z - self.sv.scan.z0 - self.r0) * self.scale)

    def image(self, rgb):
        im = Image.fromarray(np.ascontiguousarray(rgb.astype(np.uint8)))
        if self.scale != 1:
            im = im.resize((im.width * self.scale, im.height * self.scale), Image.NEAREST)
        return im


def topdown(sv: A.Survey) -> np.ndarray:
    c = sv.scan.columns
    pal = colors.palette(sv.scan.keys)
    img = pal[c["surface_id"]].astype(np.float32)
    sy = c["surface_y"].astype(np.float32)
    sy[sy < -1000] = 0
    gz, gx = np.gradient(sy)
    shade = np.clip(1 + (-gx - gz) * 0.06, 0.65, 1.3)
    img = np.clip(img * shade[..., None], 0, 255)
    img[~sv.area] = img[~sv.area] * 0.25 + 20
    return img


def ramp(v: np.ndarray, stops) -> np.ndarray:
    """Map values in [0, 1] through colour stops [(pos, (r, g, b)), ...]."""
    v = np.clip(v, 0, 1)
    out = np.zeros(v.shape + (3,), np.float32)
    pos = np.array([s[0] for s in stops])
    cols = np.array([s[1] for s in stops], np.float32)
    for ch in range(3):
        out[..., ch] = np.interp(v, pos, cols[:, ch])
    return out


VIRIDIS = [(0, (68, 1, 84)), (0.25, (59, 82, 139)), (0.5, (33, 145, 140)), (0.75, (94, 201, 98)),
           (1, (253, 231, 37))]
HEAT = [(0, (20, 20, 30)), (0.15, (60, 30, 90)), (0.4, (180, 40, 60)), (0.7, (240, 140, 30)), (1, (255, 240, 160))]
TERRAIN = [(0, (40, 70, 140)), (0.3, (60, 140, 70)), (0.55, (200, 190, 110)), (0.8, (140, 100, 70)),
           (1, (250, 250, 250))]


def legend(im: Image.Image, items, title=None, pos=(10, 10), k: int = 1):
    d = ImageDraw.Draw(im)
    f = font(14 * k)
    x, y = pos
    rows = len(items) + (1 if title else 0)
    wbox = 12 * k + max(d.textlength(t, font=f) for t, _ in items) + 30 * k
    if title:
        wbox = max(wbox, d.textlength(title, font=bold(14 * k)) + 20 * k)
    d.rectangle([x, y, x + wbox, y + rows * 20 * k + 10 * k], fill=(255, 255, 255), outline=(0, 0, 0))
    yy = y + 6 * k
    if title:
        d.text((x + 8 * k, yy), title, fill=(0, 0, 0), font=bold(14 * k))
        yy += 20 * k
    for text, col in items:
        d.rectangle([x + 8 * k, yy + 2 * k, x + 22 * k, yy + 14 * k], fill=col, outline=(0, 0, 0))
        d.text((x + 28 * k, yy), text, fill=(0, 0, 0), font=f)
        yy += 20 * k


def scale_bar(im: Image.Image, scale: int, pos=None):
    d = ImageDraw.Draw(im)
    k = scale
    x, y = pos or (10 * k, im.height - 30 * k)
    n = 128 * scale
    d.rectangle([x - 4 * k, y - 20 * k, x + n + 56 * k, y + 10 * k], fill=(255, 255, 255))
    d.line([x, y, x + n, y], fill=(0, 0, 0), width=3 * k)
    for t in (0, n // 2, n):
        d.line([x + t, y - 5 * k, x + t, y + 5 * k], fill=(0, 0, 0), width=2 * k)
    d.text((x + n + 6 * k, y - 9 * k), "128 m", fill=(0, 0, 0), font=font(13 * k))
    d.text((x, y - 20 * k), "north is up", fill=(0, 0, 0), font=font(11 * k))


def grid_lines(im: Image.Image, cv: Canvas, step=64, labels=True, color=(255, 255, 255)):
    d = ImageDraw.Draw(im)
    f = font(11)
    sx0, sz0 = cv.sv.scan.x0 + cv.c0, cv.sv.scan.z0 + cv.r0
    sx1, sz1 = cv.sv.scan.x0 + cv.c1, cv.sv.scan.z0 + cv.r1
    for x in range((sx0 // step + 1) * step, sx1, step):
        px, _ = cv.px(x, sz0)
        d.line([px, 0, px, im.height], fill=color, width=1)
        if labels:
            d.text((px + 2, 2), str(x), fill=color, font=f)
    for z in range((sz0 // step + 1) * step, sz1, step):
        _, pz = cv.px(sx0, z)
        d.line([0, pz, im.width, pz], fill=color, width=1)
        if labels:
            d.text((2, pz + 2), str(z), fill=color, font=f)


def write_images(sv: A.Survey, co: dict, districts: list[dict], dstats: list[dict], out: str, zones: np.ndarray):
    c = sv.scan.columns
    cv = Canvas(sv)
    top = topdown(sv)

    # grid (for drawing districts)
    im = cv.image(cv.crop(top))
    grid_lines(im, cv)
    save(im, os.path.join(out, "grid.png"))

    # density heatmap: built blocks per column, averaged over 9 x 9
    dens = ndi.uniform_filter(np.where(sv.area, c["built_n"], 0).astype(np.float32), size=9)
    v = np.sqrt(dens / max(np.percentile(dens[sv.area], 99.5), 1))
    rgb = ramp(v, HEAT)
    rgb[sv.sea] = (25, 45, 90)
    im = cv.image(cv.crop(rgb))
    legend(im, [("none", HEAT[0][1]), ("low", HEAT[1][1]), ("medium", HEAT[2][1]), ("high", HEAT[3][1]),
                ("very high", HEAT[4][1]), ("open water", (25, 45, 90))], "Built blocks per column")
    scale_bar(im, 1)
    save(im, os.path.join(out, "density.png"))

    # height map
    sy = c["surface_y"].astype(np.float32)
    lo, hi = sv.sea_level - 20, float(np.percentile(sy[sv.area], 99.8))
    v = (sy - lo) / (hi - lo)
    rgb = ramp(v, TERRAIN)
    gz, gx = np.gradient(np.where(sy < -1000, 0, sy))
    rgb *= np.clip(1 + (-gx - gz) * 0.05, 0.6, 1.3)[..., None]
    rgb[~sv.area] *= 0.3
    im = cv.image(cv.crop(np.clip(rgb, 0, 255)))
    steps = np.linspace(0, 1, 5)
    legend(im, [(f"y {int(lo + s * (hi - lo))}", tuple(int(t) for t in ramp(np.array([s]), TERRAIN)[0]))
                for s in steps], "Top surface height")
    scale_bar(im, 1)
    save(im, os.path.join(out, "height.png"))

    # zones
    zc = np.array([(40, 40, 40), (220, 70, 60), (235, 200, 90), (120, 170, 110), (60, 60, 60), (40, 80, 160)],
                  np.float32)
    rgb = zc[zones] * 0.8 + top * 0.2
    im = cv.image(cv.crop(rgb))
    legend(im, [("keep: built (exact mold)", tuple(zc[1].astype(int))),
                ("ground: supporting terrain", tuple(zc[2].astype(int))),
                ("blend margin (32 blocks)", tuple(zc[3].astype(int))),
                ("drop", tuple(zc[4].astype(int))), ("open water", tuple(zc[5].astype(int)))],
           "Extraction zones (whole city)")
    scale_bar(im, 1)
    save(im, os.path.join(out, "zones.png"))

    # seams
    seams = sv.extra["seams"]
    rgb = top * 0.35 + 150 * 0.65
    rgb[sv.tall] = (190, 190, 190)
    rgb[seams["lane"]] = (250, 215, 120)
    rgb[seams["street"]] = (245, 150, 40)
    rgb[seams["cliff"]] = (140, 80, 40)
    rgb[seams["river"]] = (60, 140, 230)
    rgb[seams["wall"]] = (200, 30, 30)
    rgb[sv.sea] = (35, 70, 150)
    edge = sv.envelope & ~ndi.binary_erosion(sv.envelope, iterations=2)
    rgb[edge] = (0, 0, 0)
    im = cv.image(cv.crop(rgb))
    legend(im, [("building", (190, 190, 190)), ("major street / square (12+ wide)", (245, 150, 40)),
                ("lane (5-11 wide)", (250, 215, 120)), ("wall", (200, 30, 30)), ("river, canal, pond", (60, 140, 230)),
                ("cliff / steep slope", (140, 80, 40)), ("open sea", (35, 70, 150)), ("city outline", (0, 0, 0))],
           "Seams")
    scale_bar(im, 1)
    save(im, os.path.join(out, "seams.png"))

    # water
    depth = co["depth_map"]
    rgb = top * 0.5 + 60
    wd = np.clip(depth / 30.0, 0, 1)
    wrgb = ramp(wd, [(0, (150, 210, 250)), (0.5, (50, 110, 200)), (1, (10, 30, 90))])
    rgb[sv.sea] = wrgb[sv.sea]
    other = sv.water_surface & ~sv.sea
    rgb[other] = (90, 200, 220)
    rgb[ndi.binary_dilation(co["waterfront_mask"], iterations=2)] = (255, 230, 0)
    edge = sv.envelope & ~ndi.binary_erosion(sv.envelope, iterations=2)
    rgb[edge] = (0, 0, 0)
    im = cv.image(cv.crop(rgb))
    legend(im, [(f"sea, depth 0-10 below y {sv.sea_level}", (150, 210, 250)), ("depth ~15", (50, 110, 200)),
                ("depth 30+", (10, 30, 90)), ("inland water (not sea)", (90, 200, 220)),
                ("city waterfront", (255, 230, 0)), ("city outline", (0, 0, 0))], "Open water and sea level")
    scale_bar(im, 1)
    save(im, os.path.join(out, "water.png"))

    # buildings by type
    types = sorted({b.type for b in sv.buildings})
    tcol = {"house": (230, 120, 90), "large building": (200, 60, 60), "hall": (150, 40, 150),
            "tower": (40, 90, 200), "wall segment": (90, 90, 90), "small structure": (200, 200, 120),
            "on water": (60, 180, 200), "building block": (160, 80, 40), "unknown": (255, 255, 255)}
    lut = np.zeros((sv.labels.max() + 1, 3), np.float32)
    for b in sv.buildings:
        lut[b.id] = tcol.get(b.type, (255, 255, 255))
        lut[b.id] *= 0.8 + 0.2 * ((b.id * 7919) % 5) / 4  # separate neighbours a little
    rgb = top * 0.3 + 120
    m = sv.labels > 0
    rgb[m] = lut[sv.labels[m]]
    rgb[sv.sea] = (35, 70, 150)
    im = cv.image(cv.crop(rgb))
    legend(im, [(t, tcol.get(t, (255, 255, 255))) for t in types], f"{len(sv.buildings)} buildings by type")
    scale_bar(im, 1)
    save(im, os.path.join(out, "buildings.png"))

    # overview with districts
    if districts:
        overview(sv, co, districts, dstats, top, os.path.join(out, "overview.png"))


DCOLORS = [(255, 80, 80), (80, 200, 255), (255, 210, 60), (120, 230, 120), (230, 120, 255), (255, 150, 60),
           (60, 230, 200), (255, 120, 180), (180, 180, 255), (200, 255, 120)]


def overview(sv, co, districts, dstats, top, path):
    cv = Canvas(sv, scale=1)
    # crop tighter: city outline plus 96 blocks
    zz, xx = np.nonzero(sv.envelope | np.any([d["mask"] for d in districts], axis=0))
    cv.r0, cv.r1 = max(zz.min() - 96, 0), min(zz.max() + 97, sv.area.shape[0])
    cv.c0, cv.c1 = max(xx.min() - 96, 0), min(xx.max() + 97, sv.area.shape[1])
    cv.scale = 2 if (cv.c1 - cv.c0) < 1100 else 1
    rgb = top.copy()
    wf = ndi.binary_dilation(co["waterfront_mask"], iterations=2)
    im = cv.image(cv.crop(rgb)).convert("RGBA")
    ov = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    s = cv.scale
    for i, dist in enumerate(districts):
        col = DCOLORS[i % len(DCOLORS)]
        pts = [cv.px(x, z) for x, z in dist["polygon"]]
        d.polygon(pts, fill=col + (40,))
        d.line(pts + [pts[0]], fill=col + (255,), width=3 * s)
    im = Image.alpha_composite(im, ov)
    # waterfront on top: a bright band along the city edge where it meets open sea
    band = cv.crop(ndi.binary_dilation(wf, iterations=2))
    wim = Image.fromarray((band * 255).astype(np.uint8)).resize(im.size, Image.NEAREST)
    im.paste(Image.new("RGBA", im.size, (0, 235, 255, 255)), (0, 0), wim)
    d = ImageDraw.Draw(im)
    fb = bold(15 * s)
    fs = font(12 * s)
    for i, (dist, st) in enumerate(zip(districts, dstats)):
        col = DCOLORS[i % len(DCOLORS)]
        lx, lz = dist.get("label_at", np.mean(dist["polygon"], axis=0))
        px, pz = cv.px(lx, lz)
        t1 = f"{dist['id']} {dist['name']}"
        t2 = f"{st['size'][0]}x{st['size'][1]} m, {st['buildings']} bldgs"
        w = max(d.textlength(t1, font=fb), d.textlength(t2, font=fs)) + 12 * s
        d.rectangle([px - w / 2, pz - 20 * s, px + w / 2, pz + 18 * s], fill=(255, 255, 255, 230),
                    outline=col, width=2 * s)
        d.text((px - w / 2 + 6 * s, pz - 18 * s), t1, fill=(0, 0, 0), font=fb)
        d.text((px - w / 2 + 6 * s, pz + 1 * s), t2, fill=(40, 40, 40), font=fs)
    im = im.convert("RGB")
    legend(im, [("waterfront (city edge on open sea)", (0, 230, 255))] +
           [(f"{dd['id']} {dd['name']}", DCOLORS[i % len(DCOLORS)]) for i, dd in enumerate(districts)],
           "Drachenfels districts", pos=(10, im.height - (len(districts) + 2) * 20 * s - 60 * s), k=s)
    scale_bar(im, s)
    save(im, path)


def contact_sheets(sv: A.Survey, top: np.ndarray, outdir: str) -> dict:
    """Thumbnails per district: each building's crop of the top-down map, outline in yellow, ID printed."""
    os.makedirs(outdir, exist_ok=True)
    groups: dict[str, list] = {}
    for b in sv.buildings:
        groups.setdefault(b.district or "none", []).append(b)
    where = {}
    cell, cols = 112, 12
    f = font(11)
    for dist, bs in sorted(groups.items()):
        bs = sorted(bs, key=lambda b: b.id)
        rows = (len(bs) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * cell, rows * cell), (255, 255, 255))
        dr = ImageDraw.Draw(sheet)
        name = f"sheet_{dist}.png"
        for k, b in enumerate(bs):
            pad = 4
            r0 = b.z0 - sv.scan.z0 - pad
            c0 = b.x0 - sv.scan.x0 - pad
            r1 = b.z1 - sv.scan.z0 + pad + 1
            c1 = b.x1 - sv.scan.x0 + pad + 1
            r0, c0 = max(r0, 0), max(c0, 0)
            crop = top[r0:r1, c0:c1].copy()
            m = sv.labels[r0:r1, c0:c1] == b.id
            edge = m & ~ndi.binary_erosion(m)
            crop[edge] = (255, 230, 0)
            im = Image.fromarray(crop.astype(np.uint8))
            sc = (cell - 16) / max(im.width, im.height)
            im = im.resize((max(1, int(im.width * sc)), max(1, int(im.height * sc))),
                           Image.NEAREST if sc >= 1 else Image.LANCZOS)
            x, y = (k % cols) * cell, (k // cols) * cell
            sheet.paste(im, (x + (cell - im.width) // 2, y + 14 + (cell - 16 - im.height) // 2))
            dr.text((x + 3, y + 1), f"#{b.id} {b.type[:12]}", fill=(0, 0, 0), font=f)
            where[b.id] = (f"catalog/{name}", k // cols + 1, k % cols + 1)
        save(sheet, os.path.join(outdir, name))
    return where


# ---------------------------------------------------------------- zones and seams

def zones_map(sv: A.Survey) -> np.ndarray:
    """0 outside, 1 keep (built inside the city outline), 2 supporting ground, 3 blend margin, 4 drop, 5 open water."""
    z = np.zeros(sv.area.shape, np.uint8)
    z[sv.area] = 4
    ground = (sv.envelope | ndi.binary_dilation(sv.envelope, iterations=8)) & sv.area
    blend = ndi.binary_dilation(ground, iterations=32) & sv.area & ~ground
    z[blend] = 3
    z[ground] = 2
    z[sv.envelope & sv.built] = 1
    z[sv.sea & sv.area & ~(sv.envelope & sv.built)] = 5
    return z


def seams_map(sv: A.Survey) -> dict:
    c = sv.scan.columns
    inside = sv.envelope
    open_ = inside & ~sv.tall & ~sv.sea
    width = 2 * ndi.distance_transform_edt(open_)
    # spread the corridor width from the centre line across the corridor
    wmax = ndi.maximum_filter(width, size=9) * open_
    street = open_ & (wmax >= MAJOR_STREET)
    lane = open_ & (wmax >= 5) & ~street
    water_in = sv.water_surface & ~sv.sea
    lab, n = ndi.label(water_in)
    sizes = np.bincount(lab.ravel(), minlength=n + 1)
    river = water_in & (sizes[lab] >= 30)
    g = ndi.median_filter(sv.ground.astype(np.float32), size=5)
    gz, gx = np.gradient(g)
    cliff = sv.area & ~sv.tall & ~sv.sea & (np.hypot(gx, gz) >= 1.5)
    wall = np.isin(sv.labels, [b.id for b in sv.buildings if b.type == "wall segment"])
    return {"street": street, "lane": lane, "river": river, "cliff": cliff, "wall": wall}


# ---------------------------------------------------------------- catalog

CATALOG_FIELDS = [
    "id", "district", "type", "x0", "z0", "x1", "z1", "width_x", "depth_z", "footprint_m2", "ground_y", "top_y",
    "bottom_y", "height", "storeys", "ground_floor_m2", "upper_m2", "empty_upper_m2", "fitout_m2", "cellar_m2",
    "furnish_per_100m2", "entrance",
    "entrance_basis", "doors", "workstations", "bed_blocks", "furnishings", "group", "waterfront", "sheet",
    "sheet_row", "sheet_col",
]


def write_catalog(sv: A.Survey, path: str, where: dict, wf_mask: np.ndarray) -> None:
    near_wf = ndi.binary_dilation(wf_mask, iterations=12)
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(CATALOG_FIELDS)
        for b in sorted(sv.buildings, key=lambda b: b.id):
            m = sv.labels[b.z0 - sv.scan.z0:b.z1 - sv.scan.z0 + 1, b.x0 - sv.scan.x0:b.x1 - sv.scan.x0 + 1] == b.id
            wfb = bool((near_wf[b.z0 - sv.scan.z0:b.z1 - sv.scan.z0 + 1,
                                b.x0 - sv.scan.x0:b.x1 - sv.scan.x0 + 1] & m).any())
            b.waterfront = wfb
            sheet = where.get(b.id, ("", "", ""))
            w.writerow([b.id, b.district, b.type, b.x0, b.z0, b.x1, b.z1, b.x1 - b.x0 + 1, b.z1 - b.z0 + 1, b.area,
                        b.ground, b.top, b.bottom, b.height, b.storeys, b.ground_floor_area, b.upper_area,
                        b.upper_empty_area, b.fitout_area, b.cellar_area, b.furnish_density, b.entrance,
                        b.entrance_basis, b.doors,
                        b.work, b.beds, b.furnish, b.group, int(wfb), *sheet])


# ---------------------------------------------------------------- run

def run(scan_path: str, outdir: str, districts_path: str | None = None, area_mode: str = "auto") -> dict:
    os.makedirs(outdir, exist_ok=True)
    scan = ScanResult.load(scan_path)
    sv = A.base_survey(scan, area_mode)
    A.find_buildings(sv)
    A.assign_floors(sv)
    A.classify_buildings(sv)
    A.entrances(sv)
    co = A.coast(sv)
    sv.extra["seams"] = seams_map(sv)
    zones = zones_map(sv)
    districts = load_districts(districts_path, sv)
    district_of_buildings(sv, districts)
    dstats = district_stats(sv, districts, co)
    write_images(sv, co, districts, dstats, outdir, zones)
    top = topdown(sv)
    where = contact_sheets(sv, top, os.path.join(outdir, "catalog"))
    write_catalog(sv, os.path.join(outdir, "catalog.csv"), where, co["waterfront_mask"])
    summary = summarise(sv, co, zones, dstats)
    with open(os.path.join(outdir, "summary.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2)
    print(json.dumps({k: v for k, v in summary.items() if k != "districts"}, indent=2))
    for d in dstats:
        print(json.dumps(d))
    return summary


def _bbox(mask, sv):
    zz, xx = np.nonzero(mask)
    return [int(xx.min() + sv.scan.x0), int(zz.min() + sv.scan.z0), int(xx.max() + sv.scan.x0),
            int(zz.max() + sv.scan.z0)]


def summarise(sv: A.Survey, co: dict, zones: np.ndarray, dstats: list) -> dict:
    c = sv.scan.columns
    city_built = sv.envelope & sv.built
    tops = c["built_top"][city_built]
    bottoms = c["built_bottom"][city_built]
    cut = (sv.ground - c["built_bottom"].astype(np.int32))[city_built]
    types = _count([b.type for b in sv.buildings])
    in_city = [b for b in sv.buildings if sv.envelope[(b.z0 + b.z1) // 2 - sv.scan.z0,
                                                       (b.x0 + b.x1) // 2 - sv.scan.x0]]
    return {
        "scan": sv.scan.meta,
        "authored_area_bbox": _bbox(sv.area, sv),
        "authored_area_m2": int(sv.area.sum()),
        "sea_level": sv.sea_level,
        "all_built_bbox": _bbox(sv.built, sv),
        "all_built_columns": int(sv.built.sum()),
        "building_columns": int(sv.tall.sum()),
        "city_outline_bbox": _bbox(sv.envelope, sv),
        "city_outline_m2": int(sv.envelope.sum()),
        "city_built_m2": int(city_built.sum()),
        "city_roof_y": [int(np.percentile(tops, 1)), int(np.percentile(tops, 50)), int(tops.max())],
        "city_lowest_built_y": [int(bottoms.min()), int(np.percentile(bottoms, 50))],
        "floor_cutoff_depth_below_ground": [int(np.percentile(cut, 50)), int(np.percentile(cut, 95)),
                                            int(cut.max())],
        "zones_m2": {name: int((zones == i).sum()) for i, name in
                     enumerate(["outside", "keep", "ground", "blend", "drop", "water"])},
        "buildings_total": len(sv.buildings),
        "buildings_in_city": len(in_city),
        "building_types": types,
        "floor_m2": int(sum(b.interior_area for b in in_city)),
        "upper_floor_m2": int(sum(b.upper_area for b in in_city)),
        "empty_upper_m2": int(sum(b.upper_empty_area for b in in_city)),
        "fitout_m2": int(sum(b.fitout_area for b in in_city)),
        "bed_blocks": int(c["bed_blocks"][sv.area].sum()),
        "door_blocks": int(c["doors"][sv.area].sum()),
        "workstations": int(c["work"][sv.area].sum()),
        "coast": {k: v for k, v in co.items() if not isinstance(v, np.ndarray)},
        "districts": dstats,
    }
