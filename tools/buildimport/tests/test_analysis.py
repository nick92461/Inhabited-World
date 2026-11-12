import numpy as np

from buildimport import analysis as A


def test_fill_nearest():
    v = np.array([[1, 0, 0, 5]])
    known = np.array([[True, False, False, True]])
    assert list(A.fill_nearest(v, known)[0]) == [1, 1, 5, 5]


def test_run_ridge_takes_the_highest_roof_across_a_wing():
    # an east-west wing 6 deep: gable roof peaking in the middle rows; two houses of different ridge height
    mask = np.zeros((8, 40), bool)
    mask[1:7, :] = True
    top = np.zeros((8, 40), int)
    profile = [100, 101, 102, 102, 101, 100]
    for r, h in enumerate(profile, start=1):
        top[r, :20] = h
        top[r, 20:] = h + 3
    ridge = A.run_ridge(mask, top)
    assert ridge[1, 5] == 102 and ridge[6, 5] == 102 and ridge[3, 30] == 105
    assert ridge[0, 0] == -1


def test_split_by_roof_steps_separates_row_houses_and_merges_slivers():
    mask = np.zeros((8, 60), bool)
    mask[1:7, :] = True
    top = np.zeros((8, 60), int)
    profile = [100, 101, 102, 102, 101, 100]
    for r, h in enumerate(profile, start=1):
        top[r, :20] = h            # house A
        top[r, 20:40] = h + 2      # house B: ridge 2 higher (roofs still meet at one-block steps)
        top[r, 40:] = h            # house C
    top[1:7, 40] = 104             # a 1-wide chimney-like sliver at the B/C join
    lab = A.split_by_roof_steps(mask, top)
    assert lab[3, 5] != lab[3, 30] != lab[3, 50]
    assert lab[3, 5] == lab[6, 15]
    assert len(np.unique(lab[mask])) == 3        # the sliver merged into a neighbour
    assert (lab[~mask] == 0).all()


def test_merge_small_only_into_larger_neighbours():
    lab = np.zeros((4, 30), np.int32)
    lab[:, :20] = 1
    lab[:, 20:22] = 2                # 8 cells: small
    lab[:, 22:] = 3                  # 32 cells
    out = A._merge_small(lab, 10)
    assert out[0, 20] in (out[0, 0], out[0, 25])
    assert len(np.unique(out[out > 0])) == 2


def test_wing_shape():
    thin = np.zeros((40, 200), bool)
    thin[10:24, :] = True            # 14 deep, 200 long
    blob = np.zeros((80, 80), bool)
    blob[5:75, 5:75] = True          # 70 x 70 hall
    assert A._is_wing_shaped(thin)
    assert not A._is_wing_shaped(blob)
