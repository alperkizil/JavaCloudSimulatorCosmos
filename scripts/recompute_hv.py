#!/usr/bin/env python3
"""
Recompute per-run quality indicators with a scenario-fixed reference point.

The Java runners compute HV against an internal reference, which disadvantages
single-objective algorithms. This script recomputes three indicators from the
raw Pareto-graph CSVs using a reference point that is FIXED per scenario
(r = nadir + 10% of range across ALL algorithms' union), so all algorithms
are judged on the same footing.

Indicators produced per (Algorithm, Seed, Scenario):
  - HV_fixed         Normalized hypervolume (both objectives minimized), in [0, 1].
  - ParetoContribution_pct  Fraction (%) of the scenario's universal Pareto set
                           contributed by this seed of this algorithm.
  - EpsPlus          Additive epsilon indicator I_eps+ to the universal front,
                     computed in normalized [0, 1] space (lower is better;
                     0 means the seed reproduces the universal front).

Outputs:
  <reports_dir>/scenario_{s}_quality_indicators.csv

Usage:
    python recompute_hv.py <reports_directory>
"""

import os
import sys
import numpy as np
import pandas as pd


# =============================================================================
# Pareto helpers (both objectives minimized)
# =============================================================================

def non_dominated_sort(points):
    pts = np.asarray(points, dtype=float)
    if len(pts) == 0:
        return pts
    keep = []
    for i, p in enumerate(pts):
        dominated = False
        for j, q in enumerate(pts):
            if i == j:
                continue
            if q[0] <= p[0] and q[1] <= p[1] and (q[0] < p[0] or q[1] < p[1]):
                dominated = True
                break
        if not dominated:
            keep.append(p)
    if not keep:
        return np.empty((0, 2))
    nd = np.array(keep)
    # De-duplicate
    _, idx = np.unique(np.round(nd, decimals=9), axis=0, return_index=True)
    nd = nd[np.sort(idx)]
    return nd[np.argsort(nd[:, 0])]


def hv_2d(points, reference):
    """
    2D hypervolume for minimization. Points must be non-dominated
    (the routine filters anyway).
    """
    rx, ry = float(reference[0]), float(reference[1])
    nd = non_dominated_sort(points)
    if len(nd) == 0:
        return 0.0
    nd = nd[(nd[:, 0] < rx) & (nd[:, 1] < ry)]
    if len(nd) == 0:
        return 0.0
    nd = nd[np.argsort(nd[:, 0])]
    hv = 0.0
    prev_y = ry
    for xi, yi in nd:
        hv += (rx - xi) * (prev_y - yi)
        prev_y = yi
    return float(hv)


def epsilon_plus(approx, reference_front):
    """
    Additive epsilon indicator I_eps+(A, R): minimum eps such that every point
    in R is weakly dominated by some (a - eps) for a in A. Expects normalized
    inputs and objective minimization.
    """
    a = np.asarray(approx, dtype=float)
    r = np.asarray(reference_front, dtype=float)
    if len(a) == 0 or len(r) == 0:
        return float('nan')
    eps = 0.0
    for rp in r:
        best = min(max(ap[0] - rp[0], ap[1] - rp[1]) for ap in a)
        eps = max(eps, best)
    return float(eps)


def normalize(points, bounds):
    ideal_x, ideal_y, nadir_x, nadir_y = bounds
    rx = max(nadir_x - ideal_x, 1e-12)
    ry = max(nadir_y - ideal_y, 1e-12)
    out = np.empty_like(np.asarray(points, dtype=float))
    pts = np.asarray(points, dtype=float)
    out[:, 0] = (pts[:, 0] - ideal_x) / rx
    out[:, 1] = (pts[:, 1] - ideal_y) / ry
    return out


# =============================================================================
# CSV plumbing
# =============================================================================

def detect_objective_columns(df):
    cols = list(df.columns)
    if len(cols) >= 4 and cols[0] == 'Algorithm' and cols[1] == 'Seed':
        return cols[2], cols[3]
    numeric = [c for c in cols if c not in ('Algorithm', 'Seed', 'IsUniversalPareto')]
    return (numeric[0], numeric[1]) if len(numeric) >= 2 else ('Makespan', 'Energy')


