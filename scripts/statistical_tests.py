#!/usr/bin/env python3
"""
Statistical analysis of JavaCloudSimulatorCosmos experiments.

Reads per-scenario performance-metrics CSVs and reports:
  - Friedman omnibus test per metric (HV, GD, IGD)
  - Pairwise Wilcoxon signed-rank + Holm-Bonferroni correction
  - Vargha-Delaney A12 effect size for each pair
  - Critical-difference (Nemenyi) diagram per metric

Outputs (into <reports_dir>):
  - statistical_tests_summary.csv
  - cd_diagram_HV.png
  - cd_diagram_GD.png
  - cd_diagram_IGD.png

Usage:
    python statistical_tests.py <reports_directory>
"""

import os
import sys
import math
import itertools
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt


# =============================================================================
# Statistical helpers (no scipy dependency)
# =============================================================================

def rankdata(x):
    """Average ranks with tie handling; rank 1 = smallest value."""
    x = np.asarray(x, float)
    order = np.argsort(x, kind='mergesort')
    ranks = np.empty_like(order, dtype=float)
    i = 0
    n = len(x)
    while i < n:
        j = i
        while j + 1 < n and x[order[j + 1]] == x[order[i]]:
            j += 1
        avg = (i + j) / 2.0 + 1.0
        for k in range(i, j + 1):
            ranks[order[k]] = avg
        i = j + 1
    return ranks


def norm_cdf(z):
    return 0.5 * (1.0 + math.erf(z / math.sqrt(2.0)))


def wilcoxon_signed_rank(x, y):
    """
    Two-sided paired Wilcoxon signed-rank (normal approximation with
    continuity and tie correction). Returns (W, p_value).
    """
    d = np.asarray(x, float) - np.asarray(y, float)
    d = d[d != 0]
    n = len(d)
    if n == 0:
        return 0.0, 1.0
    ranks = rankdata(np.abs(d))
    W_plus = float(ranks[d > 0].sum())
    W_minus = float(ranks[d < 0].sum())
    W = min(W_plus, W_minus)
    mean = n * (n + 1) / 4.0
    _, tie_counts = np.unique(np.abs(d), return_counts=True)
    tie_sum = sum(int(t) ** 3 - int(t) for t in tie_counts)
    var = (n * (n + 1) * (2 * n + 1) - 0.5 * tie_sum) / 24.0
    if var <= 0:
        return W, 1.0
    z = (W - mean + 0.5) / math.sqrt(var)
    p = 2.0 * (1.0 - norm_cdf(abs(z)))
    return W, max(0.0, min(1.0, p))


def holm_bonferroni(p_values):
    """Holm step-down. Returns adjusted p-values in the original order."""
    m = len(p_values)
    order = np.argsort(p_values)
    adj = np.empty(m)
    running = 0.0
    for rank, idx in enumerate(order):
        val = (m - rank) * p_values[idx]
        running = max(running, val)
        adj[idx] = min(1.0, running)
    return adj


def vargha_delaney_a12(x, y):
    """Probability that a random X beats a random Y (0.5 = no effect)."""
    x = np.asarray(x, float)
    y = np.asarray(y, float)
    m, n = len(x), len(y)
    gt = 0
    eq = 0
    for xi in x:
        gt += int(np.sum(y < xi))
        eq += int(np.sum(y == xi))
    return (gt + 0.5 * eq) / (m * n)


def _lower_regularized_gamma(a, x):
    if x <= 0:
        return 0.0
    if x < a + 1:
        term = 1.0 / a
        total = term
        for i in range(1, 300):
            term *= x / (a + i)
            total += term
            if abs(term) < 1e-14 * abs(total):
                break
        return total * math.exp(-x + a * math.log(x) - math.lgamma(a))
    # Continued fraction for Q(a, x); return 1 - Q
    b = x + 1.0 - a
    c = 1e30
    d = 1.0 / b
    h = d
    for i in range(1, 300):
        an = -i * (i - a)
        b += 2.0
        d = an * d + b
        if abs(d) < 1e-30:
            d = 1e-30
        c = b + an / c
        if abs(c) < 1e-30:
            c = 1e-30
        d = 1.0 / d
        delta = d * c
        h *= delta
        if abs(delta - 1.0) < 1e-14:
            break
    q = math.exp(-x + a * math.log(x) - math.lgamma(a)) * h
    return 1.0 - q


def chi2_cdf(x, df):
    if x <= 0 or df <= 0:
        return 0.0
    return _lower_regularized_gamma(df / 2.0, x / 2.0)


