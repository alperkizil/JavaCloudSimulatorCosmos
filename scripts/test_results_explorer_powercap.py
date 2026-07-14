#!/usr/bin/env python3
"""
Parity + regression test for results_explorer.py's PowerCap mode.

Given a PowerCeiling result folder that contains the NATIVE *_by_cap.csv files
(e.g. one written by com.cloudsimulator.observer.SyntheticPowerCeilingFolder,
or a real post-fix campaign), this script:

  1. loads it and checks the native per-tier tables parse correctly;
  2. copies it to a temp dir WITHOUT the *_by_cap.csv files, forcing the
     explorer's in-Python recompute, and compares every per-tier number
     (universal fronts, HV/GD/IGD/Spacing/HV_fixed, strict contribution
     counts, near-tie collaboration scoreboard, MEAN/STDDEV rows) against the
     native Java values;
  3. optionally (second argument) loads a NON-powercap folder and asserts the
     explorer treats it exactly as before (no powercap mode).

Usage:
    python scripts/test_results_explorer_powercap.py <powercap_folder> [plain_folder]

Exit code 0 = all checks passed.
"""

import os
import shutil
import sys
import tempfile

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from results_explorer import ExperimentData, UNCAPPED_TIER, split_tier  # noqa: E402

FAILURES = 0
CHECKS = 0


def check(what, ok, detail=''):
    global FAILURES, CHECKS
    CHECKS += 1
    if not ok:
        FAILURES += 1
        print(f'  FAIL {what}{": " + detail if detail else ""}')


def close(a, b, tol):
    a, b = float(a), float(b)
    if np.isnan(a) and np.isnan(b):
        return True
    return abs(a - b) <= tol


