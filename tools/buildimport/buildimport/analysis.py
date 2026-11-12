"""2D analysis of a scan: authored area, ground, building components, floors, coast. Pure numpy/scipy, no images.

All maps are (rows = z, cols = x) arrays over the scanned rectangle; world x = col + x0, world z = row + z0.
Tunable thresholds are module constants, named and explained where they are used.
"""
from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
from scipy import ndimage as ndi

from .scan import CHUNK_NEW, CHUNK_UPGRADED, NONE, ScanResult

# A column is part of a BUILDING (not a street or plaza) when built blocks reach this far above the ground around.
TALL_ABOVE_GROUND = 4
# Building parts smaller than this (m², i.e. columns) are lamp posts, fences or statues and are not buildings.
MIN_BUILDING_AREA = 12
# Inside one connected building block, a roof step at least this tall separates two buildings.
ROOF_STEP = 3
# Along a terrace, a change of this much in the ridge height marks the next house.
RIDGE_STEP = 2
# Building blocks thinner than this (blocks) are treated as wings of row houses and split into houses.
WING_MAX_WIDTH = 26
# Fragments of a split block smaller than this join the neighbour they share most border with.
MIN_PART_AREA = 40
# One storey: 3 blocks of headroom plus the floor block above.
STOREY_HEIGHT = 4
# A slender part counts as a tower when it rises this far above everything within 4 blocks around it.
TOWER_ABOVE_NEIGHBOURS = 6
# Existing upper floors with fewer furnishing blocks than this per 100 m² count as EMPTY (unfurnished).
EMPTY_FURNISH_PER_100M2 = 1.0
# Floor area per resident, for the rough housing estimate (m² per person; generous medieval homes and workplaces).
M2_PER_RESIDENT = 40
# City envelope: built land closed over gaps up to this radius (blocks), so streets and courtyards are inside.
ENVELOPE_CLOSE = 16
# City outline density: share of ground covered by buildings in a window of this size (blocks).
ENVELOPE_DENSITY = 0.15
ENVELOPE_WINDOW = 41
# Pieces of outline smaller than this share of the biggest piece are outskirts, not city.
ENVELOPE_MIN_SHARE = 0.05
# Waterfront: envelope edge within this many blocks of open water.
WATERFRONT_REACH = 24
# Piers, jetties and moored ships are thinner than this; they are left out when measuring the waterfront.
PIER_WIDTH = 12
# Piers are looked for this far out from the city outline.
PIER_SEARCH = 160
# Open water: a water body at sea level with at least this many columns (smaller ones are ponds and fountains).
MIN_SEA_AREA = 20000


@dataclass
class Survey:
    scan: ScanResult
    area: np.ndarray                 # authored area mask
    sea_level: int
    ground: np.ndarray               # ground height everywhere (filled under buildings)
    built: np.ndarray                # columns with any built block
    tall: np.ndarray                 # columns that are part of a building
    water_surface: np.ndarray        # top block is water
    sea: np.ndarray                  # open water connected bodies at sea level
    envelope: np.ndarray             # the city's outline (built land with streets and courtyards closed in)
    labels: np.ndarray = None        # building id per column (0 = none)
    buildings: list = field(default_factory=list)
    extra: dict = field(default_factory=dict)


def authored_area(scan: ScanResult, mode: str = "auto") -> np.ndarray:
    """Columns that belong to the authored map.

    A downloaded map opened in a newer Minecraft is surrounded by chunks the new version generated when the player
    walked near the edge (here: a superflat ring with vanilla villages). Those chunks carry no blending data, while
    the map's own upgraded chunks do. mode 'auto' keeps only upgraded chunks when there are many; 'all' keeps all
    full chunks (a workshop world made in the current version)."""
    k = scan.chunk_kind
    up = k == CHUNK_UPGRADED
    if mode == "auto":
        mode = "upgraded" if up.sum() >= 64 else "all"
    keep = up if mode == "upgraded" else (k == CHUNK_UPGRADED) | (k == CHUNK_NEW)
    # the map itself is the biggest connected block of such chunks (stray chunks elsewhere are left out)
    lab, n = ndi.label(ndi.binary_closing(keep, iterations=2) & keep)
    if n > 1:
        sizes = np.bincount(lab.ravel())
        sizes[0] = 0
        keep = lab == sizes.argmax()
    keep = ndi.binary_fill_holes(keep)
    return np.kron(keep, np.ones((16, 16), bool))


