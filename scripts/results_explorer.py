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


def build_scatter_figure(exp, scn, styles, opts):
    """Scatter/front view for one scenario.

    opts keys: swap(bool), title(str|''), show_legend, show_labels,
    show_clouds, show_fronts, hidden(set), marker_size(float).
    """
    fig = Figure(figsize=(9.2, 6.6), dpi=100)
    ax = fig.add_subplot(111)
    ox, oy = scn.obj_names
    if opts.get('swap'):
        ox, oy = oy, ox
    ms = float(opts.get('marker_size', 7))

    for algo in _visible(exp, opts):
        st = styles[algo]
        mfc = st['color'] if st['filled'] else 'none'
        if opts.get('show_clouds'):
            pts = scn.points[scn.points['Algorithm'] == algo]
            if len(pts):
                ax.scatter(pts[ox], pts[oy], s=(ms * 0.55) ** 2, alpha=0.22,
                           color=st['color'], marker=st['marker'],
                           linewidths=0, zorder=2)
        if opts.get('show_fronts', True) and algo in scn.fronts:
            fr = scn.fronts[algo].sort_values(ox)
            ax.plot(fr[ox], fr[oy], color=st['color'], marker=st['marker'],
                    markersize=ms, markerfacecolor=mfc,
                    markeredgecolor=st['color'], linewidth=1.4, alpha=0.9,
                    label=st['display'], zorder=4)
            if opts.get('show_labels') and len(fr):
                mid = fr.iloc[len(fr) // 2]
                ax.annotate(st['display'], (mid[ox], mid[oy]),
                            textcoords='offset points', xytext=(6, 6),
                            fontsize=8, color=st['color'], fontweight='bold',
                            zorder=6)

    if scn.universal is not None and len(scn.universal):
        uni = scn.universal.sort_values(ox)
        ax.plot(uni[ox], uni[oy], color=UNIVERSAL_COLOR, linewidth=1.9,
                marker='o', markersize=max(ms * 0.5, 3.5),
                label=UNIVERSAL_LABEL, zorder=8)

    ax.set_xlabel(axis_label(ox))
    ax.set_ylabel(axis_label(oy))
    ax.set_title(opts.get('title')
                 or f'{scn.name}: {axis_label(ox)} vs {axis_label(oy)}')
    _apply_legend(ax, opts)
    fig.subplots_adjust(left=0.09, right=0.97, top=0.93, bottom=0.09)
    return fig


AXIS_UNITS = {
    'Makespan': 'Makespan (s)',
    'WaitingTime': 'Avg. Wait Time (s)',
    'Energy': 'Energy (Wh)',
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
    for obj, val in (sol.get('objectives') or {}).items():
        rows.append((f'Objective {axis_label(obj)}', f'{val:.6f}'))
    for key, label, fmt in [
            ('makespanSeconds', 'Makespan (s)', '{:.4f}'),
            ('avgWaitSeconds', 'Avg wait time (s)', '{:.4f}'),
            ('totalEnergyWh', 'Total energy (Wh)', '{:.6f}'),
            ('peakPowerWatts', 'Peak total power (W)', '{:.2f}'),
            ('avgQueueSizeAllVms', 'Avg queue size (all VMs)', '{:.2f}'),
            ('avgQueueSizeActiveVms', 'Avg queue size (active VMs)', '{:.2f}')]:
        if sol.get(key) is not None:
            rows.append((label, fmt.format(sol[key])))
    order = sol.get('vmTaskOrder')
    if order is not None:
        rows.append(('Active VMs', f'{sum(1 for q in order if q)} / {len(order)}'))
    return rows


def details_vm_table(sol, scn):
    """Per-VM queue table rows from a solution dict."""
    vms = {v['index']: v for v in (scn.details.get('vms') or [])}
    order = sol.get('vmTaskOrder') or []
    rows = []
    for idx, queue in enumerate(order):
        meta = vms.get(idx, {})
        preview = ','.join(str(t) for t in queue[:12]) + (',…' if len(queue) > 12 else '')
        rows.append((idx, meta.get('id', ''), meta.get('computeType', ''),
                     meta.get('hostIndex', ''), len(queue), preview))
    return rows


def details_host_table(sol, scn):
    hosts = {h['index']: h for h in (scn.details.get('hosts') or [])}
    energy = sol.get('hostEnergyWh') or []
    rows = []
    for idx, wh in enumerate(energy):
        meta = hosts.get(idx, {})
        rows.append((idx, meta.get('id', ''), meta.get('computeType', ''),
                     f'{wh:.6f}'))
    return rows


def details_task_table(sol):
    assign = sol.get('taskVmIndex') or []
    waits = sol.get('taskWaitSeconds') or []
    rows = []
    for tid, vm in enumerate(assign):
        wait = f'{waits[tid]:.3f}' if tid < len(waits) else ''
        rows.append((tid, vm, wait))
    return rows


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
            L.append(f'### Representative solution details (scenario {n})')
            L.append('Aggregates per representative solution (task→VM maps omitted '
                     'for size; see scenario_N_solution_details.json in the result '
                     'folder for full assignments):')
            L.append('')
            rows = []
            for sol in sols:
                row = {'algorithm': sol.get('algorithm'),
                       'seed': sol.get('seed'), 'role': sol.get('role')}
                row.update({f'obj_{k}': v for k, v in (sol.get('objectives') or {}).items()})
                for k in ('makespanSeconds', 'avgWaitSeconds', 'totalEnergyWh',
                          'peakPowerWatts', 'avgQueueSizeAllVms'):
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

# === APPEND: GUI ===

