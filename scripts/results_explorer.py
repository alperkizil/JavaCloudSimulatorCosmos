#!/usr/bin/env python3
"""
COSMOS Experiment Results Explorer — interactive GUI for campaign result folders.

Manually invoked (never auto-run by the pipeline):

    python scripts/results_explorer.py [results/<ExperimentId>]

Loads a campaign result folder (the directory holding scenario_N_*.csv,
experiment_summary.csv, plot_options.json) and provides:

  * Scatter tab   — universal Pareto front (always shown, connected), per-
                    algorithm pooled fronts (union over seeds, connected),
                    optional raw per-seed point clouds, X/Y swap, custom title,
                    live per-algorithm colors, family-stable markers,
                    legend / per-algorithm label toggles, box + wheel zoom.
  * Metrics tab   — bar charts (mean ± std over seeds) for HV_fixed / HV / GD /
                    IGD / Spacing / Pareto contribution, one panel per scenario.
  * Runtime tab   — average runtime per algorithm, per scenario + overall.
  * Tables tab    — per-seed metric tables (all seeds + MEAN/STDDEV + universal
                    front info) and the seed-collaboration scoreboard.
  * Details tab   — per-solution schedule details (task→VM assignments, per-VM
                    queue sizes, per-host energy, makespan / wait / energy
                    aggregates) read from scenario_N_solution_details.json when
                    the run was produced by a build that exports it.
  * Save          — any figure to PNG/SVG/PDF at a chosen DPI, any table to
                    CSV, and a single-file "Save for Claude" Markdown bundle of
                    every table/front for LLM analysis.

Headless bulk export (no GUI, useful for scripting):

    python scripts/results_explorer.py <folder> --export-all <outdir>

Only needs pandas + numpy + matplotlib (same as the rest of the pipeline);
tkinter is imported only when the GUI actually starts.
"""

import argparse
import gzip
import json
import math
import os
import re
import sys
from datetime import datetime, timezone

import numpy as np
import pandas as pd

import matplotlib as mpl
from matplotlib.figure import Figure

mpl.rcParams.update({
    'font.size': 10,
    'axes.titlesize': 12,
    'axes.labelsize': 11,
    'axes.grid': True,
    'grid.alpha': 0.3,
    'figure.autolayout': False,
})

UNIVERSAL_KEY = 'Universal_Pareto'
UNIVERSAL_COLOR = '#000000'
UNIVERSAL_LABEL = 'Universal Pareto'

# =============================================================================
# STYLES — Okabe-Ito palette, family-stable markers (GA=diamond, SA=square,
# NSGA-II=circle, SPEA-II=triangle, AMOSA=inverted triangle). Colors are only
# DEFAULTS: the GUI lets the user recolor any algorithm on the fly; markers
# stay fixed so a family keeps its shape across recolorings.
# =============================================================================

OKABE_ITO = {
    'blue': '#0072B2', 'vermillion': '#D55E00', 'bluish_green': '#009E73',
    'reddish_purple': '#CC79A7', 'orange': '#E69F00', 'sky_blue': '#56B4E9',
    'yellow': '#F0E442', 'black': '#000000',
}

# label -> (color, marker, filled, display label). Mirrors the defaults of
# scripts/plot_scenario_pareto.py so the GUI and the publication plots agree.
ALGORITHM_STYLE = {
    'GA_Makespan':              (OKABE_ITO['blue'], 'D', False, 'GA (Makespan)'),
    'GA_WaitingTime':           (OKABE_ITO['blue'], 'D', False, 'GA (Waiting Time)'),
    'GA_Energy':                (OKABE_ITO['blue'], 'D', True, 'GA (Energy)'),
    'SA_Makespan':              (OKABE_ITO['vermillion'], 's', False, 'SA (Makespan)'),
    'SA_WaitingTime':           (OKABE_ITO['vermillion'], 's', False, 'SA (Waiting Time)'),
    'SA_Energy':                (OKABE_ITO['vermillion'], 's', True, 'SA (Energy)'),
    'GA_Makespan_Dominance':    ('#56B4E9', 'D', True, 'GA Dominance (Makespan)'),
    'GA_WaitingTime_Dominance': ('#56B4E9', 'D', True, 'GA Dominance (Waiting Time)'),
    'GA_Energy_Dominance':      ('#1B3A6B', 'D', True, 'GA Dominance (Energy)'),
    'SA_Makespan_Dominance':    ('#FF6B9D', 's', True, 'SA Dominance (Makespan)'),
    'SA_WaitingTime_Dominance': ('#FF6B9D', 's', True, 'SA Dominance (Waiting Time)'),
    'SA_Energy_Dominance':      ('#8B0000', 's', True, 'SA Dominance (Energy)'),
    'NSGA-II':                  (OKABE_ITO['bluish_green'], 'o', True, 'NSGA-II'),
    'SPEA-II':                  (OKABE_ITO['reddish_purple'], '^', True, 'SPEA-II'),
    'AMOSA':                    (OKABE_ITO['orange'], 'v', True, 'AMOSA'),
}

FALLBACK_COLORS = [
    '#0072B2', '#E69F00', '#009E73', '#CC79A7', '#56B4E9', '#D55E00',
    '#8C564B', '#7F7F7F', '#BCBD22', '#17BECF', '#9467BD', '#2CA02C',
]

# Family-stable marker inference for labels missing from ALGORITHM_STYLE.
FAMILY_MARKERS = [
    (re.compile(r'^GA[_-]', re.I), 'D'),
    (re.compile(r'^SA[_-]', re.I), 's'),
    (re.compile(r'^NSGA', re.I), 'o'),
    (re.compile(r'^SPEA', re.I), '^'),
    (re.compile(r'^AMOSA', re.I), 'v'),
    # Heuristic baselines read as one "reference" family.
    (re.compile(r'^(LPT|WorkloadAware|EnergyAware|RoundRobin|Random|MinMin|MaxMin)', re.I), 'P'),
]
FALLBACK_MARKERS = ['o', 's', 'D', '^', 'v', 'P', 'X', '*']


def family_marker(label):
    for rx, marker in FAMILY_MARKERS:
        if rx.search(label):
            return marker
    return None


def resolve_styles(algorithms):
    """Encounter-ordered algorithm labels -> {label: {color, marker, filled,
    display}}. Deterministic, so the same run always loads the same way."""
    styles = {}
    fallback_i = 0
    for algo in algorithms:
        if algo in styles or algo == UNIVERSAL_KEY:
            continue
        if algo in ALGORITHM_STYLE:
            color, marker, filled, display = ALGORITHM_STYLE[algo]
        else:
            fam = family_marker(algo)
            color = FALLBACK_COLORS[fallback_i % len(FALLBACK_COLORS)]
            marker = fam or FALLBACK_MARKERS[fallback_i % len(FALLBACK_MARKERS)]
            filled, display = True, algo
            fallback_i += 1
        styles[algo] = {'color': color, 'marker': marker,
                        'filled': filled, 'display': display}
    return styles


# =============================================================================
# DATA LAYER
# =============================================================================

def _non_dominated(points):
    """Non-dominated subset of an (n,2) array, both objectives minimized."""
    pts = np.asarray(points, dtype=float)
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
            keep.append(i)
    return pts[keep]


class ScenarioData:
    """All artifacts of one scenario_N_* set, parsed and split."""

    def __init__(self, number, name):
        self.number = number
        self.name = name
        self.obj_names = []          # [obj_x, obj_y] as named in the CSV header
        self.points = None           # raw per-seed cloud rows (no universal rows)
        self.universal = None        # pooled universal front DataFrame
        self.fronts = {}             # label -> per-algorithm pooled front DataFrame
        self.metrics_seed = None     # per-seed metric rows (numeric Seed)
        self.metrics_mean = None     # MEAN rows, indexed by Algorithm
        self.metrics_std = None      # STDDEV rows, indexed by Algorithm
        self.universal_metrics = {}  # trailer row of performance_metrics.csv
        self.has_hv_fixed = False
        self.collab_seed = None      # per-seed collaboration rows (or None)
        self.collab_mean = None
        self.collab_std = None
        self.collab_universal = {}   # {'mean': {...}, 'std': {...}}
        self.details = None          # parsed scenario_N_solution_details.json