def fill_nearest(values: np.ndarray, known: np.ndarray) -> np.ndarray:
    """Copy each unknown cell's value from its nearest known cell."""
    if known.all() or not known.any():
        return values.copy()
    _, (iz, ix) = ndi.distance_transform_edt(~known, return_indices=True)
    return values[iz, ix]


def sea_level_of(scan: ScanResult, area: np.ndarray) -> int:
    c = scan.columns
    wt = c["water_top"]
    surf = area & (wt != NONE) & (wt == c["surface_y"])
    vals, counts = np.unique(wt[surf], return_counts=True)
    return int(vals[np.argmax(counts)]) if len(vals) else 63


def base_survey(scan: ScanResult, area_mode: str = "auto") -> Survey:
    c = scan.columns
    area = authored_area(scan, area_mode)
    sea_level = sea_level_of(scan, area)
    built = area & (c["built_n"] > 0)
    water_surface = area & (c["water_top"] != NONE) & (c["water_top"] == c["surface_y"])

    # Ground: where nothing (or only paving, <= 2 built blocks) stands, the natural terrain top; under water the
    # seabed; elsewhere copied from the nearest such column.
    g = c["ground_y"].astype(np.int32)
    known = area & (g != NONE) & (c["built_n"] <= 2)
    ground = fill_nearest(g, known)

    top = c["built_top"].astype(np.int32)
    tall = built & (top != NONE) & (top - ground >= TALL_ABOVE_GROUND)
    tall = ndi.binary_opening(tall, structure=np.ones((2, 2), bool))  # drop 1-wide posts and fences

    lab, n = ndi.label(water_surface & (np.abs(c["water_top"].astype(np.int32) - sea_level) <= 1))
    sizes = ndi.sum(np.ones_like(lab), lab, index=np.arange(1, n + 1))
    big = np.zeros(n + 1, bool)
    big[1:] = sizes >= MIN_SEA_AREA
    sea = big[lab]

    # The city outline: where buildings cover at least ENVELOPE_DENSITY of the ground within ENVELOPE_WINDOW
    # blocks, closed over streets and courtyards and with holes filled. Scattered farm huts stay outside
    # ("outskirts").
    dens = ndi.uniform_filter(tall.astype(np.float32), size=ENVELOPE_WINDOW, mode="constant")
    env = ndi.binary_closing(np.pad(dens >= ENVELOPE_DENSITY, ENVELOPE_CLOSE), structure=_disk(ENVELOPE_CLOSE))
    env = env[ENVELOPE_CLOSE:-ENVELOPE_CLOSE, ENVELOPE_CLOSE:-ENVELOPE_CLOSE]
    env = ndi.binary_fill_holes(env | (tall & ndi.binary_dilation(env, iterations=4))) & area & ~sea
    elab, en = ndi.label(env)
    if en:
        sizes = np.bincount(elab.ravel(), minlength=en + 1)
        sizes[0] = 0
        keep = sizes >= ENVELOPE_MIN_SHARE * sizes.max()
        keep[0] = False
        env = keep[elab]
    return Survey(scan, area, sea_level, ground, built, tall, water_surface, sea, env)


def _disk(r: int) -> np.ndarray:
    y, x = np.ogrid[-r:r + 1, -r:r + 1]
    return x * x + y * y <= r * r


# ---------------------------------------------------------------- buildings