def compare_tier_tables(native, recomputed, scn_num, tier):
    tag = f's{scn_num}/{tier}'
    nat, rec = native.tiers[tier], recomputed.tiers[tier]

    # Universal fronts: same size, same points (CSV print precision).
    check(f'{tag} universal size', len(nat.universal) == len(rec.universal),
          f'native={len(nat.universal)} recomputed={len(rec.universal)}')
    if len(nat.universal) == len(rec.universal):
        n = nat.universal.to_numpy(dtype=float)
        r = rec.universal.to_numpy(dtype=float)
        check(f'{tag} universal points', bool(np.allclose(n, r, atol=1e-6)),
              'max |d|=%.3g' % float(np.max(np.abs(n - r))) if len(n) else '')

    # Per-seed metric rows.
    tol = {'HV': 1e-4, 'GD': 1e-4, 'IGD': 1e-4, 'Spacing': 1e-4, 'HV_fixed': 2e-3}
    nseed = nat.metrics_seed.set_index(['Algorithm', 'Seed'])
    rseed = rec.metrics_seed.set_index(['Algorithm', 'Seed'])
    check(f'{tag} metric row count', len(nseed) == len(rseed),
          f'native={len(nseed)} recomputed={len(rseed)}')
    for key, nrow in nseed.iterrows():
        if key not in rseed.index:
            check(f'{tag} row {key} present in recompute', False)
            continue
        rrow = rseed.loc[key]
        for col, t in tol.items():
            check(f'{tag} {key} {col}', close(nrow[col], rrow[col], t),
                  f'native={nrow[col]:.6f} recomputed={rrow[col]:.6f}')
        for col in ('NonDomSolutions', 'TotalSolutions', 'ParetoContribution'):
            check(f'{tag} {key} {col}', int(nrow[col]) == int(rrow[col]),
                  f'native={int(nrow[col])} recomputed={int(rrow[col])}')
        check(f'{tag} {key} TimeMs', close(nrow['TimeMs'], rrow['TimeMs'], 0.5))

    # MEAN / STDDEV rows + universal trailer.
    for col in ('HV', 'GD', 'IGD', 'Spacing', 'HV_fixed'):
        for algo in nat.metrics_mean.index:
            check(f'{tag} MEAN {algo} {col}',
                  close(nat.metrics_mean.loc[algo, col],
                        rec.metrics_mean.loc[algo, col], 1e-4))
            check(f'{tag} STDDEV {algo} {col}',
                  close(nat.metrics_std.loc[algo, col],
                        rec.metrics_std.loc[algo, col], 1e-4))
    for algo in nat.metrics_mean.index:
        check(f'{tag} MEAN {algo} union contribution',
              int(nat.metrics_mean.loc[algo, 'ParetoContribution'])
              == int(rec.metrics_mean.loc[algo, 'ParetoContribution']))
    for col, t in (('HV', 1e-4), ('HV_fixed', 2e-3), ('NonDomSolutions', 0.5)):
        check(f'{tag} universal trailer {col}',
              close(nat.universal_metrics[col], rec.universal_metrics[col], t),
              f'native={nat.universal_metrics[col]} recomputed={rec.universal_metrics[col]}')

    # Seed-collaboration scoreboard.
    ncol = nat.collab_seed.set_index(['Algorithm', 'Seed'])
    rcol = rec.collab_seed.set_index(['Algorithm', 'Seed'])
    check(f'{tag} collab row count', len(ncol) == len(rcol))
    for key, nrow in ncol.iterrows():
        if key not in rcol.index:
            check(f'{tag} collab row {key} present', False)
            continue
        rrow = rcol.loc[key]
        check(f'{tag} collab {key} count',
              int(nrow['ContributionCount']) == int(rrow['ContributionCount']),
              f'native={int(nrow["ContributionCount"])} recomputed={int(rrow["ContributionCount"])}')
        check(f'{tag} collab {key} front size',
              int(nrow['SeedUniversalFrontSize']) == int(rrow['SeedUniversalFrontSize']))
        check(f'{tag} collab {key} pct', close(nrow['ContributionPct'],
                                               rrow['ContributionPct'], 1e-3))
        check(f'{tag} collab {key} seed-univ HV_fixed',
              close(nrow['SeedUniversalHV_fixed'], rrow['SeedUniversalHV_fixed'], 2e-3))


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    folder = sys.argv[1]

    print(f'-- Loading native: {folder}')
    native = ExperimentData.load(folder)
    check('powercap detected', native.is_powercap)
    check('by_cap source native', native.by_cap_source == 'native',
          str(native.by_cap_source))
    check('tier order', native.tier_names[0] == UNCAPPED_TIER
          and native.tier_names[1:] == sorted(native.tier_names[1:],
                                              key=lambda t: -int(t[2:])),
          str(native.tier_names))
    check('base algorithms have no tier suffix',
          all(split_tier(b)[1] == UNCAPPED_TIER for b in native.base_algorithms))
    check('cap watts known for capped tiers',
          all(t in native.cap_watts for t in native.tier_names if t != UNCAPPED_TIER),
          str(native.cap_watts))
    check('feasibility table loaded', native.feasibility is not None)
    for n, scn in native.scenarios.items():
        for tier in native.tier_names:
            td = scn.tiers[tier]
            check(f's{n}/{tier} native tables parsed',
                  td.universal is not None and td.metrics_seed is not None
                  and td.collab_seed is not None and len(td.fronts) > 0)

    print('-- Recompute parity (copy without *_by_cap.csv)')
    with tempfile.TemporaryDirectory() as tmp:
        copy = os.path.join(tmp, os.path.basename(folder))
        shutil.copytree(folder, copy)
        for f in os.listdir(copy):
            if f.endswith('_by_cap.csv'):
                os.remove(os.path.join(copy, f))
        recomputed = ExperimentData.load(copy)
        check('recompute source flagged', recomputed.by_cap_source == 'recomputed',
              str(recomputed.by_cap_source))
        check('cap watts via feasibility fallback',
              all(t in recomputed.cap_watts for t in recomputed.tier_names
                  if t != UNCAPPED_TIER), str(recomputed.cap_watts))
        for n in sorted(native.scenarios):
            for tier in native.tier_names:
                compare_tier_tables(native.scenarios[n], recomputed.scenarios[n], n, tier)

    if len(sys.argv) > 2:
        plain_folder = sys.argv[2]
        print(f'-- Non-powercap regression: {plain_folder}')
        plain = ExperimentData.load(plain_folder)
        check('plain folder not powercap', not plain.is_powercap)
        check('plain folder has no tiers',
              all(not scn.tiers for scn in plain.scenarios.values()))
        check('plain folder algorithms intact', len(plain.algorithms) > 0)
        first = next(iter(plain.scenarios.values()))
        check('plain folder universal front intact',
              first.universal is not None and len(first.universal) > 0)

    print()
    print(f'Checks: {CHECKS}, failures: {FAILURES}')
    print('=== PASSED ===' if FAILURES == 0 else '=== FAILED ===')
    return 0 if FAILURES == 0 else 1


if __name__ == '__main__':
    sys.exit(main())