class ExperimentData:
    """A loaded campaign result folder."""

    def __init__(self, folder):
        self.folder = os.path.abspath(folder)
        self.name = os.path.basename(self.folder.rstrip('/'))
        self.scenarios = {}      # number -> ScenarioData
        self.algorithms = []     # encounter-ordered labels (no Universal_Pareto)
        self.plot_options = {}

    # ---- loading --------------------------------------------------------

    @classmethod
    def load(cls, folder):
        exp = cls(folder)
        nums = sorted(
            int(m.group(1))
            for f in os.listdir(exp.folder)
            if (m := re.match(r'scenario_(\d+)_pareto_graph_data\.csv$', f)))
        if not nums:
            raise ValueError(
                f'No scenario_N_pareto_graph_data.csv found in {exp.folder} — '
                'not a campaign result folder.')

        scen_names = exp._load_scenario_names()
        po = os.path.join(exp.folder, 'plot_options.json')
        if os.path.isfile(po):
            try:
                with open(po) as fh:
                    exp.plot_options = json.load(fh)
            except (OSError, json.JSONDecodeError):
                pass

        for n in nums:
            scn = ScenarioData(n, scen_names.get(n, f'Scenario {n}'))
            exp._load_points(scn)
            exp._load_fronts(scn)
            exp._load_metrics(scn)
            exp._load_collaboration(scn)
            exp._load_details(scn)
            exp.scenarios[n] = scn

        # Encounter-ordered algorithm list from metrics (falls back to points).
        seen = {}
        for scn in exp.scenarios.values():
            src = scn.metrics_seed if scn.metrics_seed is not None else scn.points
            if src is None:
                continue
            for a in src['Algorithm']:
                if a != UNIVERSAL_KEY:
                    seen.setdefault(a, None)
        exp.algorithms = list(seen)
        return exp

    def _path(self, fname):
        return os.path.join(self.folder, fname)

    def _load_scenario_names(self):
        names = {}
        path = self._path('experiment_summary.csv')
        if os.path.isfile(path):
            try:
                df = pd.read_csv(path, usecols=['Scenario', 'ScenarioName'])
                for _, row in df.drop_duplicates('Scenario').iterrows():
                    names[int(row['Scenario'])] = str(row['ScenarioName']).replace('_', ' ')
            except (ValueError, KeyError, OSError):
                pass
        return names

    def _load_points(self, scn):
        df = pd.read_csv(self._path(f'scenario_{scn.number}_pareto_graph_data.csv'),
                         dtype={'Seed': str})
        aux = {'Algorithm', 'Seed', 'IsUniversalPareto', 'PeakPowerWatts'}
        scn.obj_names = [c for c in df.columns if c not in aux][:2]
        for c in scn.obj_names:
            df[c] = pd.to_numeric(df[c], errors='coerce')
        is_univ_row = df['Algorithm'] == UNIVERSAL_KEY
        scn.universal = (df[is_univ_row]
                         .sort_values(scn.obj_names[0])
                         .reset_index(drop=True))
        scn.points = df[~is_univ_row].reset_index(drop=True)

    def _load_fronts(self, scn):
        path = self._path(f'scenario_{scn.number}_algorithm_pareto_fronts.csv')
        if os.path.isfile(path):
            df = pd.read_csv(path)
            obj = [c for c in df.columns if c != 'Algorithm'][:2]
            for label, grp in df.groupby('Algorithm', sort=False):
                scn.fronts[label] = (grp.rename(columns=dict(zip(obj, scn.obj_names)))
                                     .sort_values(scn.obj_names[0])
                                     .reset_index(drop=True))
        else:
            # Older folders: derive each algorithm's pooled front from the clouds.
            for label, grp in scn.points.groupby('Algorithm', sort=False):
                nd = _non_dominated(grp[scn.obj_names].to_numpy())
                nd = nd[np.argsort(nd[:, 0])]
                scn.fronts[label] = pd.DataFrame(nd, columns=scn.obj_names)

    def _load_metrics(self, scn):
        path = self._path(f'scenario_{scn.number}_performance_metrics.csv')
        if not os.path.isfile(path):
            return
        df = pd.read_csv(path, dtype={'Seed': str})
        scn.has_hv_fixed = 'HV_fixed' in df.columns
        num_cols = [c for c in df.columns if c not in ('Algorithm', 'Seed')]
        for c in num_cols:
            df[c] = pd.to_numeric(df[c], errors='coerce')
        is_univ = df['Algorithm'] == UNIVERSAL_KEY
        seed_mask = df['Seed'].str.fullmatch(r'\d+') & ~is_univ
        scn.metrics_seed = df[seed_mask].copy()
        scn.metrics_seed['Seed'] = scn.metrics_seed['Seed'].astype(int)
        scn.metrics_mean = df[(df['Seed'] == 'MEAN') & ~is_univ].set_index('Algorithm')
        scn.metrics_std = df[(df['Seed'] == 'STDDEV') & ~is_univ].set_index('Algorithm')
        univ = df[is_univ]
        if len(univ):
            scn.universal_metrics = univ.iloc[0].to_dict()

    def _load_collaboration(self, scn):
        path = self._path(f'scenario_{scn.number}_seed_collaboration.csv')
        if not os.path.isfile(path):
            return
        df = pd.read_csv(path, dtype={'Seed': str})
        num_cols = [c for c in df.columns if c not in ('Algorithm', 'Seed')]
        for c in num_cols:
            df[c] = pd.to_numeric(df[c], errors='coerce')
        is_univ = df['Algorithm'] == UNIVERSAL_KEY
        seed_mask = df['Seed'].str.fullmatch(r'\d+') & ~is_univ
        scn.collab_seed = df[seed_mask].copy()
        scn.collab_seed['Seed'] = scn.collab_seed['Seed'].astype(int)
        scn.collab_mean = df[(df['Seed'] == 'MEAN') & ~is_univ].set_index('Algorithm')
        scn.collab_std = df[(df['Seed'] == 'STDDEV') & ~is_univ].set_index('Algorithm')
        for kind in ('MEAN', 'STDDEV'):
            row = df[is_univ & (df['Seed'] == kind)]
            if len(row):
                scn.collab_universal[kind.lower()] = row.iloc[0].to_dict()

    def _load_details(self, scn):
        for suffix, opener in ((".json", open), (".json.gz", gzip.open)):
            path = self._path(f'scenario_{scn.number}_solution_details{suffix}')
            if os.path.isfile(path):
                try:
                    with opener(path, 'rt') as fh:
                        scn.details = json.load(fh)
                except (OSError, json.JSONDecodeError) as e:
                    print(f'warning: could not parse {path}: {e}', file=sys.stderr)
                return

    # ---- convenience ----------------------------------------------------

    @property
    def objective_names(self):
        first = next(iter(self.scenarios.values()))
        return first.obj_names

    def study_label(self):
        objs = self.objective_names
        return f'{objs[0]} vs {objs[1]}' if len(objs) == 2 else self.name

# =============================================================================
# FIGURE BUILDERS — pure functions (Figure in, no pyplot, no GUI state) so the
# same code drives the embedded canvases, the Save buttons, and --export-all.
# =============================================================================

METRIC_CHOICES = [
    # (key, needs_hv_fixed, display, ylabel, source)
    ('HV_fixed', True, 'Hypervolume (fixed reference)', 'HV_fixed', 'seed'),
    ('HV', False, 'Hypervolume (legacy, per-pair frame)', 'HV', 'seed'),
    ('GD', False, 'Generational Distance', 'GD', 'seed'),
    ('IGD', False, 'Inverted Generational Distance', 'IGD', 'seed'),
    ('Spacing', False, 'Spacing', 'Spacing', 'seed'),
    ('ParetoContribution', False, 'Pareto Contribution (pooled union count)',
     'points contributed', 'union'),
    ('CollabSharePct', False, 'Pareto Contribution (per-seed near-tie share %)',
     'share of per-seed universal front (%)', 'collab'),
]


def available_metrics(exp):
    """METRIC_CHOICES filtered to what this folder actually contains."""
    scns = list(exp.scenarios.values())
    have_fixed = any(s.has_hv_fixed for s in scns)
    have_collab = any(s.collab_seed is not None for s in scns)
    have_metrics = any(s.metrics_seed is not None for s in scns)
    out = []
    for key, needs_fixed, display, ylabel, source in METRIC_CHOICES:
        if source in ('seed', 'union') and not have_metrics:
            continue
        if needs_fixed and not have_fixed:
            continue
        if source == 'collab' and not have_collab:
            continue
        out.append((key, display, ylabel, source))
    return out


def _visible(exp, opts):
    hidden = opts.get('hidden', set())
    return [a for a in exp.algorithms if a not in hidden]


def _apply_legend(ax_or_fig, opts, handles=None, labels=None):
    if not opts.get('show_legend', True):
        return
    if handles is not None:
        ax_or_fig.legend(handles, labels, fontsize=9, framealpha=0.85)
    else:
        ax_or_fig.legend(fontsize=9, framealpha=0.85)


def scenario_bounds(scn):
    """Pooled per-scenario ideal/nadir over ALL algorithms' published points
    plus the universal front — the same frame HV_fixed normalizes by. Stable
    under visibility toggles so the picture doesn't rescale when algorithms
    are hidden. Returns {obj_name: (ideal, span)}."""
    frames = [scn.points[scn.obj_names]]
    if scn.universal is not None and len(scn.universal):
        frames.append(scn.universal[scn.obj_names])
    allpts = pd.concat(frames, ignore_index=True)
    out = {}
    for c in scn.obj_names:
        ideal = float(allpts[c].min())
        span = max(float(allpts[c].max()) - ideal, 1e-12)
        out[c] = (ideal, span)
    return out