def run_ridge(mask: np.ndarray, top: np.ndarray) -> np.ndarray:
    """For each masked column, the ridge height across its wing.

    A terrace or perimeter block is a chain of houses whose roofs slope into each other, so neighbouring columns
    differ by one block almost everywhere. Across the wing (the shorter way through the column), though, the
    highest roof point is that house's ridge, and it steps where one house ends and the next begins (different
    height, or the street climbing a hill). So: measure the contiguous run of the mask through each column along x
    and along z, take the shorter one as "across", and return the highest roof on that run."""
    out = np.full(mask.shape, -1, np.int32)
    lens, maxs = [], []
    for axis in (1, 0):
        m = mask if axis == 1 else mask.T
        t = top if axis == 1 else top.T
        # label contiguous runs along rows
        start = m & ~np.pad(m, ((0, 0), (1, 0)))[:, :-1]
        run_id = np.cumsum(start.ravel()).reshape(m.shape) * m
        n = run_id.max()
        length = np.bincount(run_id.ravel(), minlength=n + 1)
        length[0] = 0
        rmax = ndi.maximum(t, run_id, index=np.arange(n + 1)).astype(np.int32)
        L, M = length[run_id], rmax[run_id]
        if axis == 0:
            L, M = L.T, M.T
        lens.append(L)
        maxs.append(M)
    across_x = lens[0] <= lens[1]
    out = np.where(across_x, maxs[0], maxs[1])
    return np.where(mask, out, -1)


def split_by_roof_steps(mask: np.ndarray, top: np.ndarray) -> np.ndarray:
    """Label `mask` into building parts. Neighbouring columns stay together when their roof step is below ROOF_STEP
    and their ridge heights across the wing (run_ridge) differ by at most RIDGE_STEP - 1; fragments smaller than
    MIN_PART_AREA then merge into the neighbour sharing the longest border."""
    h, w = mask.shape
    ridge = run_ridge(mask, top)
    idx = np.arange(h * w).reshape(h, w)
    pairs = []
    for dz, dx in ((0, 1), (1, 0)):
        a = mask[: h - dz, : w - dx] & mask[dz:, dx:]
        step = np.abs(top[: h - dz, : w - dx] - top[dz:, dx:]) < ROOF_STEP
        rstep = np.abs(ridge[: h - dz, : w - dx] - ridge[dz:, dx:]) < RIDGE_STEP
        sel = a & step & rstep
        pairs.append(np.stack([idx[: h - dz, : w - dx][sel], idx[dz:, dx:][sel]], axis=1))
    pairs = np.concatenate(pairs)
    labels = _components_from_pairs(h * w, pairs, mask.ravel()).reshape(h, w)
    return _merge_small(labels, MIN_PART_AREA)


def _is_wing_shaped(m: np.ndarray) -> bool:
    """A block made of narrow wings: big, and its median thickness (twice the distance to its edge) at most
    WING_MAX_WIDTH."""
    if m.sum() < 3 * MIN_PART_AREA:
        return False
    d = ndi.distance_transform_edt(np.pad(m, 1))[1:-1, 1:-1]
    return 2 * np.percentile(d[m], 90) <= WING_MAX_WIDTH


def _components_from_pairs(n: int, pairs: np.ndarray, valid: np.ndarray) -> np.ndarray:
    from scipy.sparse import coo_matrix
    from scipy.sparse.csgraph import connected_components
    g = coo_matrix((np.ones(len(pairs), np.int8), (pairs[:, 0], pairs[:, 1])), shape=(n, n))
    _, comp = connected_components(g, directed=False)
    out = np.zeros(n, np.int32)
    vals = comp[valid]
    _, inv = np.unique(vals, return_inverse=True)
    out[valid] = inv + 1
    return out


def _merge_small(labels: np.ndarray, min_area: int, rounds: int = 6) -> np.ndarray:
    lab = labels.copy()
    h, w = lab.shape
    for _ in range(rounds):
        n = int(lab.max())
        if n == 0:
            break
        areas = np.bincount(lab.ravel(), minlength=n + 1)
        small = np.zeros(n + 1, bool)
        small[1:] = areas[1:] < min_area
        if not small[1:].any():
            break
        ps, qs = [], []
        for dz, dx in ((0, 1), (1, 0)):
            a = lab[: h - dz, : w - dx]
            b = lab[dz:, dx:]
            sel = (a != b) & (a > 0) & (b > 0)
            ps += [a[sel], b[sel]]
            qs += [b[sel], a[sel]]
        p = np.concatenate(ps)
        q = np.concatenate(qs)
        m = small[p]
        p, q = p[m], q[m]
        if not len(p):
            break
        # count shared border per (small part, neighbour) and keep each small part's longest border
        key = p.astype(np.int64) * (n + 1) + q
        uk, cnt = np.unique(key, return_counts=True)
        up, uq = uk // (n + 1), uk % (n + 1)
        order = np.lexsort((-cnt, up))
        up, uq = up[order], uq[order]
        first = np.concatenate([[True], up[1:] != up[:-1]])
        up, uq = up[first], uq[first]
        # merge only into a larger neighbour, so two small parts never swap labels
        ok = areas[uq] >= areas[up]
        remap = np.arange(n + 1)
        remap[up[ok]] = uq[ok]
        if not ok.any():
            break
        for _ in range(8):
            remap = remap[remap]
        lab = remap[lab]
    return _compact(lab)