def compute_scenario_indicators(df, scenario_num):
    """Returns a DataFrame of per-run indicators for one scenario."""
    x_col, y_col = detect_objective_columns(df)

    non_univ = df[df['Algorithm'] != 'Universal_Pareto']
    universal = df[df['Algorithm'] == 'Universal_Pareto'][[x_col, y_col]].values

    # Scenario-fixed reference point: 10% beyond the union's nadir.
    all_pts = df[[x_col, y_col]].values.astype(float)
    ideal_x = float(all_pts[:, 0].min())
    ideal_y = float(all_pts[:, 1].min())
    nadir_x = float(all_pts[:, 0].max())
    nadir_y = float(all_pts[:, 1].max())
    rng_x = max(nadir_x - ideal_x, 1e-12)
    rng_y = max(nadir_y - ideal_y, 1e-12)
    ref_x = nadir_x + 0.1 * rng_x
    ref_y = nadir_y + 0.1 * rng_y
    # Normalized reference: same formula yields (1.1, 1.1) in normalized space.
    ref_norm = (1.1, 1.1)
    bounds = (ideal_x, ideal_y, nadir_x, nadir_y)

    # Universal Pareto (normalized) for epsilon+.
    univ_norm = normalize(universal, bounds) if len(universal) else np.empty((0, 2))
    univ_sig = set(map(tuple, np.round(univ_norm, decimals=9))) if len(univ_norm) else set()
    univ_count = len(univ_norm)

    rows = []
    for (algo, seed), sub in non_univ.groupby(['Algorithm', 'Seed']):
        pts = sub[[x_col, y_col]].values.astype(float)
        pts_norm = normalize(pts, bounds)

        hv = hv_2d(pts_norm, ref_norm)
        hv_max = ref_norm[0] * ref_norm[1]  # theoretical upper bound in [0,1] frame
        hv_normalized = hv / hv_max

        eps_plus = epsilon_plus(pts_norm, univ_norm) if len(univ_norm) else float('nan')

        if univ_count:
            matched = sum(
                1 for pt in np.round(pts_norm, decimals=9)
                if tuple(pt) in univ_sig
            )
            contrib_pct = 100.0 * matched / univ_count
        else:
            contrib_pct = float('nan')

        rows.append({
            'Scenario': scenario_num,
            'Algorithm': algo,
            'Seed': int(seed),
            'HV_fixed': hv_normalized,
            'EpsPlus': eps_plus,
            'ParetoContribution_pct': contrib_pct,
            'nSolutions': int(len(pts)),
            'ref_x_raw': ref_x,
            'ref_y_raw': ref_y,
        })
    return pd.DataFrame(rows)


def process_directory(reports_dir, num_scenarios=6):
    wrote_any = False
    combined = []
    for s in range(1, num_scenarios + 1):
        fp = os.path.join(reports_dir, f'scenario_{s}_pareto_graph_data.csv')
        if not os.path.exists(fp):
            continue
        df = pd.read_csv(fp)
        out = compute_scenario_indicators(df, s)
        out_path = os.path.join(reports_dir, f'scenario_{s}_quality_indicators.csv')
        out.to_csv(out_path, index=False)
        print(f'  Saved: {out_path}  ({len(out)} rows)')
        combined.append(out)
        wrote_any = True

    if combined:
        all_df = pd.concat(combined, ignore_index=True)
        all_path = os.path.join(reports_dir, 'quality_indicators_all_scenarios.csv')
        all_df.to_csv(all_path, index=False)
        print(f'  Saved: {all_path}  ({len(all_df)} rows total)')

        print('\nMedian [IQR] per algorithm (pooled across scenarios):')
        print(f'  {"Algorithm":<22} {"HV_fixed":<22} {"EpsPlus":<22} {"PCont%":<22}')
        for algo, sub in all_df.groupby('Algorithm'):
            def stats(col):
                v = sub[col].dropna().values
                if len(v) == 0:
                    return '--'
                return f'{np.median(v):.3f} [{np.percentile(v, 25):.3f},{np.percentile(v, 75):.3f}]'
            print(f'  {algo:<22} {stats("HV_fixed"):<22} {stats("EpsPlus"):<22} '
                  f'{stats("ParetoContribution_pct"):<22}')

    if not wrote_any:
        print('No Pareto-graph CSVs found.')
        return 1
    return 0


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Usage: python recompute_hv.py <reports_directory>')
        sys.exit(1)
    target = sys.argv[1]
    if not os.path.isdir(target):
        print(f'ERROR: {target} is not a directory')
        sys.exit(1)
    sys.exit(process_directory(target))