def friedman_test(matrix):
    """
    matrix: shape (N_problems, k_algorithms) with values to RANK
    (rank 1 = best). Returns (chi2, p, mean_ranks).
    """
    N, k = matrix.shape
    ranks = np.apply_along_axis(rankdata, 1, matrix)
    R_mean = ranks.mean(axis=0)
    chi2 = (12.0 * N / (k * (k + 1))) * ((R_mean ** 2).sum() - k * (k + 1) ** 2 / 4.0)
    p = 1.0 - chi2_cdf(chi2, k - 1)
    return chi2, p, R_mean


# Nemenyi q_alpha critical values (Demsar 2006, Table 5).
_Q_ALPHA_005 = {
    2: 1.960, 3: 2.343, 4: 2.569, 5: 2.728, 6: 2.850,
    7: 2.949, 8: 3.031, 9: 3.102, 10: 3.164,
}


def nemenyi_cd(k, N, alpha=0.05):
    q = _Q_ALPHA_005.get(k)
    if q is None:
        return None
    return q * math.sqrt(k * (k + 1) / (6.0 * N))


# =============================================================================
# Critical-Difference diagram (Demsar 2006)
# =============================================================================

def draw_cd_diagram(mean_ranks, names, cd, title, out_path):
    k = len(mean_ranks)
    order = np.argsort(mean_ranks)
    sorted_ranks = np.array(mean_ranks)[order]
    sorted_names = [names[i] for i in order]

    fig_h = 2.0 + 0.35 * k
    fig, ax = plt.subplots(figsize=(10, fig_h))
    ax.set_xlim(0.5, k + 0.5)
    ax.set_ylim(0.0, fig_h)
    ax.invert_xaxis()
    ax.set_frame_on(False)
    ax.get_yaxis().set_visible(False)
    ax.get_xaxis().set_visible(False)

    y_axis = fig_h - 0.8
    ax.plot([0.5, k + 0.5], [y_axis, y_axis], 'k-', lw=1)
    for xt in range(1, k + 1):
        ax.plot([xt, xt], [y_axis - 0.08, y_axis + 0.08], 'k-', lw=1)
        ax.text(xt, y_axis + 0.22, str(xt), ha='center', va='bottom', fontsize=10)

    # Algorithm labels: left half first (best ranks on the right visually),
    # right half after.
    half = (k + 1) // 2
    for i in range(k):
        rank = sorted_ranks[i]
        name = sorted_names[i]
        if i < half:
            ty = y_axis - 0.5 - i * 0.35
            ax.plot([rank, rank], [y_axis, ty], 'k-', lw=0.6)
            ax.plot([rank, 0.6], [ty, ty], 'k-', lw=0.6)
            ax.text(0.55, ty, f'{name} ({rank:.2f})',
                    ha='right', va='center', fontsize=9)
        else:
            ti = (k - 1) - i
            ty = y_axis - 0.5 - ti * 0.35
            ax.plot([rank, rank], [y_axis, ty], 'k-', lw=0.6)
            ax.plot([rank, k + 0.4], [ty, ty], 'k-', lw=0.6)
            ax.text(k + 0.45, ty, f'{name} ({rank:.2f})',
                    ha='left', va='center', fontsize=9)

    if cd is not None:
        cd_y = y_axis + 0.55
        ax.plot([1, 1 + cd], [cd_y, cd_y], 'k-', lw=2)
        ax.plot([1, 1], [cd_y - 0.08, cd_y + 0.08], 'k-', lw=1)
        ax.plot([1 + cd, 1 + cd], [cd_y - 0.08, cd_y + 0.08], 'k-', lw=1)
        ax.text(1 + cd / 2.0, cd_y + 0.18, f'CD = {cd:.3f}',
                ha='center', fontsize=9)

        # Maximal cliques: groups of consecutively-ranked algos within CD.
        cliques = []
        i = 0
        while i < k:
            j = i
            while j + 1 < k and (sorted_ranks[j + 1] - sorted_ranks[i]) <= cd + 1e-9:
                j += 1
            if j > i:
                cliques.append((i, j))
            i += 1
        seen = set()
        maximal = []
        for c in cliques:
            dominated = any(o != c and o[0] <= c[0] and o[1] >= c[1] for o in cliques)
            if not dominated and c not in seen:
                maximal.append(c)
                seen.add(c)
        for idx, (a, b) in enumerate(maximal):
            cy = y_axis - 0.22 - idx * 0.14
            ax.plot([sorted_ranks[a], sorted_ranks[b]], [cy, cy],
                    'k-', lw=3, solid_capstyle='round')

    ax.set_title(title, fontsize=11, fontweight='bold', pad=10)
    fig.savefig(out_path, dpi=300, bbox_inches='tight')
    plt.close(fig)