def _compact(lab: np.ndarray) -> np.ndarray:
    """Renumber labels 1..n in order; 0 stays background."""
    out = np.zeros(lab.shape, np.int32)
    pos = lab > 0
    if pos.any():
        _, inv = np.unique(lab[pos], return_inverse=True)
        out[pos] = inv + 1
    return out


@dataclass
class Building:
    id: int
    group: int                 # connected building block it belongs to
    x0: int
    z0: int
    x1: int
    z1: int
    area: int                  # footprint, m²
    top: int                   # highest built block (world y)
    bottom: int                # lowest built block
    ground: int                # median ground level around it
    height: int                # top above ground
    doors: int = 0
    beds: int = 0
    work: int = 0
    furnish: int = 0
    entrance: str = ""
    entrance_basis: str = ""
    type: str = "unknown"
    district: str = ""
    waterfront: bool = False
    on_water: bool = False
    min_width: int = 0
    group_area: int = 0        # footprint of the whole connected block
    group_top_median: int = 0  # median roof height of the whole block
    ring_top: int = 0          # highest built block within 4 blocks around the footprint
    interior_area: int = 0      # existing floor area: ground storey + upper floors (m²)
    ground_floor_area: int = 0
    upper_area: int = 0         # existing upper floors
    upper_empty_area: int = 0   # existing upper floors with (almost) no furnishing
    cellar_area: int = 0
    fitout_area: int = 0        # floor area new floors could add inside hollow height
    furnish_density: float = 0.0  # furnishing blocks per 100 m² of floor
    storeys: float = 0.0        # existing floor area / footprint


def find_buildings(sv: Survey) -> None:
    c = sv.scan.columns
    top = c["built_top"].astype(np.int32)
    groups, ng = ndi.label(sv.tall)
    sizes = np.bincount(groups.ravel(), minlength=ng + 1)
    keep = np.zeros(ng + 1, bool)
    keep[1:] = sizes[1:] >= MIN_BUILDING_AREA
    groups = np.where(keep[groups], groups, 0)
    # Split only wing-shaped blocks (terraces, perimeter blocks): a compact landmark such as a cathedral or a keep
    # stays whole even though its roof has steps.
    ridge_mask = np.zeros_like(sv.tall)
    for gi, s in enumerate(ndi.find_objects(groups), start=1):
        if s is None:
            continue
        m = groups[s] == gi
        on_water = np.median(sv.ground[s][m]) < sv.sea_level - 1  # ships and piers stay whole
        if not on_water and _is_wing_shaped(m):
            ridge_mask[s] |= m
    parts = split_by_roof_steps(ridge_mask, top)
    whole, _ = ndi.label((groups > 0) & ~ridge_mask)
    parts = np.where(whole > 0, whole + parts.max(), parts)
    # a part never spans two groups (they are not connected), so group id per part is unique
    sv.labels = parts
    n = parts.max()
    gidx = np.arange(ng + 1)
    group_area = np.bincount(groups.ravel(), minlength=ng + 1)
    group_top = ndi.median(top, groups, index=gidx) if ng else np.zeros(1)
    group_top = np.nan_to_num(np.asarray(group_top, float)).astype(int)
    objs = ndi.find_objects(parts)
    x0, z0 = sv.scan.x0, sv.scan.z0
    sl = sv.sea_level
    for i in range(1, n + 1):
        s = objs[i - 1]
        if s is None:
            continue
        m = parts[s] == i
        area = int(m.sum())
        if area < MIN_BUILDING_AREA:
            parts[s][m] = 0
            continue
        sub = lambda name: c[name][s][m]
        g = int(np.median(sv.ground[s][m]))
        tp = int(sub("built_top").max())
        bt = int(sub("built_bottom").min())
        dist = ndi.distance_transform_edt(np.pad(m, 1))
        b = Building(
            id=i, group=int(np.bincount(groups[s][m]).argmax()),
            x0=s[1].start + x0, z0=s[0].start + z0, x1=s[1].stop - 1 + x0, z1=s[0].stop - 1 + z0,
            area=area, top=tp, bottom=bt, ground=g, height=tp - g,
            doors=int(sub("doors").sum()) // 2, beds=int(sub("bed_blocks").sum()), work=int(sub("work").sum()),
            furnish=int(sub("furnish_n").sum()), min_width=int(round(2 * dist.max())) - 1,
        )
        b.on_water = bool(np.mean(sv.ground[s][m] < sl - 1) > 0.5)
        b.group_area = int(group_area[b.group])
        b.group_top_median = int(group_top[b.group])
        big = (slice(max(s[0].start - 4, 0), s[0].stop + 4), slice(max(s[1].start - 4, 0), s[1].stop + 4))
        mm = parts[big] == i
        ring = ndi.binary_dilation(mm, iterations=4) & ~mm
        rt = c["built_top"][big][ring]
        rt = rt[rt != NONE]
        b.ring_top = int(rt.max()) if len(rt) else b.ground
        sv.buildings.append(b)
    _relabel(sv)