def build_scatter_figure(exp, scn, styles, opts):
    """Scatter/front view for one scenario.

    opts keys: swap(bool), title(str|''), show_legend, show_labels,
    show_clouds, show_fronts, normalize(bool), hidden(set), marker_size(float).
    """
    fig = Figure(figsize=(9.2, 6.6), dpi=100)
    ax = fig.add_subplot(111)
    ox, oy = scn.obj_names
    if opts.get('swap'):
        ox, oy = oy, ox
    ms = float(opts.get('marker_size', 7))

    if opts.get('normalize'):
        bounds = scenario_bounds(scn)

        def val(series, col):
            ideal, span = bounds[col]
            return (series - ideal) / span
    else:
        def val(series, col):
            return series

    for algo in _visible(exp, opts):
        st = styles[algo]
        mfc = st['color'] if st['filled'] else 'none'
        if opts.get('show_clouds'):
            pts = scn.points[scn.points['Algorithm'] == algo]
            if len(pts):
                # gid drives the GUI's click-to-details lookup; the cloud is
                # plotted in file order so point index i within this algorithm
                # equals (seed, solutionIndex) row order of the CSV.
                coll = ax.scatter(val(pts[ox], ox), val(pts[oy], oy),
                                  s=(ms * 0.55) ** 2, alpha=0.22,
                                  color=st['color'], marker=st['marker'],
                                  linewidths=0, zorder=2)
                coll.set_gid(f'cloud::{algo}')
        if opts.get('show_fronts', True) and algo in scn.fronts:
            fr = scn.fronts[algo].sort_values(ox)
            line, = ax.plot(val(fr[ox], ox), val(fr[oy], oy),
                            color=st['color'], marker=st['marker'],
                            markersize=ms, markerfacecolor=mfc,
                            markeredgecolor=st['color'], linewidth=1.4, alpha=0.9,
                            label=st['display'], zorder=4)
            line.set_gid(f'front::{algo}')
            if opts.get('show_labels') and len(fr):
                mid = fr.iloc[len(fr) // 2]
                ax.annotate(st['display'],
                            (float(val(mid[ox], ox)), float(val(mid[oy], oy))),
                            textcoords='offset points', xytext=(6, 6),
                            fontsize=8, color=st['color'], fontweight='bold',
                            zorder=6)

    if scn.universal is not None and len(scn.universal):
        uni = scn.universal.sort_values(ox)
        ax.plot(val(uni[ox], ox), val(uni[oy], oy),
                color=UNIVERSAL_COLOR, linewidth=1.9,
                marker='o', markersize=max(ms * 0.5, 3.5),
                label=UNIVERSAL_LABEL, zorder=8)

    suffix = '  (normalized)' if opts.get('normalize') else ''
    ax.set_xlabel(axis_label(ox) + suffix)
    ax.set_ylabel(axis_label(oy) + suffix)
    ax.set_title(opts.get('title')
                 or f'{scn.name}: {axis_label(ox)} vs {axis_label(oy)}')
    _apply_legend(ax, opts)
    fig.subplots_adjust(left=0.09, right=0.97, top=0.93, bottom=0.09)
    return fig


# Mirrors plot_scenario_pareto.AXIS_LABEL (Energy is kWh in every CSV).
AXIS_UNITS = {
    'Makespan': 'Makespan (s)',
    'WaitingTime': 'Average Waiting Time (s)',
    'Energy': 'Energy (kWh)',
}


def axis_label(obj_name):
    return AXIS_UNITS.get(obj_name, obj_name)


def _metric_values(scn, algo, key, source):
    """(mean, std) of one metric for one algorithm in one scenario."""
    if source == 'collab':
        if scn.collab_mean is None or algo not in scn.collab_mean.index:
            return (np.nan, np.nan)
        return (scn.collab_mean.loc[algo, 'ContributionPct'],
                scn.collab_std.loc[algo, 'ContributionPct']
                if scn.collab_std is not None and algo in scn.collab_std.index
                else np.nan)
    if scn.metrics_seed is None:
        return (np.nan, np.nan)
    if source == 'union':
        # The MEAN row's ParetoContribution holds the union-over-seeds count
        # (a single number per arm, not an average) — no error bar.
        if scn.metrics_mean is not None and algo in scn.metrics_mean.index:
            return (scn.metrics_mean.loc[algo, 'ParetoContribution'], np.nan)
        return (np.nan, np.nan)
    rows = scn.metrics_seed[scn.metrics_seed['Algorithm'] == algo]
    if key not in rows.columns or not len(rows):
        return (np.nan, np.nan)
    vals = rows[key].dropna()
    if not len(vals):
        return (np.nan, np.nan)
    return (vals.mean(), vals.std(ddof=1) if len(vals) > 1 else 0.0)


def build_metric_figure(exp, key, display, ylabel, source, styles, opts):
    """One indicator, one panel per scenario, bars = algorithms (mean ± std)."""
    scns = [exp.scenarios[n] for n in sorted(exp.scenarios)]
    fig = Figure(figsize=(4.6 * max(len(scns), 1) + 1.2, 5.4), dpi=100)
    axes = fig.subplots(1, len(scns)) if len(scns) > 1 else [fig.add_subplot(111)]
    if len(scns) > 1:
        axes = list(np.atleast_1d(axes))
    algos = _visible(exp, opts)

    for ax, scn in zip(axes, scns):
        means, stds, colors, names = [], [], [], []
        for algo in algos:
            m, s = _metric_values(scn, algo, key, source)
            means.append(m)
            stds.append(s)
            colors.append(styles[algo]['color'])
            names.append(styles[algo]['display'])
        x = np.arange(len(algos))
        yerr = None if all(np.isnan(s) for s in stds) else \
            [0 if np.isnan(s) else s for s in stds]
        ax.bar(x, [0 if np.isnan(m) else m for m in means], color=colors,
               yerr=yerr, capsize=3, error_kw={'linewidth': 1})
        ax.set_xticks(x)
        ax.set_xticklabels(names, rotation=38, ha='right', fontsize=8)
        ax.set_title(scn.name, fontsize=11)
        ax.grid(axis='x', alpha=0)
        if source == 'union' and scn.universal is not None:
            ax.text(0.98, 0.96, f'universal front: {len(scn.universal)} pts',
                    transform=ax.transAxes, ha='right', va='top', fontsize=8,
                    color='#555555')
    axes[0].set_ylabel(ylabel)
    fig.suptitle(opts.get('title') or f'{display} — mean ± std over seeds'
                 if source not in ('union',)
                 else opts.get('title') or display, fontsize=13)
    fig.subplots_adjust(left=0.07, right=0.985, top=0.86, bottom=0.28,
                        wspace=0.24)
    return fig


def build_runtime_figure(exp, styles, opts):
    """Average runtime (s) per algorithm: one panel per scenario + overall."""
    scns = [exp.scenarios[n] for n in sorted(exp.scenarios)]
    panels = [(s.name, s.metrics_seed) for s in scns if s.metrics_seed is not None]
    all_rows = pd.concat([p[1] for p in panels], ignore_index=True) if panels else None
    if all_rows is not None and len(panels) > 1:
        panels = panels + [('All scenarios', all_rows)]
    n = max(len(panels), 1)
    fig = Figure(figsize=(4.6 * n + 1.2, 5.4), dpi=100)
    axes = list(np.atleast_1d(fig.subplots(1, n))) if n > 1 else [fig.add_subplot(111)]
    algos = _visible(exp, opts)

    for ax, (name, rows) in zip(axes, panels):
        means, stds, colors, names = [], [], [], []
        for algo in algos:
            vals = rows[rows['Algorithm'] == algo]['TimeMs'].dropna() / 1000.0
            means.append(vals.mean() if len(vals) else 0)
            stds.append(vals.std(ddof=1) if len(vals) > 1 else 0)
            colors.append(styles[algo]['color'])
            names.append(styles[algo]['display'])
        x = np.arange(len(algos))
        ax.bar(x, means, yerr=stds, color=colors, capsize=3,
               error_kw={'linewidth': 1})
        ax.set_xticks(x)
        ax.set_xticklabels(names, rotation=38, ha='right', fontsize=8)
        ax.set_title(name, fontsize=11)
    if panels:
        axes[0].set_ylabel('runtime (s)')
    fig.suptitle(opts.get('title') or 'Average runtime per algorithm (mean ± std over seeds)',
                 fontsize=13)
    fig.subplots_adjust(left=0.07, right=0.985, top=0.86, bottom=0.28,
                        wspace=0.24)
    return fig


# =============================================================================
# TABLE BUILDERS
# =============================================================================

def pivot_metric_table(scn, metric):
    """(DataFrame seeds×algorithms + MEAN/STDDEV rows, universal-info string)."""
    if scn.metrics_seed is None or metric not in scn.metrics_seed.columns:
        return None, ''
    piv = scn.metrics_seed.pivot_table(index='Seed', columns='Algorithm',
                                       values=metric, sort=False)
    piv = piv[[a for a in scn.metrics_seed['Algorithm'].unique() if a in piv.columns]]
    piv.index = piv.index.astype(str)
    extra = {}
    if scn.metrics_mean is not None and metric in scn.metrics_mean.columns:
        extra['MEAN'] = {a: scn.metrics_mean.loc[a, metric]
                         for a in piv.columns if a in scn.metrics_mean.index}
    if scn.metrics_std is not None and metric in scn.metrics_std.columns:
        extra['STDDEV'] = {a: scn.metrics_std.loc[a, metric]
                           for a in piv.columns if a in scn.metrics_std.index}
    for name, row in extra.items():
        piv.loc[name] = pd.Series(row)

    info = ''
    um = scn.universal_metrics
    if um:
        bits = [f"size={int(um['NonDomSolutions'])}" if pd.notna(um.get('NonDomSolutions')) else None,
                f"HV={um['HV']:.6f}" if pd.notna(um.get('HV')) else None,
                f"HV_fixed={um['HV_fixed']:.6f}" if pd.notna(um.get('HV_fixed')) else None]
        info = 'Pooled universal front: ' + ', '.join(b for b in bits if b)
    return piv, info


def collab_table(scn):
    """Seed-collaboration scoreboard as a display frame (or None)."""
    if scn.collab_seed is None:
        return None, ''
    piv = scn.collab_seed.pivot_table(index='Seed', columns='Algorithm',
                                      values='ContributionPct', sort=False)
    piv = piv[[a for a in scn.collab_seed['Algorithm'].unique() if a in piv.columns]]
    piv.index = piv.index.astype(str)
    if scn.collab_mean is not None:
        piv.loc['MEAN'] = pd.Series({a: scn.collab_mean.loc[a, 'ContributionPct']
                                     for a in piv.columns if a in scn.collab_mean.index})
    if scn.collab_std is not None:
        piv.loc['STDDEV'] = pd.Series({a: scn.collab_std.loc[a, 'ContributionPct']
                                       for a in piv.columns if a in scn.collab_std.index})
    info = ''
    cu = scn.collab_universal
    if 'mean' in cu:
        mu, sd = cu['mean'], cu.get('std', {})
        info = ('Per-seed universal front: size '
                f"{mu.get('SeedUniversalFrontSize', float('nan')):.1f}"
                f" ± {sd.get('SeedUniversalFrontSize', float('nan')):.1f}, "
                f"HV_fixed {mu.get('SeedUniversalHV_fixed', float('nan')):.4f}"
                f" ± {sd.get('SeedUniversalHV_fixed', float('nan')):.4f}"
                ' (near-tie shares can sum >100%)')
    return piv, info


# =============================================================================
# DETAILS (scenario_N_solution_details.json) — helpers
# =============================================================================

def details_solutions(scn):
    if not scn.details:
        return []
    return scn.details.get('solutions', [])


def details_summary_rows(sol, scn):
    """Aggregate figures of one solution as (name, value-string) pairs."""
    rows = []
    obj_names = (scn.details or {}).get('objectives', scn.obj_names)
    for name, val in zip(obj_names, sol.get('objectives') or []):
        rows.append((f'Objective: {axis_label(name)}', f'{val:.6f}'))
    for key, label, fmt in [
            ('makespanSeconds', 'Makespan (s)', '{:.4f}'),
            ('avgWaitSeconds', 'Avg wait time (s)', '{:.4f}'),
            ('totalEnergyKWh', 'Total energy (kWh)', '{:.6f}'),
            ('peakPowerWatts', 'Peak total power (W)', '{:.2f}'),
            ('avgQueueSizeAllVms', 'Avg queue size (all VMs)', '{:.2f}'),
            ('avgQueueSizeActiveVms', 'Avg queue size (active VMs)', '{:.2f}')]:
        if sol.get(key) is not None:
            rows.append((label, fmt.format(sol[key])))
    queues = sol.get('vmQueues')
    if queues is not None:
        active = sol.get('activeVmCount', sum(1 for q in queues if q))
        rows.append(('Active VMs', f'{active} / {len(queues)}'))
    if sol.get('roles'):
        rows.append(('Representative roles', ', '.join(sol['roles'])))
    return rows


def details_vm_table(sol, scn):
    """Per-VM queue table rows from a solution dict."""
    vms = {v['index']: v for v in (scn.details.get('vms') or [])}
    rows = []
    for idx, queue in enumerate(sol.get('vmQueues') or []):
        meta = vms.get(idx, {})
        preview = ','.join(str(t) for t in queue[:12]) + (',…' if len(queue) > 12 else '')
        rows.append((idx, meta.get('computeType', ''), meta.get('vcpus', ''),
                     meta.get('gpus', ''), meta.get('hostIndex', ''),
                     len(queue), preview))
    return rows


def details_host_table(sol, scn):
    hosts = {h['index']: h for h in (scn.details.get('hosts') or [])}
    rows = []
    for idx, kwh in enumerate(sol.get('hostEnergyKWh') or []):
        meta = hosts.get(idx, {})
        rows.append((idx, meta.get('computeType', ''), meta.get('cores', ''),
                     meta.get('gpus', ''), f'{kwh:.9f}'))
    return rows


def details_task_table(sol, scn):
    tasks = {t['index']: t for t in (scn.details.get('tasks') or [])}
    assign = sol.get('taskVmIndex') or []
    waits = sol.get('taskWaitSeconds') or []
    rows = []
    for tid, vm in enumerate(assign):
        meta = tasks.get(tid, {})
        wait = f'{waits[tid]:.3f}' if tid < len(waits) else ''
        rows.append((tid, meta.get('lengthInstructions', ''),
                     meta.get('workload', ''), vm, wait))
    return rows


def solution_label(sol, obj_names):
    """Human-readable identity of one solution for pickers/status lines."""
    objs = sol.get('objectives') or []
    parts = [f'#{sol.get("solutionIndex", "?")}']
    parts += [f'{n}={v:.6g}' for n, v in zip(obj_names, objs)]
    if sol.get('roles'):
        parts.append('[' + ', '.join(sol['roles']) + ']')
    return '  '.join(parts)


# =============================================================================
# SAVE FOR CLAUDE — one self-contained Markdown bundle for LLM analysis
# =============================================================================

def _df_to_csv_block(df, index_label=None, float_format='%.6f'):
    txt = df.to_csv(index_label=index_label, float_format=float_format)
    return '```csv\n' + txt + '```\n'


def build_claude_bundle(exp):
    L = []
    objs = exp.objective_names
    L.append(f'# Experiment bundle: {exp.name}')
    L.append('')
    L.append(f'- Generated: {datetime.now(timezone.utc).isoformat(timespec="seconds")}')
    L.append(f'- Study objectives (both minimized): {objs[0]} vs {objs[1]}')
    L.append(f'- Scenarios: ' + ', '.join(
        f'{n} = {exp.scenarios[n].name}' for n in sorted(exp.scenarios)))
    L.append(f'- Algorithms: ' + ', '.join(exp.algorithms))
    L.append('')
    L.append('Notes for analysis: HV_fixed (when present) is the only hypervolume '
             'comparable across algorithms/seeds within this run; the legacy HV '
             'column uses a per-pair normalization frame and is NOT cross-comparable. '
             'ParetoContribution per-seed rows are strict exact-match counts against '
             'the pooled universal front; the MEAN row of that column is the '
             'union-over-seeds count. The seed-collaboration table uses near-tie '
             'credit (0.3% relative on both objectives), so shares can sum to >100%.')
    L.append('')

    for n in sorted(exp.scenarios):
        scn = exp.scenarios[n]
        L.append(f'## Scenario {n}: {scn.name}')
        L.append('')
        if scn.metrics_seed is not None:
            L.append(f'### Per-seed quality indicators (scenario {n})')
            cols = [c for c in scn.metrics_seed.columns]
            L.append(_df_to_csv_block(scn.metrics_seed[cols], float_format='%.6g'))
            mixed = []
            if scn.metrics_mean is not None:
                mm = scn.metrics_mean.copy()
                mm.insert(0, 'Kind', 'MEAN')
                mixed.append(mm)
            if scn.metrics_std is not None:
                ms = scn.metrics_std.copy()
                ms.insert(0, 'Kind', 'STDDEV')
                mixed.append(ms)
            if mixed:
                L.append(f'### MEAN / STDDEV rows (scenario {n})')
                L.append(_df_to_csv_block(pd.concat(mixed).drop(columns=['Seed'],
                                                                errors='ignore'),
                                          index_label='Algorithm',
                                          float_format='%.6g'))
            if scn.universal_metrics:
                um = {k: v for k, v in scn.universal_metrics.items()
                      if k not in ('Algorithm', 'Seed') and pd.notna(v)}
                L.append('Pooled universal front metrics: '
                         + ', '.join(f'{k}={v:.6g}' if isinstance(v, float)
                                     else f'{k}={v}' for k, v in um.items()))
                L.append('')
        if scn.collab_seed is not None:
            L.append(f'### Seed-collaboration scoreboard (near-tie credit, scenario {n})')
            L.append(_df_to_csv_block(scn.collab_seed, float_format='%.6g'))
            piv, info = collab_table(scn)
            if info:
                L.append(info)
                L.append('')
        if scn.universal is not None and len(scn.universal):
            L.append(f'### Pooled universal Pareto front ({len(scn.universal)} points)')
            L.append(_df_to_csv_block(scn.universal[scn.obj_names],
                                      float_format='%.9g'))
        if scn.fronts:
            L.append(f'### Per-algorithm pooled fronts (union over seeds)')
            for label, fr in scn.fronts.items():
                L.append(f'#### {label} ({len(fr)} points)')
                L.append(_df_to_csv_block(fr[scn.obj_names], float_format='%.9g'))
        sols = details_solutions(scn)
        if sols:
            reps = [s for s in sols if s.get('roles')] or sols
            L.append(f'### Solution schedule details (scenario {n}) — '
                     f'{len(sols)} front solutions captured, aggregates below for '
                     f'the {len(reps)} representative (best-per-objective / knee) ones')
            L.append('Full per-solution task→VM maps, per-VM queues and per-host '
                     'energy are in scenario_N_solution_details.json.gz in the '
                     'result folder.')
            L.append('')
            obj_names = (scn.details or {}).get('objectives', scn.obj_names)
            rows = []
            for sol in reps:
                row = {'algorithm': sol.get('algorithm'),
                       'seed': sol.get('seed'),
                       'roles': '+'.join(sol.get('roles') or [])}
                row.update({f'obj_{k}': v for k, v in
                            zip(obj_names, sol.get('objectives') or [])})
                for k in ('makespanSeconds', 'avgWaitSeconds', 'totalEnergyKWh',
                          'peakPowerWatts', 'avgQueueSizeAllVms', 'activeVmCount'):
                    if sol.get(k) is not None:
                        row[k] = sol[k]
                rows.append(row)
            L.append(_df_to_csv_block(pd.DataFrame(rows), float_format='%.6g'))
        L.append('')
    return '\n'.join(L)


# =============================================================================
# HEADLESS BULK EXPORT (also exercises every builder without a display)
# =============================================================================

def export_all(exp, outdir, dpi=300):
    os.makedirs(outdir, exist_ok=True)
    styles = resolve_styles(exp.algorithms)
    opts = {'show_legend': True}
    written = []

    for n in sorted(exp.scenarios):
        scn = exp.scenarios[n]
        fig = build_scatter_figure(exp, scn, styles, dict(opts))
        p = os.path.join(outdir, f'scatter_scenario_{n}.png')
        fig.savefig(p, dpi=dpi)
        written.append(p)
        for metric in ('HV_fixed', 'HV', 'GD', 'IGD', 'Spacing',
                       'ParetoContribution', 'TimeMs'):
            piv, info = pivot_metric_table(scn, metric)
            if piv is None:
                continue
            p = os.path.join(outdir, f'table_scenario_{n}_{metric}.csv')
            with open(p, 'w') as fh:
                piv.to_csv(fh, index_label='Seed', float_format='%.6f')
                if info:
                    fh.write(f'\n# {info}\n')
            written.append(p)
        piv, info = collab_table(scn)
        if piv is not None:
            p = os.path.join(outdir, f'table_scenario_{n}_seed_collaboration.csv')
            with open(p, 'w') as fh:
                piv.to_csv(fh, index_label='Seed', float_format='%.6f')
                if info:
                    fh.write(f'\n# {info}\n')
            written.append(p)

    for key, display, ylabel, source in available_metrics(exp):
        fig = build_metric_figure(exp, key, display, ylabel, source, styles, dict(opts))
        p = os.path.join(outdir, f'bars_{key}.png')
        fig.savefig(p, dpi=dpi)
        written.append(p)

    fig = build_runtime_figure(exp, styles, dict(opts))
    p = os.path.join(outdir, 'bars_runtime.png')
    fig.savefig(p, dpi=dpi)
    written.append(p)

    p = os.path.join(outdir, f'claude_bundle_{exp.name}.md')
    with open(p, 'w') as fh:
        fh.write(build_claude_bundle(exp))
    written.append(p)
    return written

# =============================================================================
# GUI — Tkinter is imported only inside launch_gui() so headless use
# (--export-all) works on machines without a display or python3-tk.
# =============================================================================

PLOT_TABS = ('scatter', 'metrics', 'runtime')


class ResultsExplorerApp:
    """Main window. Composition over tk.Tk so the module imports headlessly."""

    def __init__(self, tkmod, folder=None):
        # tkmod bundles the lazily imported tkinter modules (see launch_gui).
        self.tk = tkmod['tk']
        self.ttk = tkmod['ttk']
        self.filedialog = tkmod['filedialog']
        self.colorchooser = tkmod['colorchooser']
        self.messagebox = tkmod['messagebox']
        self.FigureCanvasTkAgg = tkmod['FigureCanvasTkAgg']
        self.NavigationToolbar2Tk = tkmod['NavigationToolbar2Tk']

        self.exp = None
        self.styles = {}
        self.hidden = set()
        self.custom_titles = {}       # tab key -> custom title ('' = default)
        self.figures = {}             # tab key -> current Figure
        self.canvases = {}            # tab key -> FigureCanvasTkAgg
        self.current_table = None     # (DataFrame, info) shown in Tables tab
        self._details_sols = []       # solutions of the current details filter

        root = self.tk.Tk()
        self.root = root
        root.title('COSMOS Results Explorer')
        root.geometry('1420x880')
        self._build_ui()
        if folder:
            self.load_folder(folder)

    def run(self):
        self.root.mainloop()

    # ---- UI skeleton -----------------------------------------------------

    def _build_ui(self):
        tk, ttk = self.tk, self.ttk

        top = ttk.Frame(self.root, padding=(8, 6))
        top.pack(side='top', fill='x')
        ttk.Button(top, text='Open experiment…', command=self.open_dialog).pack(side='left')
        self.exp_label = ttk.Label(top, text='No experiment loaded', foreground='#666')
        self.exp_label.pack(side='left', padx=12)

        body = ttk.Frame(self.root)
        body.pack(fill='both', expand=True)

        side = ttk.Frame(body, padding=(8, 4), width=260)
        side.pack(side='left', fill='y')
        side.pack_propagate(False)
        self._build_sidebar(side)

        main = ttk.Frame(body)
        main.pack(side='left', fill='both', expand=True)
        self.notebook = ttk.Notebook(main)
        self.notebook.pack(fill='both', expand=True, padx=4, pady=4)
        self.tabs = {}
        for key, title in (('scatter', 'Scatter'), ('metrics', 'Metrics'),
                           ('runtime', 'Runtime'), ('tables', 'Tables'),
                           ('details', 'Details')):
            frame = ttk.Frame(self.notebook)
            self.notebook.add(frame, text=title)
            self.tabs[key] = frame
        self.notebook.bind('<<NotebookTabChanged>>', lambda e: self._on_tab_changed())

        self._build_metrics_controls()
        self._build_tables_tab()
        self._build_details_tab()

        self.status = ttk.Label(self.root, text='Open a results/<ExperimentId> folder to begin.',
                                anchor='w', padding=(8, 3))
        self.status.pack(side='bottom', fill='x')

    def _build_sidebar(self, side):
        tk, ttk = self.tk, self.ttk

        def header(text):
            ttk.Label(side, text=text.upper(), foreground='#888',
                      font=('TkDefaultFont', 8, 'bold')).pack(anchor='w', pady=(10, 2))

        header('Scenario')
        self.scenario_var = tk.IntVar(value=1)
        self.scenario_frame = ttk.Frame(side)
        self.scenario_frame.pack(anchor='w', fill='x')

        header('Algorithms — color & visibility')
        self.algo_frame = ttk.Frame(side)
        self.algo_frame.pack(anchor='w', fill='x')

        header('Display')
        self.legend_var = tk.BooleanVar(value=True)
        self.labels_var = tk.BooleanVar(value=False)
        self.clouds_var = tk.BooleanVar(value=False)
        self.swap_var = tk.BooleanVar(value=False)
        self.normalize_var = tk.BooleanVar(value=False)
        for var, text in ((self.legend_var, 'Show legend'),
                          (self.labels_var, 'Algorithm name labels'),
                          (self.clouds_var, 'Per-seed point clouds'),
                          (self.swap_var, 'Swap X / Y axes'),
                          (self.normalize_var, 'Normalize axes (pooled ideal/nadir)')):
            ttk.Checkbutton(side, text=text, variable=var,
                            command=self.refresh_plots).pack(anchor='w')

        ttk.Label(side, text='Title (empty = default):').pack(anchor='w', pady=(8, 0))
        self.title_entry = ttk.Entry(side)
        self.title_entry.pack(fill='x')
        self.title_entry.bind('<Return>', lambda e: self._apply_title())
        self.title_entry.bind('<FocusOut>', lambda e: self._apply_title())

        row = ttk.Frame(side)
        row.pack(anchor='w', pady=(8, 0))
        ttk.Label(row, text='Marker').pack(side='left')
        self.marker_var = tk.StringVar(value='7')
        tk.Spinbox(row, from_=2, to=20, width=4, textvariable=self.marker_var,
                   command=self.refresh_plots).pack(side='left', padx=(4, 10))
        ttk.Label(row, text='Save DPI').pack(side='left')
        self.dpi_var = tk.StringVar(value='300')
        tk.Spinbox(row, from_=72, to=600, increment=25, width=5,
                   textvariable=self.dpi_var).pack(side='left', padx=4)

        actions = ttk.Frame(side)
        actions.pack(side='bottom', fill='x', pady=8)
        for text, cmd in (('Save figure…', self.save_figure),
                          ('Save table…', self.save_table),
                          ('Export all…', self.export_all_dialog),
                          ('Save for Claude', self.save_for_claude)):
            ttk.Button(actions, text=text, command=cmd).pack(fill='x', pady=2)

    def _build_metrics_controls(self):
        ttk = self.ttk
        bar = ttk.Frame(self.tabs['metrics'])
        bar.pack(side='top', fill='x', padx=4, pady=4)
        ttk.Label(bar, text='Indicator:').pack(side='left')
        self.metric_var = self.tk.StringVar()
        self.metric_combo = ttk.Combobox(bar, textvariable=self.metric_var,
                                         state='readonly', width=48)
        self.metric_combo.pack(side='left', padx=6)
        self.metric_combo.bind('<<ComboboxSelected>>',
                               lambda e: self._refresh_tab('metrics'))

    def _build_tables_tab(self):
        ttk = self.ttk
        frame = self.tabs['tables']
        bar = ttk.Frame(frame)
        bar.pack(side='top', fill='x', padx=4, pady=4)
        ttk.Label(bar, text='Table:').pack(side='left')
        self.table_kind_var = self.tk.StringVar()
        self.table_kind_combo = ttk.Combobox(bar, textvariable=self.table_kind_var,
                                             state='readonly', width=44)
        self.table_kind_combo.pack(side='left', padx=6)
        self.table_kind_combo.bind('<<ComboboxSelected>>',
                                   lambda e: self._refresh_tab('tables'))

        holder = ttk.Frame(frame)
        holder.pack(fill='both', expand=True, padx=4)
        self.table_tree = ttk.Treeview(holder, show='headings')
        ysb = ttk.Scrollbar(holder, orient='vertical', command=self.table_tree.yview)
        xsb = ttk.Scrollbar(holder, orient='horizontal', command=self.table_tree.xview)
        self.table_tree.configure(yscroll=ysb.set, xscroll=xsb.set)
        self.table_tree.grid(row=0, column=0, sticky='nsew')
        ysb.grid(row=0, column=1, sticky='ns')
        xsb.grid(row=1, column=0, sticky='ew')
        holder.rowconfigure(0, weight=1)
        holder.columnconfigure(0, weight=1)
        self.table_tree.tag_configure('agg', background='#dde8f0')

        self.table_info = ttk.Label(frame, text='', foreground='#666', padding=(6, 4))
        self.table_info.pack(anchor='w')

    def _build_details_tab(self):
        ttk = self.ttk
        frame = self.tabs['details']

        self.details_msg = ttk.Label(
            frame, padding=14, foreground='#666', wraplength=900, text=(
                'No solution details in this run.\n\n'
                'scenario_N_solution_details.json.gz is written by campaigns run '
                'on a build that includes the SolutionDetailsCollector exporter '
                '(every published Pareto-front solution is captured). Re-run the '
                'campaign to get this view.'))

        self.details_body = ttk.Frame(frame)

        bar = ttk.Frame(self.details_body)
        bar.pack(side='top', fill='x', padx=4, pady=4)
        self.d_algo_var = self.tk.StringVar()
        self.d_seed_var = self.tk.StringVar()
        self.d_sol_var = self.tk.StringVar()
        ttk.Label(bar, text='Algorithm:').pack(side='left')
        self.d_algo_combo = ttk.Combobox(bar, textvariable=self.d_algo_var,
                                         state='readonly', width=26)
        self.d_algo_combo.pack(side='left', padx=(2, 8))
        ttk.Label(bar, text='Seed:').pack(side='left')
        self.d_seed_combo = ttk.Combobox(bar, textvariable=self.d_seed_var,
                                         state='readonly', width=7)
        self.d_seed_combo.pack(side='left', padx=(2, 8))
        ttk.Label(bar, text='Solution:').pack(side='left')
        self.d_sol_combo = ttk.Combobox(bar, textvariable=self.d_sol_var,
                                        state='readonly', width=52)
        self.d_sol_combo.pack(side='left', padx=2, fill='x', expand=True)
        self.d_algo_combo.bind('<<ComboboxSelected>>', lambda e: self._details_filter_changed())
        self.d_seed_combo.bind('<<ComboboxSelected>>', lambda e: self._details_filter_changed())
        self.d_sol_combo.bind('<<ComboboxSelected>>', lambda e: self._details_show_selected())

        grids = ttk.Frame(self.details_body)
        grids.pack(fill='both', expand=True, padx=4, pady=4)
        grids.columnconfigure(0, weight=1)
        grids.columnconfigure(1, weight=1)
        grids.rowconfigure(1, weight=1)
        grids.rowconfigure(3, weight=2)

        def make_tree(parent, columns, widths):
            holder = ttk.Frame(parent)
            tree = ttk.Treeview(holder, show='headings', columns=columns)
            for col, w in zip(columns, widths):
                tree.heading(col, text=col)
                tree.column(col, width=w, anchor='e' if col not in
                            ('Queue (dispatch order)', 'Type', 'Workload') else 'w')
            ysb = ttk.Scrollbar(holder, orient='vertical', command=tree.yview)
            tree.configure(yscroll=ysb.set)
            tree.grid(row=0, column=0, sticky='nsew')
            ysb.grid(row=0, column=1, sticky='ns')
            holder.rowconfigure(0, weight=1)
            holder.columnconfigure(0, weight=1)
            return holder, tree

        ttk.Label(grids, text='Solution summary').grid(row=0, column=0, sticky='w')
        holder, self.d_summary_tree = make_tree(
            grids, ('Metric', 'Value'), (220, 160))
        holder.grid(row=1, column=0, sticky='nsew', padx=(0, 6))
        self.d_summary_tree.column('Metric', anchor='w')

        ttk.Label(grids, text='Per-host energy').grid(row=0, column=1, sticky='w')
        holder, self.d_host_tree = make_tree(
            grids, ('Host', 'Type', 'Cores', 'GPUs', 'Energy (kWh)'),
            (50, 130, 55, 50, 120))
        holder.grid(row=1, column=1, sticky='nsew')
        self.d_host_tree.column('Type', anchor='w')

        ttk.Label(grids, text='Per-VM queues').grid(row=2, column=0, sticky='w', pady=(8, 0))
        holder, self.d_vm_tree = make_tree(
            grids, ('VM', 'Type', 'vCPUs', 'GPUs', 'Host', 'Queue size',
                    'Queue (dispatch order)'),
            (44, 70, 55, 48, 48, 80, 420))
        holder.grid(row=3, column=0, sticky='nsew', padx=(0, 6))

        ttk.Label(grids, text='Task assignments').grid(row=2, column=1, sticky='w', pady=(8, 0))
        holder, self.d_task_tree = make_tree(
            grids, ('Task', 'Length (instr.)', 'Workload', '→ VM', 'Wait (s)'),
            (55, 110, 130, 55, 80))
        holder.grid(row=3, column=1, sticky='nsew')

        self.details_msg.pack(fill='both', expand=True)

    # ---- loading -----------------------------------------------------------

    def open_dialog(self):
        folder = self.filedialog.askdirectory(
            title='Select an experiment results folder',
            initialdir=self._initial_dir())
        if folder:
            self.load_folder(folder)

    def _initial_dir(self):
        here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        for cand in (os.path.join(here, 'results'),
                     os.path.join(here, 'newExperimentResults'), here):
            if os.path.isdir(cand):
                return cand
        return os.getcwd()

    def load_folder(self, folder):
        try:
            exp = ExperimentData.load(folder)
        except Exception as e:
            self.messagebox.showerror('Could not load experiment', str(e))
            return
        self.exp = exp
        self.styles = resolve_styles(exp.algorithms)
        self.hidden = set()
        self.custom_titles = {}
        objs = exp.objective_names
        n_scen = len(exp.scenarios)
        n_seeds = 0
        first = next(iter(exp.scenarios.values()))
        if first.metrics_seed is not None:
            n_seeds = first.metrics_seed['Seed'].nunique()
        self.exp_label.config(text=(
            f'{exp.name}   ·   {objs[0]} vs {objs[1]} · {n_scen} scenarios · '
            f'{len(exp.algorithms)} algorithms · {n_seeds} seeds'))
        self._rebuild_scenario_controls()
        self._rebuild_algo_controls()
        self._rebuild_metric_choices()
        self._rebuild_table_choices()
        self.refresh_all()
        self.set_status(f'Loaded {exp.folder}')

    def _rebuild_scenario_controls(self):
        for w in self.scenario_frame.winfo_children():
            w.destroy()
        nums = sorted(self.exp.scenarios)
        self.scenario_var.set(nums[0])
        for n in nums:
            self.ttk.Radiobutton(
                self.scenario_frame, text=f'{n} — {self.exp.scenarios[n].name}',
                value=n, variable=self.scenario_var,
                command=self._scenario_changed).pack(anchor='w')

    def _scenario_changed(self):
        self._refresh_tab('scatter')
        self._refresh_tab('tables')
        self._refresh_details_tab()

    def _rebuild_algo_controls(self):
        tk = self.tk
        for w in self.algo_frame.winfo_children():
            w.destroy()
        self._algo_vars = {}
        for algo in self.exp.algorithms:
            st = self.styles[algo]
            row = self.ttk.Frame(self.algo_frame)
            row.pack(anchor='w', fill='x')
            var = tk.BooleanVar(value=True)
            self._algo_vars[algo] = var
            self.ttk.Checkbutton(row, variable=var,
                                 command=self._visibility_changed).pack(side='left')
            btn = tk.Button(row, width=2, bg=st['color'],
                            activebackground=st['color'], relief='raised',
                            command=lambda a=algo: self.pick_color(a))
            btn.pack(side='left', padx=(0, 6))
            st['_swatch'] = btn
            self.ttk.Label(row, text=st['display']).pack(side='left')

    def _visibility_changed(self):
        self.hidden = {a for a, v in self._algo_vars.items() if not v.get()}
        self.refresh_plots()

    def pick_color(self, algo):
        st = self.styles[algo]
        rgb, hexcolor = self.colorchooser.askcolor(
            color=st['color'], title=f'Color for {st["display"]}')
        if hexcolor:
            st['color'] = hexcolor
            st['_swatch'].config(bg=hexcolor, activebackground=hexcolor)
            self.refresh_plots()

    def _rebuild_metric_choices(self):
        self._metric_defs = {display: (key, ylabel, source)
                             for key, display, ylabel, source in available_metrics(self.exp)}
        values = list(self._metric_defs)
        self.metric_combo['values'] = values
        if values:
            self.metric_var.set(values[0])

    def _rebuild_table_choices(self):
        scn = next(iter(self.exp.scenarios.values()))
        kinds = []
        if scn.metrics_seed is not None:
            metric_cols = [c for c in scn.metrics_seed.columns
                           if c not in ('Algorithm', 'Seed')]
            kinds += [f'Per-seed metrics — {c}' for c in metric_cols]
        if any(s.collab_seed is not None for s in self.exp.scenarios.values()):
            kinds.append('Seed-collaboration share % (near-tie)')
        self.table_kind_combo['values'] = kinds
        if kinds:
            default = next((k for k in kinds if 'HV_fixed' in k), kinds[0])
            self.table_kind_var.set(default)

    # ---- refresh -----------------------------------------------------------

    def set_status(self, text):
        self.status.config(text=text)

    def _current_tab(self):
        idx = self.notebook.index(self.notebook.select())
        return list(self.tabs)[idx]

    def _on_tab_changed(self):
        tab = self._current_tab()
        self.title_entry.delete(0, 'end')
        self.title_entry.insert(0, self.custom_titles.get(tab, ''))

    def _apply_title(self):
        tab = self._current_tab()
        if tab in PLOT_TABS:
            self.custom_titles[tab] = self.title_entry.get().strip()
            self._refresh_tab(tab)

    def _opts(self, tab):
        try:
            ms = float(self.marker_var.get())
        except ValueError:
            ms = 7.0
        return {
            'swap': self.swap_var.get(),
            'title': self.custom_titles.get(tab, ''),
            'show_legend': self.legend_var.get(),
            'show_labels': self.labels_var.get(),
            'show_clouds': self.clouds_var.get(),
            'normalize': self.normalize_var.get(),
            'hidden': self.hidden,
            'marker_size': ms,
        }

    def refresh_all(self):
        self.refresh_plots()
        self._refresh_tab('tables')
        self._refresh_details_tab()

    def refresh_plots(self):
        for tab in PLOT_TABS:
            self._refresh_tab(tab)

    def _refresh_tab(self, tab):
        if self.exp is None:
            return
        if tab == 'scatter':
            scn = self.exp.scenarios[self.scenario_var.get()]
            fig = build_scatter_figure(self.exp, scn, self.styles, self._opts(tab))
            self._mount_figure(tab, fig, pickable=True)
        elif tab == 'metrics':
            display = self.metric_var.get()
            if display in self._metric_defs:
                key, ylabel, source = self._metric_defs[display]
                fig = build_metric_figure(self.exp, key, display, ylabel, source,
                                          self.styles, self._opts(tab))
                self._mount_figure(tab, fig)
        elif tab == 'runtime':
            fig = build_runtime_figure(self.exp, self.styles, self._opts(tab))
            self._mount_figure(tab, fig)
        elif tab == 'tables':
            self._refresh_table()

    def _mount_figure(self, tab, fig, pickable=False):
        frame = self.tabs[tab]
        old = self.canvases.get(tab)
        if old is not None:
            old.get_tk_widget().master.destroy()
        holder = self.ttk.Frame(frame)
        holder.pack(fill='both', expand=True)
        canvas = self.FigureCanvasTkAgg(fig, master=holder)
        canvas.draw()
        toolbar = self.NavigationToolbar2Tk(canvas, holder, pack_toolbar=False)
        toolbar.update()
        toolbar.pack(side='bottom', fill='x')
        canvas.get_tk_widget().pack(fill='both', expand=True)
        canvas.mpl_connect('scroll_event', self._on_scroll)
        if pickable:
            for artist in fig.axes[0].get_children():
                if artist.get_gid():
                    artist.set_picker(5)
            canvas.mpl_connect('pick_event', self._on_pick)
        self.figures[tab] = fig
        self.canvases[tab] = canvas

    def _on_scroll(self, event):
        ax = event.inaxes
        if ax is None:
            return
        factor = 1 / 1.25 if event.button == 'up' else 1.25
        for lim, get, set_, coord in (
                ('x', ax.get_xlim, ax.set_xlim, event.xdata),
                ('y', ax.get_ylim, ax.set_ylim, event.ydata)):
            lo, hi = get()
            if coord is None:
                coord = (lo + hi) / 2
            set_(coord - (coord - lo) * factor, coord + (hi - coord) * factor)
        event.canvas.draw_idle()

    # ---- scatter pick -> details -------------------------------------------

    def _on_pick(self, event):
        gid = event.artist.get_gid()
        if not gid or self.exp is None or not len(event.ind):
            return
        scn = self.exp.scenarios[self.scenario_var.get()]
        if not scn.details:
            self.set_status('No solution details in this run — Details tab unavailable.')
            return
        kind, algo = gid.split('::', 1)
        idx = event.ind[0]
        if kind == 'cloud':
            pts = scn.points[scn.points['Algorithm'] == algo].reset_index()
            if idx >= len(pts):
                return
            row = pts.iloc[idx]
            seed = int(row['Seed'])
            sol_idx = int(row['index'] -
                          pts[pts['Seed'] == row['Seed']]['index'].min())
            self._select_details(algo, seed, sol_idx)
        elif kind == 'front':
            fr = scn.fronts.get(algo)
            if fr is None:
                return
            # Must mirror the builder's plot order (sorted by the CURRENT x axis).
            ox = scn.obj_names[1] if self.swap_var.get() else scn.obj_names[0]
            fr = fr.sort_values(ox).reset_index(drop=True)
            if idx >= len(fr):
                return
            target = fr.iloc[idx][scn.obj_names].to_numpy(dtype=float)
            self._select_details_by_objectives(algo, target, scn)

    def _select_details_by_objectives(self, algo, target, scn):
        best, best_err = None, None
        for sol in details_solutions(scn):
            if sol.get('algorithm') != algo:
                continue
            objs = np.asarray(sol.get('objectives', []), dtype=float)
            if len(objs) != len(target):
                continue
            scale = np.maximum(np.abs(target), 1e-12)
            err = float(np.max(np.abs(objs - target) / scale))
            if best_err is None or err < best_err:
                best, best_err = sol, err
        if best is None or best_err > 1e-4:
            self.set_status(f'No matching captured solution for this {algo} point.')
            return
        self._select_details(algo, best['seed'], best['solutionIndex'])

    def _select_details(self, algo, seed, sol_idx):
        self.notebook.select(self.tabs['details'])
        self.d_algo_var.set(algo)
        self._details_fill_seeds()
        self.d_seed_var.set(str(seed))
        self._details_fill_solutions()
        for label in self.d_sol_combo['values']:
            if label.startswith(f'#{sol_idx} ') or label.startswith(f'#{sol_idx} '):
                self.d_sol_var.set(label)
                break
        else:
            values = self.d_sol_combo['values']
            if sol_idx < len(values):
                self.d_sol_var.set(values[sol_idx])
        self._details_show_selected()

    # ---- details tab ---------------------------------------------------------

    def _refresh_details_tab(self):
        scn = self.exp.scenarios[self.scenario_var.get()] if self.exp else None
        if scn is None or not scn.details:
            self.details_body.pack_forget()
            self.details_msg.pack(fill='both', expand=True)
            return
        self.details_msg.pack_forget()
        self.details_body.pack(fill='both', expand=True)
        algos = sorted({s.get('algorithm') for s in details_solutions(scn)})
        self.d_algo_combo['values'] = algos
        if self.d_algo_var.get() not in algos and algos:
            self.d_algo_var.set(algos[0])
        self._details_fill_seeds()
        self._details_fill_solutions()
        self._details_show_selected()

    def _details_filter_changed(self):
        self._details_fill_seeds()
        self._details_fill_solutions()
        self._details_show_selected()

    def _details_fill_seeds(self):
        scn = self.exp.scenarios[self.scenario_var.get()]
        seeds = sorted({s.get('seed') for s in details_solutions(scn)
                        if s.get('algorithm') == self.d_algo_var.get()})
        self.d_seed_combo['values'] = [str(s) for s in seeds]
        if self.d_seed_var.get() not in self.d_seed_combo['values'] and seeds:
            self.d_seed_var.set(str(seeds[0]))

    def _details_fill_solutions(self):
        scn = self.exp.scenarios[self.scenario_var.get()]
        obj_names = (scn.details or {}).get('objectives', scn.obj_names)
        try:
            seed = int(self.d_seed_var.get())
        except ValueError:
            seed = None
        self._details_sols = [
            s for s in details_solutions(scn)
            if s.get('algorithm') == self.d_algo_var.get() and s.get('seed') == seed]
        self._details_sols.sort(key=lambda s: s.get('solutionIndex', 0))
        labels = [solution_label(s, obj_names) for s in self._details_sols]
        self.d_sol_combo['values'] = labels
        if labels and self.d_sol_var.get() not in labels:
            self.d_sol_var.set(labels[0])

    def _details_show_selected(self):
        if not self._details_sols:
            for tree in (self.d_summary_tree, self.d_vm_tree,
                         self.d_host_tree, self.d_task_tree):
                tree.delete(*tree.get_children())
            return
        try:
            idx = list(self.d_sol_combo['values']).index(self.d_sol_var.get())
        except ValueError:
            idx = 0
        sol = self._details_sols[idx]
        scn = self.exp.scenarios[self.scenario_var.get()]

        for tree, rows in (
                (self.d_summary_tree, details_summary_rows(sol, scn)),
                (self.d_vm_tree, details_vm_table(sol, scn)),
                (self.d_host_tree, details_host_table(sol, scn)),
                (self.d_task_tree, details_task_table(sol, scn))):
            tree.delete(*tree.get_children())
            for row in rows:
                tree.insert('', 'end', values=row)
        total = sum(sol.get('hostEnergyKWh') or [])
        self.d_host_tree.insert('', 'end',
                                values=('TOTAL', '', '', '', f'{total:.9f}'))
        self.set_status(
            f'Details: {sol.get("algorithm")} seed {sol.get("seed")} '
            f'solution #{sol.get("solutionIndex")}')

    # ---- tables tab ------------------------------------------------------------

    def _refresh_table(self):
        scn = self.exp.scenarios[self.scenario_var.get()]
        kind = self.table_kind_var.get()
        if kind.startswith('Per-seed metrics — '):
            metric = kind.split('— ', 1)[1]
            df, info = pivot_metric_table(scn, metric)
        elif kind.startswith('Seed-collaboration'):
            df, info = collab_table(scn)
        else:
            df, info = None, ''
        self.current_table = (df, info)
        tree = self.table_tree
        tree.delete(*tree.get_children())
        if df is None:
            self.table_info.config(text='Not available in this run.')
            return
        cols = ['Seed'] + [self.styles.get(c, {'display': c})['display']
                           for c in df.columns]
        tree['columns'] = cols
        for col in cols:
            tree.heading(col, text=col)
            tree.column(col, width=max(90, 9 * len(col)), anchor='e')
        tree.column('Seed', width=70, anchor='w')
        for seed, row in df.iterrows():
            tags = ('agg',) if seed in ('MEAN', 'STDDEV') else ()
            vals = [seed] + ['' if pd.isna(v) else f'{v:.6f}' for v in row]
            tree.insert('', 'end', values=vals, tags=tags)
        self.table_info.config(text=info or '')

    # ---- saving -----------------------------------------------------------------

    def save_figure(self):
        tab = self._current_tab()
        if tab not in PLOT_TABS or tab not in self.figures:
            self.messagebox.showinfo(
                'Save figure', 'Switch to a plot tab (Scatter / Metrics / Runtime) first.')
            return
        path = self.filedialog.asksaveasfilename(
            defaultextension='.png',
            initialfile=f'{tab}_{self.exp.name}.png',
            filetypes=[('PNG', '*.png'), ('SVG', '*.svg'), ('PDF', '*.pdf')])
        if not path:
            return
        try:
            dpi = float(self.dpi_var.get())
        except ValueError:
            dpi = 300
        self.figures[tab].savefig(path, dpi=dpi, bbox_inches='tight')
        self.set_status(f'Saved {path}')

    def save_table(self):
        tab = self._current_tab()
        if tab == 'details' and self._details_sols:
            self._save_details_tables()
            return
        if self.current_table is None or self.current_table[0] is None:
            self.messagebox.showinfo('Save table', 'Open the Tables tab first.')
            return
        df, info = self.current_table
        path = self.filedialog.asksaveasfilename(
            defaultextension='.csv', initialfile=f'table_{self.exp.name}.csv',
            filetypes=[('CSV', '*.csv')])
        if not path:
            return
        with open(path, 'w') as fh:
            df.to_csv(fh, index_label='Seed', float_format='%.6f')
            if info:
                fh.write(f'\n# {info}\n')
        self.set_status(f'Saved {path}')

    def _save_details_tables(self):
        outdir = self.filedialog.askdirectory(
            title='Directory for the four detail CSVs of the selected solution')
        if not outdir:
            return
        try:
            idx = list(self.d_sol_combo['values']).index(self.d_sol_var.get())
        except ValueError:
            idx = 0
        sol = self._details_sols[idx]
        scn = self.exp.scenarios[self.scenario_var.get()]
        stem = (f'{sol.get("algorithm")}_seed{sol.get("seed")}'
                f'_sol{sol.get("solutionIndex")}')
        pd.DataFrame(details_summary_rows(sol, scn),
                     columns=['Metric', 'Value']).to_csv(
            os.path.join(outdir, f'{stem}_summary.csv'), index=False)
        pd.DataFrame(details_vm_table(sol, scn),
                     columns=['VM', 'Type', 'vCPUs', 'GPUs', 'Host',
                              'QueueSize', 'QueueOrder']).to_csv(
            os.path.join(outdir, f'{stem}_vm_queues.csv'), index=False)
        pd.DataFrame(details_host_table(sol, scn),
                     columns=['Host', 'Type', 'Cores', 'GPUs',
                              'EnergyKWh']).to_csv(
            os.path.join(outdir, f'{stem}_host_energy.csv'), index=False)
        pd.DataFrame(details_task_table(sol, scn),
                     columns=['Task', 'LengthInstructions', 'Workload', 'VM',
                              'WaitSeconds']).to_csv(
            os.path.join(outdir, f'{stem}_task_assignments.csv'), index=False)
        self.set_status(f'Saved 4 detail CSVs to {outdir}')

    def export_all_dialog(self):
        if self.exp is None:
            return
        outdir = self.filedialog.askdirectory(title='Export all figures + tables to…')
        if not outdir:
            return
        try:
            dpi = float(self.dpi_var.get())
        except ValueError:
            dpi = 300
        written = export_all(self.exp, outdir, dpi=dpi)
        self.set_status(f'Exported {len(written)} files to {outdir}')

    def save_for_claude(self):
        if self.exp is None:
            return
        path = self.filedialog.asksaveasfilename(
            defaultextension='.md',
            initialfile=f'claude_bundle_{self.exp.name}.md',
            filetypes=[('Markdown', '*.md'), ('Gzipped Markdown', '*.md.gz')])
        if not path:
            return
        text = build_claude_bundle(self.exp)
        if path.endswith('.gz'):
            with gzip.open(path, 'wt') as fh:
                fh.write(text)
        else:
            with open(path, 'w') as fh:
                fh.write(text)
        self.set_status(f'Saved Claude bundle: {path} '
                        f'({os.path.getsize(path) / 1024:.0f} KB)')


def launch_gui(folder=None):
    try:
        import tkinter as tk
        from tkinter import ttk, filedialog, colorchooser, messagebox
    except ImportError:
        print('tkinter is not available. Install python3-tk, or use '
              '--export-all <dir> for headless export.', file=sys.stderr)
        return 2
    from matplotlib.backends.backend_tkagg import (
        FigureCanvasTkAgg, NavigationToolbar2Tk)
    app = ResultsExplorerApp({
        'tk': tk, 'ttk': ttk, 'filedialog': filedialog,
        'colorchooser': colorchooser, 'messagebox': messagebox,
        'FigureCanvasTkAgg': FigureCanvasTkAgg,
        'NavigationToolbar2Tk': NavigationToolbar2Tk,
    }, folder=folder)
    app.run()
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(
        description='Interactive explorer for campaign experiment results '
                    '(manual tool; never auto-run by the pipeline).')
    parser.add_argument('folder', nargs='?',
                        help='experiment results folder (results/<ExperimentId>)')
    parser.add_argument('--export-all', metavar='DIR',
                        help='headless: export all figures/tables/bundle to DIR and exit')
    parser.add_argument('--dpi', type=float, default=300.0,
                        help='DPI for --export-all figures (default 300)')
    args = parser.parse_args(argv)

    if args.export_all:
        if not args.folder:
            parser.error('--export-all requires a results folder argument')
        exp = ExperimentData.load(args.folder)
        written = export_all(exp, args.export_all, dpi=args.dpi)
        print(f'Exported {len(written)} files to {args.export_all}')
        return 0
    return launch_gui(args.folder)


if __name__ == '__main__':
    sys.exit(main())