# =============================================================================
# Main analysis
# =============================================================================

def load_all_metrics(reports_dir, num_scenarios=6):
    """Load every scenario_*_performance_metrics.csv present (up to num_scenarios)."""
    rows = []
    for s in range(1, num_scenarios + 1):
        fp = os.path.join(reports_dir, f'scenario_{s}_performance_metrics.csv')
        if not os.path.exists(fp):
            continue
        df = pd.read_csv(fp)
        df = df[df['Algorithm'] != 'Universal_Pareto'].copy()
        df['Scenario'] = s
        rows.append(df)
    return pd.concat(rows, ignore_index=True) if rows else pd.DataFrame()


def pivot_for_metric(df, metric):
    """Return (N_problems x k_algorithms) matrix, paired on (Scenario, Seed)."""
    pivot = df.pivot_table(
        index=['Scenario', 'Seed'], columns='Algorithm',
        values=metric, aggfunc='first',
    )
    return pivot.dropna(how='any')


def analyze_metric(df, metric, higher_is_better, out_dir):
    print(f'\n=== {metric}  (higher is better: {higher_is_better}) ===')
    pivot = pivot_for_metric(df, metric)
    N, k = pivot.shape
    if N < 5 or k < 2:
        print(f'  Insufficient data: N={N}, k={k}. Skipping.')
        return []

    algos = list(pivot.columns)
    matrix = pivot.values.astype(float)
    to_rank = -matrix if higher_is_better else matrix
    chi2, p, mean_ranks = friedman_test(to_rank)
    cd = nemenyi_cd(k, N, alpha=0.05)

    print(f'  Friedman: chi2={chi2:.3f}, p={p:.4g}, N={N}, k={k}')
    for a, r in zip(algos, mean_ranks):
        print(f'    {a:<22} mean rank = {r:.3f}')
    if cd is not None:
        print(f'  Nemenyi CD (alpha=0.05) = {cd:.3f}')
        cd_path = os.path.join(out_dir, f'cd_diagram_{metric}.png')
        title = (f'Critical-Difference Diagram: {metric}'
                 f'  (Friedman p={p:.3g}, N={N})')
        draw_cd_diagram(mean_ranks, algos, cd, title, cd_path)
        print(f'  Saved: {cd_path}')
    else:
        print(f'  Nemenyi CD table has no entry for k={k}; skipping diagram.')

    # Pairwise Wilcoxon + Vargha-Delaney
    pairs = list(itertools.combinations(range(k), 2))
    raw_p = []
    rows = []
    for i, j in pairs:
        x = matrix[:, i]
        y = matrix[:, j]
        W, p_w = wilcoxon_signed_rank(x, y)
        a12 = vargha_delaney_a12(x, y)
        raw_p.append(p_w)
        rows.append({
            'metric': metric,
            'A': algos[i], 'B': algos[j],
            'median_A': float(np.median(x)),
            'median_B': float(np.median(y)),
            'W': float(W),
            'p_raw': p_w,
            'A12_A_over_B': a12,
            'N_pairs': N,
        })
    adj = holm_bonferroni(raw_p)
    for row, ap in zip(rows, adj):
        row['p_holm'] = float(ap)
        row['significant_0.05'] = bool(ap < 0.05)
    return rows


def main(reports_dir):
    df = load_all_metrics(reports_dir)
    if df.empty:
        print('No performance_metrics CSVs found.')
        return 1
    print(f'Loaded {len(df)} rows across scenarios {sorted(df["Scenario"].unique())}.')

    all_rows = []
    for metric, hib in [('HV', True), ('GD', False), ('IGD', False)]:
        if metric in df.columns:
            all_rows.extend(analyze_metric(df, metric, hib, reports_dir))

    if all_rows:
        out_csv = os.path.join(reports_dir, 'statistical_tests_summary.csv')
        pd.DataFrame(all_rows).to_csv(out_csv, index=False)
        print(f'\nSaved: {out_csv}')
    return 0


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Usage: python statistical_tests.py <reports_directory>')
        sys.exit(1)
    target = sys.argv[1]
    if not os.path.isdir(target):
        print(f'ERROR: {target} is not a directory')
        sys.exit(1)
    sys.exit(main(target))