def _relabel(sv: Survey) -> None:
    """Renumber buildings 1..n in reading order (north to south, west to east) so IDs are stable and browsable."""
    sv.buildings.sort(key=lambda b: ((b.z0 + b.z1) // 64, (b.x0 + b.x1) // 2))
    remap = np.zeros(sv.labels.max() + 1, np.int32)
    for new, b in enumerate(sv.buildings, start=1):
        remap[b.id] = new
        b.id = new
    sv.labels = remap[sv.labels]


def assign_floors(sv: Survey) -> None:
    """Measure each building's floors from its room slices (see scan.py).

    Per column, room slices below the building's ground level - 2 are CELLAR; the lowest other slice is the GROUND
    storey; any slices above it are existing UPPER floors. A slice taller than one storey (STOREY_HEIGHT blocks
    including the new floor) is hollow height where new floors could go: (height + 1) // STOREY_HEIGHT - 1 extra
    floors over that column. Many Drachenfels buildings are tall shells, so this "fit-out" space is most of the
    building's spare room."""
    runs = sv.scan.runs
    if not len(runs):
        return
    x0, z0 = sv.scan.x0, sv.scan.z0
    w = sv.labels.shape[1]
    col = runs[:, 0] - x0
    row = runs[:, 1] - z0
    lab = sv.labels[row, col]
    sel = lab > 0
    lab, fy, hgt, fur = lab[sel], runs[sel, 2], runs[sel, 3], runs[sel, 4]
    ckey = (row[sel].astype(np.int64) * w + col[sel])
    by_id = {b.id: b for b in sv.buildings}
    gl = np.zeros(sv.labels.max() + 1, np.int32)
    for b in sv.buildings:
        gl[b.id] = b.ground
    cellar = fy < gl[lab] - 2
    # rank non-cellar slices within their column, lowest first
    order = np.lexsort((fy, cellar, ckey))
    ck, cel = ckey[order], cellar[order]
    first = np.ones(len(order), bool)
    first[1:] = (ck[1:] != ck[:-1]) | (cel[1:] != cel[:-1])
    ground_storey = np.zeros(len(order), bool)
    ground_storey[order] = first & ~cel
    upper = ~cellar & ~ground_storey
    extra = np.maximum(0, (hgt + 1) // STOREY_HEIGHT - 1)
    n = sv.labels.max() + 1

    def per(mask, weights=None):
        return np.bincount(lab[mask], weights=None if weights is None else weights[mask], minlength=n)

    ground_a, upper_a, cellar_a = per(ground_storey), per(upper), per(cellar)
    upper_f = per(upper, fur)
    all_f = per(~cellar, fur)
    extra_a = per(~cellar, extra)
    for b in sv.buildings:
        i = b.id
        b.ground_floor_area = int(ground_a[i])
        b.upper_area = int(upper_a[i])
        b.cellar_area = int(cellar_a[i])
        b.interior_area = int(ground_a[i] + upper_a[i])
        dens = upper_f[i] * 100.0 / upper_a[i] if upper_a[i] else 0.0
        b.upper_empty_area = int(upper_a[i]) if dens < EMPTY_FURNISH_PER_100M2 else 0
        b.fitout_area = int(extra_a[i])
        b.furnish_density = round(all_f[i] * 100.0 / b.interior_area, 2) if b.interior_area else 0.0
        b.storeys = round((ground_a[i] + upper_a[i]) / max(b.area, 1), 1)


def classify_buildings(sv: Survey) -> None:
    for b in sv.buildings:
        w = b.x1 - b.x0 + 1
        d = b.z1 - b.z0 + 1
        long_side = max(w, d)
        if b.on_water:
            b.type = "on water"
        elif b.min_width <= 5 and long_side >= 24 and b.area < 0.35 * w * d + 6 * long_side:
            b.type = "wall segment"
        elif b.area <= 160 and b.height >= 16 and b.height >= 2.0 * np.sqrt(b.area) and (
                b.group_area <= 1.5 * b.area or b.top - b.ring_top >= TOWER_ABOVE_NEIGHBOURS):
            b.type = "tower"
        elif b.area <= 50 and b.interior_area < 20 and b.fitout_area < 20:
            b.type = "small structure"
        elif b.area >= 600 and _tall_hall(b):
            b.type = "hall"
        elif b.area <= 600:
            b.type = "house"
        elif b.area <= 2500:
            b.type = "large building"
        else:
            b.type = "building block"


def _tall_hall(b: Building) -> bool:
    """One big room high inside: tall, with little upper floor for its size."""
    return b.height >= 18 and (b.upper_area < 0.3 * b.area or b.fitout_area > 2 * b.area)


def entrances(sv: Survey) -> None:
    """Likely entrance side: where the doors are, or else the side with the most open street in front."""
    c = sv.scan.columns
    x0, z0 = sv.scan.x0, sv.scan.z0
    lab = sv.labels
    open_ground = sv.area & ~sv.tall & ~sv.water_surface
    for b in sv.buildings:
        s = (slice(b.z0 - z0, b.z1 - z0 + 1), slice(b.x0 - x0, b.x1 - x0 + 1))
        m = lab[s] == b.id
        doors = c["doors"][s] * m
        ring = ndi.binary_dilation(m, iterations=2) & ~ndi.binary_erosion(m, iterations=2)
        dz, dx = np.nonzero(doors * ring)
        cz, cx = np.argwhere(m).mean(0)
        if len(dz):
            vz, vx = dz.mean() - cz, dx.mean() - cx
            b.entrance_basis = "doors"
        else:
            # street frontage per side: open ground cells within 3 blocks outside each side of the footprint
            big = (slice(max(s[0].start - 3, 0), s[0].stop + 3), slice(max(s[1].start - 3, 0), s[1].stop + 3))
            mm = np.zeros(open_ground[big].shape, bool)
            oz, ox = s[0].start - big[0].start, s[1].start - big[1].start
            mm[oz:oz + m.shape[0], ox:ox + m.shape[1]] = m
            front = ndi.binary_dilation(mm, iterations=3) & ~mm & open_ground[big]
            fz, fx = np.nonzero(front)
            if not len(fz):
                b.entrance = "?"
                b.entrance_basis = "none"
                continue
            vz, vx = fz.mean() - (cz + oz), fx.mean() - (cx + ox)
            b.entrance_basis = "street"
        if abs(vx) >= abs(vz):
            b.entrance = "E" if vx > 0 else "W"
        else:
            b.entrance = "S" if vz > 0 else "N"


# ---------------------------------------------------------------- coast

SIDES = {"N": (-1, 0), "S": (1, 0), "W": (0, -1), "E": (0, 1)}


def coast(sv: Survey) -> dict:
    """Where the city's outline meets open water, how long that waterfront is, and how deep the water is offshore.

    Piers and moored ships are thin and would add water on every side, so the waterfront is measured on the outline
    with thin parts (under PIER_WIDTH) removed; how far the piers reach out is reported separately."""
    env = sv.envelope
    sea = sv.sea
    core = ndi.binary_opening(env, structure=_disk(PIER_WIDTH // 2))
    lab, n = ndi.label(core)
    if n:
        sizes = np.bincount(lab.ravel(), minlength=n + 1)
        sizes[0] = 0
        core = lab == sizes.argmax()
    dist_sea, (iz, ix) = ndi.distance_transform_edt(~sea, return_indices=True)
    edge = core & ~ndi.binary_erosion(core)
    wf = edge & (dist_sea <= WATERFRONT_REACH)
    zz, xx = np.nonzero(wf)
    vz = iz[zz, xx] - zz
    vx = ix[zz, xx] - xx
    side = np.where(np.abs(vx) >= np.abs(vz), np.where(vx > 0, "E", "W"), np.where(vz > 0, "S", "N"))
    sides = {s: int((side == s).sum()) for s in SIDES}
    # overall bearing of the sea from the city's middle (sea within 300 blocks of the outline)
    cz, cx = np.argwhere(core).mean(0)
    near = sea & (ndi.distance_transform_edt(~core) <= 300)
    sz, sx = np.nonzero(near)
    ang = np.degrees(np.arctan2(sx - cx, -(sz - cz))) % 360  # 0 = north, 90 = east
    hist = np.bincount(((ang + 22.5) // 45).astype(int) % 8, minlength=8)
    names8 = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"]
    bearing = {names8[i]: round(hist[i] / max(hist.sum(), 1), 3) for i in range(8)}
    # depth offshore: water_top - seabed, in bands out from the waterfront
    c = sv.scan.columns
    depth = np.where(sea, c["water_top"].astype(np.int32) - sv.ground, 0)
    dist_wf = ndi.distance_transform_edt(~wf) if wf.any() else np.full(sea.shape, 1e9)
    bands = {}
    for lo, hi in ((0, 16), (16, 48), (48, 96), (96, 160)):
        band = sea & (dist_wf > lo) & (dist_wf <= hi)
        v = depth[band]
        bands[f"{lo}-{hi}"] = {
            "columns": int(band.sum()),
            "median_depth": float(np.median(v)) if len(v) else None,
            "p10_depth": float(np.percentile(v, 10)) if len(v) else None,
            "p90_depth": float(np.percentile(v, 90)) if len(v) else None,
        }
    # piers: built things over open water joined to the city's waterfront, and how far out they reach
    dist_core = ndi.distance_transform_edt(~core)
    # (only structures rising above the water surface: the seabed has decoration of its own)
    over = (sv.built & ~core & (sv.ground < sv.sea_level - 1) & (dist_core <= PIER_SEARCH)
            & (c["built_top"].astype(np.int32) >= sv.sea_level))
    olab, on = ndi.label(over, structure=np.ones((3, 3), bool))
    touch = np.unique(olab[ndi.binary_dilation(wf, iterations=8) & over])
    piers = np.isin(olab, touch[touch > 0])
    reach = dist_core[piers] if piers.any() else np.zeros(1)
    wz, wx = (np.nonzero(wf) if wf.any() else (np.zeros(1, int), np.zeros(1, int)))
    return {
        "sea_level": sv.sea_level,
        "outline_area": int(core.sum()),
        "outline_edge": int(edge.sum()),
        "waterfront_length": int(wf.sum()),
        "waterfront_share": round(int(wf.sum()) / max(int(edge.sum()), 1), 3),
        "waterfront_sides": sides,
        "waterfront_span": {"x": [int(wx.min() + sv.scan.x0), int(wx.max() + sv.scan.x0)],
                            "z": [int(wz.min() + sv.scan.z0), int(wz.max() + sv.scan.z0)]},
        "sea_bearing_share": bearing,
        "offshore_depth": bands,
        "pier_reach_max": int(reach.max()),
        "pier_reach_p95": int(np.percentile(reach, 95)),
        "waterfront_mask": wf,
        "depth_map": depth,
    }
