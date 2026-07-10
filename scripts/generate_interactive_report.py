#!/usr/bin/env python3
"""
Interactive HTML report generator for JavaCloudSimulatorCosmos experiments.

Consumes the same CSVs as plot_scenario_pareto.py (written by ExperimentReporter):
    scenario_<N>_pareto_graph_data.csv
    scenario_<N>_performance_metrics.csv
    plot_options.json
and writes a single self-contained  interactive_report.html  into the reports
directory.

Every figure is a genuine matplotlib render (publication EAF style, identical
maths/styling to plot_scenario_pareto.py, whose helpers are imported directly),
exported as SVG with per-artist `gid` tags so that plain-JS controls in the page
can, without re-rendering:
    * swap the X and Y axes (each figure is pre-rendered in both orientations),
    * change the colour of every algorithm - including the Universal Pareto set,
    * show/hide the legend, the algorithm name labels and the bar value labels,
    * show/hide individual algorithms,
    * set a custom title on every plot.

Tables mirror the scenario_<N>_performance_metrics.csv layout exactly
(per-seed rows + MEAN/STDDEV + Universal_Pareto), the same tables the other
scripts in this folder consume.

Usage:
    python generate_interactive_report.py <reports_directory> [--out FILE]
    python generate_interactive_report.py
        With no arguments (e.g. run by hand or double-clicked), file-picker
        dialogs ask for the experiment results folder and where to save the
        HTML. The Java runners (PostRunScripts) always pass the directory, so
        campaigns stay fully automatic and never prompt.
"""

import argparse
import io
import json
import os
import re
import sys
from datetime import datetime, timezone

import numpy as np
import pandas as pd

import matplotlib
matplotlib.use('Agg')
import matplotlib as mpl
import matplotlib.pyplot as plt

# Reuse the publication styling, palette, EAF/normalization math and config
# loading from the canonical plotter so both outputs can never drift apart.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import plot_scenario_pareto as psp  # noqa: E402  (needs sys.path tweak first)

# Text must stay real <text> elements (not paths) so JS can edit titles, and
# ids must be stable so regenerated reports diff cleanly.
mpl.rcParams['svg.fonttype'] = 'none'
mpl.rcParams['svg.hashsalt'] = 'cosmos-interactive-report'

HTML_NAME = 'interactive_report.html'

# Fallback colours for algorithms unknown to plot_scenario_pareto.ALGORITHM_STYLE
# (e.g. exotic labels). Power-ceiling variants (<base>_PC<tier>) instead inherit
# the base algorithm's colour, blended toward white by cap tier.
FALLBACK_COLORS = [
    '#0072B2', '#E69F00', '#009E73', '#CC79A7', '#56B4E9', '#D55E00',
    '#8C564B', '#7F7F7F', '#BCBD22', '#17BECF', '#9467BD', '#2CA02C',
]
FALLBACK_MARKERS = ['o', 's', 'D', '^', 'v', 'P', 'X', '*']

UNIVERSAL_KEY = 'Universal_Pareto'
UNIVERSAL_SLUG = 'universal-pareto'

PC_SUFFIX_RE = re.compile(r'^(?P<base>.+)_PC(?P<tier>\d+)$')


def pc_parts(key):
    """(base algorithm key, tier string or None) for a CSV algorithm name."""
    if key != UNIVERSAL_KEY:
        m = PC_SUFFIX_RE.match(key)
        if m:
            return m.group('base'), m.group('tier')
    return key, None


# =============================================================================
# STYLE RESOLUTION (per-algorithm colour/marker/label, robust to PC variants)
# =============================================================================

def slugify(name):
    s = re.sub(r'[^a-z0-9]+', '-', str(name).lower()).strip('-')
    s = re.sub(r'-{2,}', '-', s)
    return s or 'x'


def blend_toward_white(hex_color, frac):
    rgb = mpl.colors.to_rgb(hex_color)
    mixed = tuple(c + (1.0 - c) * frac for c in rgb)
    return mpl.colors.to_hex(mixed)


def resolve_styles(algorithms):
    """
    algorithms: encounter-ordered list of CSV algorithm names (may include
    Universal_Pareto). Returns an ordered dict:
        key -> {slug, color, marker, filled, label, is_universal}
    """
    pc_tiers = sorted(
        {int(m.group('tier')) for a in algorithms
         if (m := PC_SUFFIX_RE.match(a)) and a != UNIVERSAL_KEY},
        reverse=True)  # 90 (tightest blend first) ... 25
    styles = {}
    fallback_i = 0
    for algo in algorithms:
        if algo in styles:
            continue
        if algo == UNIVERSAL_KEY:
            styles[algo] = {
                'slug': UNIVERSAL_SLUG,
                'color': psp.UNIVERSAL_PARETO_COLOR,
                'marker': 'o', 'filled': True,
                'label': psp.UNIVERSAL_PARETO_LABEL,
                'is_universal': True,
            }
            continue
        m = PC_SUFFIX_RE.match(algo)
        if algo in psp.ALGORITHM_STYLE:
            color, marker, filled, label = psp.ALGORITHM_STYLE[algo]
        elif m and m.group('base') in psp.ALGORITHM_STYLE:
            base_c, marker, filled, base_l = psp.ALGORITHM_STYLE[m.group('base')]
            tier = int(m.group('tier'))
            rank = pc_tiers.index(tier) if tier in pc_tiers else 0
            denom = max(len(pc_tiers) - 1, 1)
            color = blend_toward_white(base_c, 0.20 + 0.45 * rank / denom)
            label = f'{base_l} @PC{tier}'
        else:
            color = FALLBACK_COLORS[fallback_i % len(FALLBACK_COLORS)]
            marker = FALLBACK_MARKERS[fallback_i % len(FALLBACK_MARKERS)]
            filled = True
            label = algo
            fallback_i += 1
        styles[algo] = {
            'slug': slugify(algo), 'color': mpl.colors.to_hex(color),
            'marker': marker, 'filled': filled, 'label': label,
            'is_universal': False,
        }
    # Guarantee slug uniqueness (ids are derived from slugs): allocate against
    # the set of slugs actually taken, so a rename can never collide with a
    # later algorithm's natural slug (or vice versa).
    used = set()
    for st in styles.values():
        base = st['slug']
        candidate, k = base, 1
        while candidate in used:
            candidate = f'{base}-{k}'
            k += 1
        st['slug'] = candidate
        used.add(candidate)
    return styles


# =============================================================================
# GID BOOKKEEPING
# =============================================================================

class GidFactory:
    """Unique SVG ids of the form  <fig><variant>--<role>--<slug>--<n>.

    JS selects by the delimited substring  --<role>--<slug>--  so ids stay
    unique while every element of an algorithm can be addressed together.
    """

    def __init__(self, fig_id):
        self.fig_id = fig_id
        self.counter = 0

    def __call__(self, role, slug):
        gid = f'{self.fig_id}--{role}--{slug}--{self.counter}'
        self.counter += 1
        return gid


def tag(artist, gid):
    try:
        artist.set_gid(gid)
    except AttributeError:
        pass


def tag_contour(contour_set, gids, role, slug):
    """gid-tag a contour/contourf result across matplotlib versions.

    On matplotlib >= 3.8 a ContourSet is a single Artist; before that it is a
    plain holder whose .collections are the taggable artists.
    """
    if hasattr(contour_set, 'set_gid'):
        tag(contour_set, gids(role, slug))
    else:
        for coll in getattr(contour_set, 'collections', []):
            tag(coll, gids(role, slug))


def legend_handle_artists(leg):
    """Legend handles across matplotlib versions (legend_handles is 3.7+,
    legendHandles was removed later)."""
    handles = getattr(leg, 'legend_handles', None)
    if handles is None:
        handles = getattr(leg, 'legendHandles', [])
    return handles


# =============================================================================
# FIGURE -> SVG
# =============================================================================

def fig_to_svg(fig):
    buf = io.StringIO()
    fig.savefig(buf, format='svg', metadata={'Date': None}, bbox_inches=None)
    plt.close(fig)
    svg = buf.getvalue()
    svg = svg[svg.index('<svg'):]
    # Drop the fixed pixel size so CSS can scale the figure responsively;
    # the viewBox keeps the aspect ratio.
    svg = re.sub(r'<svg([^>]*?)\swidth="[^"]*"', r'<svg\1', svg, count=1)
    svg = re.sub(r'<svg([^>]*?)\sheight="[^"]*"', r'<svg\1', svg, count=1)
    return svg


def add_bottom_legend(fig, handles, labels, gids, slugs):
    """Shared bottom legend with per-entry gids (handle + text)."""
    ncol = 2 if len(labels) <= 10 else 3
    leg = fig.legend(
        handles, labels, loc='lower center', ncol=ncol,
        bbox_to_anchor=(0.5, 0.01), fontsize=8,
        frameon=True, fancybox=False, edgecolor='#888888',
    )
    tag(leg, gids('legend', 'all'))
    for h, s in zip(legend_handle_artists(leg), slugs):
        tag(h, gids('legh', s))
    for t, s in zip(leg.get_texts(), slugs):
        tag(t, gids('legt', s))
    return leg


def legend_rows(n_entries):
    ncol = 2 if n_entries <= 10 else 3
    return int(np.ceil(n_entries / ncol))


# =============================================================================
# PARETO FIGURE (EAF publication style, mirrors psp.plot_scenario_pareto)
# =============================================================================

def render_pareto_figure(fig_id, df, scenario_num, scenario_name,
                         x_col, y_col, bounds, styles, hv_summary, config):
    """One scenario, one orientation. Pass (x_col, y_col, bounds) pre-swapped
    for the rotated variant. Returns the SVG string."""
    gids = GidFactory(fig_id)
    marker_size = config.get('marker_size', 10)

    algorithms = [a for a in df['Algorithm'].unique() if a != UNIVERSAL_KEY]
    entries = len(algorithms) + (1 if (df['Algorithm'] == UNIVERSAL_KEY).any() else 0)
    rows = legend_rows(entries)

    ax_w, ax_h = 5.6, 4.9
    left, right, top = 0.85, 0.80, 0.85
    bottom_axes = 0.62
    legend_h = 0.26 * rows + 0.30
    fig_w = left + ax_w + right
    fig_h = top + ax_h + bottom_axes + legend_h
    fig, ax = plt.subplots(figsize=(fig_w, fig_h))
    fig.subplots_adjust(left=left / fig_w, right=1 - right / fig_w,
                        top=1 - top / fig_h,
                        bottom=(bottom_axes + legend_h) / fig_h)

    title = ax.set_title(
        f'Scenario {scenario_num}: '
        f'{psp.SCENARIO_NAMES.get(scenario_num, scenario_name)}', pad=26)
    tag(title, gids('title', 'main'))
    ax.set_xlabel(f'{psp.axis_label(x_col)}  (normalized)')
    ax.set_ylabel(f'{psp.axis_label(y_col)}  (normalized)')
    ax.set_xlim(-0.03, 1.05)
    ax.set_ylim(-0.03, 1.05)
    ax.grid(True, which='major')

    grid_x = np.linspace(0.0, 1.05, 220)
    grid_y = np.linspace(0.0, 1.05, 220)

    leg_handles, leg_labels, leg_slugs = [], [], []

    for algo in algorithms:
        algo_df = df[df['Algorithm'] == algo]
        st = styles[algo]
        color, marker, filled = st['color'], st['marker'], st['filled']
        slug = st['slug']
        face = color if filled else 'none'
        label = f"{st['label']}{psp.format_hv_suffix(hv_summary.get(algo))}"
        label_anchor = None

        if algo in psp.SINGLE_OBJ_ALGORITHMS or algo in psp.BASELINE_ALGORITHMS:
            pts = psp.normalize_points(algo_df[[x_col, y_col]].values, bounds)
            if len(pts) == 0:
                continue
            mx, my = float(np.median(pts[:, 0])), float(np.median(pts[:, 1]))
            qx = np.percentile(pts[:, 0], [25, 75])
            qy = np.percentile(pts[:, 1], [25, 75])
            x_max = y_max = 1.02
            if mx > x_max or my > y_max:  # off-chart baseline (see psp)
                mx, my = min(mx, x_max), min(my, y_max)
                label += '  (off-chart)'
                xerr = yerr = None
            else:
                xerr = [[max(mx - qx[0], 0.0)], [max(qx[1] - mx, 0.0)]]
                yerr = [[max(my - qy[0], 0.0)], [max(qy[1] - my, 0.0)]]
            eb = ax.errorbar(
                mx, my, xerr=xerr, yerr=yerr,
                fmt=marker, markersize=float(np.sqrt(marker_size * 18)),
                markerfacecolor=face, markeredgecolor=color, markeredgewidth=1.2,
                ecolor=color, elinewidth=0.9, capsize=3, capthick=0.9, zorder=5,
            )
            for part in (eb.lines[0], *eb.lines[1], *eb.lines[2]):
                tag(part, gids('algo', slug))
            leg_handles.append(mpl.lines.Line2D(
                [], [], marker=marker, linestyle='none',
                markersize=7, markerfacecolor=face, markeredgecolor=color))
            label_anchor = (mx, my)
        else:
            seed_fronts = []
            pooled = []
            for _, seed_df in algo_df.groupby('Seed'):
                pts = psp.normalize_points(seed_df[[x_col, y_col]].values, bounds)
                nd = psp.get_non_dominated(pts)
                if len(nd) > 0:
                    seed_fronts.append(nd)
                    pooled.extend(nd.tolist())
            if not seed_fronts:
                continue
            freq = psp.compute_eaf_grid(seed_fronts, grid_x, grid_y)
            try:
                cf = ax.contourf(grid_x, grid_y, freq, levels=[0.25, 0.75],
                                 colors=[color], alpha=0.18, zorder=2)
                tag_contour(cf, gids, 'algo', slug)
            except ValueError:
                pass
            try:
                cl = ax.contour(grid_x, grid_y, freq, levels=[0.5],
                                colors=[color], linewidths=1.6, zorder=4)
                tag_contour(cl, gids, 'algo', slug)
            except ValueError:
                pass
            leg_handles.append(mpl.lines.Line2D([], [], color=color, linewidth=1.6))
            pooled_nd = psp.get_non_dominated(np.array(pooled))
            if len(pooled_nd) > 0:
                label_anchor = tuple(pooled_nd[len(pooled_nd) // 2])

        leg_labels.append(label)
        leg_slugs.append(slug)
        if label_anchor is not None:
            ann = ax.annotate(
                st['label'], xy=label_anchor, xytext=(5, 5),
                textcoords='offset points', fontsize=7, color=color,
                alpha=0.95, zorder=9, clip_on=True)
            tag(ann, gids('lbl', slug))

    univ_df = df[df['Algorithm'] == UNIVERSAL_KEY]
    if not univ_df.empty:
        st = styles[UNIVERSAL_KEY]
        ucolor, uslug = st['color'], st['slug']
        univ_pts = psp.normalize_points(univ_df[[x_col, y_col]].values, bounds)
        univ_sorted = univ_pts[np.argsort(univ_pts[:, 0])]
        line, = ax.plot(univ_sorted[:, 0], univ_sorted[:, 1], color=ucolor,
                        linewidth=1.8, linestyle='-', alpha=0.9, zorder=6)
        tag(line, gids('algo', uslug))
        sc = ax.scatter(univ_sorted[:, 0], univ_sorted[:, 1],
                        s=marker_size * 3, marker='o', facecolors=ucolor,
                        edgecolors=ucolor, linewidths=0.4, zorder=7)
        tag(sc, gids('algo', uslug))
        mid = univ_sorted[len(univ_sorted) // 2]
        ann = ax.annotate(st['label'], xy=tuple(mid), xytext=(6, -12),
                          textcoords='offset points', fontsize=7,
                          color=ucolor, alpha=0.95, zorder=9, clip_on=True)
        tag(ann, gids('lbl', uslug))
        leg_handles.append(mpl.lines.Line2D(
            [], [], color=ucolor, linewidth=1.8, marker='o', markersize=4,
            markerfacecolor=ucolor, markeredgecolor=ucolor))
        leg_labels.append(
            f"{st['label']}{psp.format_hv_suffix(hv_summary.get(UNIVERSAL_KEY))}")
        leg_slugs.append(uslug)

    # Ideal-point reference marker at (0, 0)
    ax.scatter([0.0], [0.0], marker='*', s=140, facecolor='white',
               edgecolor='black', linewidths=0.9, zorder=8)
    ax.annotate('ideal', xy=(0.0, 0.0), xytext=(6, 6),
                textcoords='offset points', fontsize=8, style='italic')

    # Secondary axes in raw units (same construction as psp)
    ideal_x, ideal_y, nadir_x, nadir_y = bounds
    range_x = max(nadir_x - ideal_x, 1e-12)
    range_y = max(nadir_y - ideal_y, 1e-12)
    secx = ax.secondary_xaxis(
        'top', functions=(lambda u, a=ideal_x, r=range_x: a + u * r,
                          lambda v, a=ideal_x, r=range_x: (v - a) / r))
    secx.set_xlabel(psp.axis_label(x_col), fontsize=9, labelpad=4)
    secx.tick_params(labelsize=8)
    secy = ax.secondary_yaxis(
        'right', functions=(lambda u, a=ideal_y, r=range_y: a + u * r,
                            lambda v, a=ideal_y, r=range_y: (v - a) / r))
    secy.tick_params(labelsize=8)

    add_bottom_legend(fig, leg_handles, leg_labels, gids, leg_slugs)
    return fig_to_svg(fig)


# =============================================================================
# PLAIN POINTS FIGURE (raw units: pooled Pareto sets + universal rings)
# =============================================================================

def render_points_figure(fig_id, df, front_df, scenario_num, scenario_name,
                         x_col, y_col, styles, hv_summary, config,
                         bounds=None):
    """Plain X-Y scatter: each algorithm's pooled Pareto set as coloured
    markers connected by a front line, with the Universal Pareto set overlaid
    as OPEN black rings — the coloured marker inside a ring is the algorithm
    that contributed that universal point. With `bounds` the points are
    globally normalized to ideal (0,0) / nadir (1,1) like the EAF view (raw
    units on secondary axes); without, raw units."""
    gids = GidFactory(fig_id)
    marker_size = config.get('marker_size', 10)
    normalized = bounds is not None

    def prep(pts):
        return psp.normalize_points(pts, bounds) if normalized else pts

    algorithms = [a for a in df['Algorithm'].unique() if a != UNIVERSAL_KEY]
    entries = len(algorithms) + (1 if (df['Algorithm'] == UNIVERSAL_KEY).any() else 0)
    rows = legend_rows(entries)

    ax_w, ax_h = 5.6, 4.9
    left = 0.95
    right = 0.80 if normalized else 0.35   # room for the secondary raw axes
    top = 0.85 if normalized else 0.55
    bottom_axes = 0.62
    legend_h = 0.26 * rows + 0.30
    fig_w = left + ax_w + right
    fig_h = top + ax_h + bottom_axes + legend_h
    fig, ax = plt.subplots(figsize=(fig_w, fig_h))
    fig.subplots_adjust(left=left / fig_w, right=1 - right / fig_w,
                        top=1 - top / fig_h,
                        bottom=(bottom_axes + legend_h) / fig_h)

    title = ax.set_title(
        f'Scenario {scenario_num}: '
        f'{psp.SCENARIO_NAMES.get(scenario_num, scenario_name)}',
        pad=26 if normalized else 6)
    tag(title, gids('title', 'main'))
    suffix = '  (normalized)' if normalized else ''
    ax.set_xlabel(f'{psp.axis_label(x_col)}{suffix}')
    ax.set_ylabel(f'{psp.axis_label(y_col)}{suffix}')
    ax.grid(True, which='major')
    if normalized:
        ax.set_xlim(-0.03, 1.05)
        ax.set_ylim(-0.03, 1.05)

    def pooled_raw(algo):
        """Pooled Pareto set in raw units: the reporter's per-algorithm front
        CSV when available, else the non-dominated union of the seed data."""
        if front_df is not None and x_col in front_df.columns \
                and y_col in front_df.columns:
            sub = front_df[front_df['Algorithm'] == algo]
            if not sub.empty:
                return sub[[x_col, y_col]].values.astype(float)
        pts = df[df['Algorithm'] == algo][[x_col, y_col]].values.astype(float)
        nd = psp.get_non_dominated(pts)
        return nd if len(nd) else pts

    leg_handles, leg_labels, leg_slugs = [], [], []

    for algo in algorithms:
        st = styles[algo]
        color, marker, filled = st['color'], st['marker'], st['filled']
        slug = st['slug']
        pts = pooled_raw(algo)
        if len(pts) == 0:
            continue
        pts = prep(pts)
        pts = pts[np.argsort(pts[:, 0])]
        # Front line connecting the algorithm's Pareto points, under the markers.
        if len(pts) > 1:
            ln, = ax.plot(pts[:, 0], pts[:, 1], color=color, linewidth=1.0,
                          alpha=0.65, zorder=3)
            tag(ln, gids('algo', slug))
        sc = ax.scatter(pts[:, 0], pts[:, 1], s=marker_size * 2.6,
                        marker=marker,
                        facecolors=color if filled else 'none',
                        edgecolors=color, linewidths=0.9, alpha=0.85, zorder=4)
        tag(sc, gids('algo', slug))
        leg_handles.append(mpl.lines.Line2D(
            [], [], marker=marker, linestyle='-', linewidth=1.0,
            color=color, markersize=6,
            markerfacecolor=color if filled else 'none',
            markeredgecolor=color))
        leg_labels.append(
            f"{st['label']}{psp.format_hv_suffix(hv_summary.get(algo))}")
        leg_slugs.append(slug)
        mid = pts[len(pts) // 2]
        ann = ax.annotate(st['label'], xy=tuple(mid), xytext=(5, 5),
                          textcoords='offset points', fontsize=7, color=color,
                          alpha=0.95, zorder=9, clip_on=True)
        tag(ann, gids('lbl', slug))

    univ_df = df[df['Algorithm'] == UNIVERSAL_KEY]
    if not univ_df.empty:
        st = styles[UNIVERSAL_KEY]
        ucolor, uslug = st['color'], st['slug']
        u = prep(univ_df[[x_col, y_col]].values.astype(float))
        u = u[np.argsort(u[:, 0])]
        line, = ax.plot(u[:, 0], u[:, 1], color=ucolor, linewidth=0.9,
                        alpha=0.55, zorder=6)
        tag(line, gids('algo', uslug))
        rings = ax.scatter(u[:, 0], u[:, 1], s=marker_size * 9, marker='o',
                           facecolors='none', edgecolors=ucolor,
                           linewidths=1.3, zorder=7)
        tag(rings, gids('algo', uslug))
        leg_handles.append(mpl.lines.Line2D(
            [], [], color=ucolor, linewidth=0.9, marker='o', markersize=8,
            markerfacecolor='none', markeredgecolor=ucolor))
        leg_labels.append(
            f"{st['label']}{psp.format_hv_suffix(hv_summary.get(UNIVERSAL_KEY))}")
        leg_slugs.append(uslug)
        mid = u[len(u) // 2]
        ann = ax.annotate(st['label'], xy=tuple(mid), xytext=(6, -12),
                          textcoords='offset points', fontsize=7,
                          color=ucolor, alpha=0.95, zorder=9, clip_on=True)
        tag(ann, gids('lbl', uslug))

    if normalized:
        # Secondary axes in raw units (same construction as the EAF view).
        ideal_x, ideal_y, nadir_x, nadir_y = bounds
        range_x = max(nadir_x - ideal_x, 1e-12)
        range_y = max(nadir_y - ideal_y, 1e-12)
        secx = ax.secondary_xaxis(
            'top', functions=(lambda u, a=ideal_x, r=range_x: a + u * r,
                              lambda v, a=ideal_x, r=range_x: (v - a) / r))
        secx.set_xlabel(psp.axis_label(x_col), fontsize=9, labelpad=4)
        secx.tick_params(labelsize=8)
        secy = ax.secondary_yaxis(
            'right', functions=(lambda u, a=ideal_y, r=range_y: a + u * r,
                                lambda v, a=ideal_y, r=range_y: (v - a) / r))
        secy.tick_params(labelsize=8)
    else:
        ax.margins(0.04)
    add_bottom_legend(fig, leg_handles, leg_labels, gids, leg_slugs)
    return fig_to_svg(fig)


# =============================================================================
# METRIC BAR FIGURE (mirrors psp.plot_metrics_comparison, + Universal_Pareto)
# =============================================================================

# (metric, title, axis label, integer-valued) — integer metrics use the CSV
# MEAN row's total-across-seeds semantics (see plot_scenario_pareto.py).
METRIC_SPECS = [
    ('HV', 'Hypervolume (higher is better)', 'HV (mean)', False),
    ('HV_fixed', 'Fixed-Reference Hypervolume (higher is better; the only HV formula comparable across arms)', 'HV_fixed (mean)', False),
    ('GD', 'Generational Distance (lower is better)', 'GD (mean)', False),
    ('IGD', 'Inverted Generational Distance (lower is better)', 'IGD (mean)',
     False),
    ('ParetoContribution', 'Pareto Contribution (higher is better)',
     'Pareto contribution (total across seeds)', True),
]


def metric_rows_for_scenario(metrics_df, metric):
    """(algorithm, value) pairs: per-algorithm MEAN rows plus the
    Universal_Pareto reference row (Seed == ALL)."""
    out = []
    mean_rows = metrics_df[
        (metrics_df['Algorithm'] != UNIVERSAL_KEY) &
        (metrics_df['Seed'].astype(str) == 'MEAN')]
    for _, r in mean_rows.iterrows():
        out.append((r['Algorithm'], float(r[metric])))
    univ = metrics_df[metrics_df['Algorithm'] == UNIVERSAL_KEY]
    if not univ.empty:
        out.append((UNIVERSAL_KEY, float(univ.iloc[0][metric])))
    return out


def render_metric_figure(fig_id, metric, title_txt, ylabel,
                         all_metrics, styles, config, horizontal,
                         is_int=False):
    gids = GidFactory(fig_id)
    scenarios = sorted(all_metrics.keys())
    per_scenario = {s: metric_rows_for_scenario(all_metrics[s], metric)
                    for s in scenarios}
    n_bars = sum(len(v) for v in per_scenario.values())
    if n_bars == 0:
        return None

    seen_algos = []
    for s in scenarios:
        for algo, _ in per_scenario[s]:
            if algo not in seen_algos:
                seen_algos.append(algo)
    rows = legend_rows(len(seen_algos))
    legend_h = 0.26 * rows + 0.30

    bar_width = 0.12
    span = n_bars * bar_width + (len(scenarios) + 1) * bar_width * 2
    max_name = max(len(styles[a]['label']) for a in seen_algos)
    # Bands reserved on the categorical axis for the per-bar algorithm names
    # (drawn as tick labels under/left of the graph) and the scenario names
    # (one band further out).
    names_h = min(max(0.036 * max_name + 0.25, 0.7), 2.4)  # rotated 40° names
    names_w = min(max(0.055 * max_name + 0.25, 0.9), 2.8)  # horizontal names
    scen_band = 0.35

    if horizontal:
        axes_w = 5.8
        left = names_w + scen_band + 0.15
        fig_w = left + axes_w + 0.40
        # ~0.22 in per bar along the categorical (Y) axis
        axes_h = min(max(3.4, span * 1.9), 18.0)
        fig_h = 0.55 + axes_h + 0.55 + legend_h
        fig, ax = plt.subplots(figsize=(fig_w, fig_h))
        fig.subplots_adjust(left=left / fig_w, right=1 - 0.40 / fig_w,
                            top=1 - 0.55 / fig_h,
                            bottom=(0.55 + legend_h) / fig_h)
    else:
        axes_h = 3.9
        bottom = 0.15 + names_h + scen_band + legend_h + 0.10
        # ~0.30 in per bar along the categorical (X) axis
        fig_w = min(max(7.6, span * 2.5 + 1.6), 20.0)
        fig_h = 0.55 + axes_h + bottom
        fig, ax = plt.subplots(figsize=(fig_w, fig_h))
        fig.subplots_adjust(left=0.85 / fig_w, right=1 - 0.35 / fig_w,
                            top=1 - 0.55 / fig_h, bottom=bottom / fig_h)

    title = ax.set_title(title_txt)
    tag(title, gids('title', 'main'))
    if horizontal:
        ax.set_xlabel(ylabel)
    else:
        ax.set_ylabel(ylabel)

    labelled = set()
    group_ticks, group_names, boundaries = [], [], []
    tick_pos, tick_names, tick_slugs, tick_colors = [], [], [], []
    pos, prev_end = 0.0, None
    max_val = 0.0

    leg_handles, leg_labels, leg_slugs = [], [], []

    for s in scenarios:
        rows_s = per_scenario[s]
        n = len(rows_s)
        coords = np.arange(n) * bar_width + pos
        if prev_end is not None:
            boundaries.append((prev_end + coords[0]) / 2)
        for j, (algo, val) in enumerate(rows_s):
            st = styles[algo]
            color, filled, slug = st['color'], st['filled'], st['slug']
            # facecolor white (not hatch) for unfilled styles: hatch patterns
            # are un-recolourable in SVG, plain edges keep JS recolouring exact.
            face = color if filled else 'white'
            if horizontal:
                bar = ax.barh(coords[j], val, bar_width * 0.85,
                              facecolor=face, edgecolor=color, linewidth=0.9)
            else:
                bar = ax.bar(coords[j], val, bar_width * 0.85,
                             facecolor=face, edgecolor=color, linewidth=0.9)
            tag(bar.patches[0], gids('algo', slug))
            vfmt = f'{int(round(val))}' if is_int else f'{val:.3g}'
            vtexts = ax.bar_label(bar, labels=[vfmt], padding=2,
                                  fontsize=7)
            for t in vtexts:
                tag(t, gids('val', slug))
            # Algorithm-name labels become per-bar tick labels under/left of
            # the graph (added after the loop, keeping the plot area clean).
            tick_pos.append(coords[j])
            tick_names.append(st['label'])
            tick_slugs.append(slug)
            tick_colors.append(color)
            max_val = max(max_val, val)
            if algo not in labelled:
                labelled.add(algo)
                leg_handles.append(mpl.patches.Patch(
                    facecolor=face, edgecolor=color, linewidth=0.9))
                leg_labels.append(st['label'])
                leg_slugs.append(slug)
        mid = pos + (n - 1) * bar_width / 2
        group_ticks.append(mid)
        group_names.append(
            f'S{s} ({psp.SCENARIO_NAMES.get(s, "")})')
        prev_end = coords[-1]
        pos += n * bar_width + bar_width * 2

    for xb in boundaries:
        if horizontal:
            ax.axhline(xb, color='gray', linestyle='--', linewidth=0.8, alpha=0.6)
        else:
            ax.axvline(xb, color='gray', linestyle='--', linewidth=0.8, alpha=0.6)

    if horizontal:
        ax.set_yticks(tick_pos)
        ax.set_yticklabels(tick_names, fontsize=6.5)
        for tl, slug, color in zip(ax.get_yticklabels(), tick_slugs, tick_colors):
            tl.set_color(color)
            tag(tl, gids('lbl', slug))
        ax.invert_yaxis()
        ax.set_xlim(0, max_val * 1.15 if max_val > 0 else 1)
        ax.grid(True, axis='x')
        # Scenario names: a band left of the algorithm names, rotated.
        trans = mpl.transforms.blended_transform_factory(ax.transAxes,
                                                         ax.transData)
        x_frac = -(names_w + 0.10 + scen_band * 0.5) / axes_w
        for mid, gname in zip(group_ticks, group_names):
            ax.text(x_frac, mid, gname, transform=trans, rotation=90,
                    va='center', ha='center', fontsize=10)
    else:
        ax.set_xticks(tick_pos)
        ax.set_xticklabels(tick_names, rotation=40, ha='right', fontsize=6.5)
        for tl, slug, color in zip(ax.get_xticklabels(), tick_slugs, tick_colors):
            tl.set_color(color)
            tag(tl, gids('lbl', slug))
        ax.set_ylim(0, max_val * 1.15 if max_val > 0 else 1)
        ax.grid(True, axis='y')
        # Scenario names: a band below the algorithm names.
        trans = mpl.transforms.blended_transform_factory(ax.transData,
                                                         ax.transAxes)
        y_frac = -(0.15 + names_h + scen_band * 0.5) / axes_h
        for mid, gname in zip(group_ticks, group_names):
            ax.text(mid, y_frac, gname, transform=trans,
                    va='center', ha='center', fontsize=10)

    add_bottom_legend(fig, leg_handles, leg_labels, gids, leg_slugs)
    return fig_to_svg(fig)


# =============================================================================
# TABLES (exact mirror of scenario_<N>_performance_metrics.csv values)
# =============================================================================

def esc(s):
    return (str(s).replace('&', '&amp;').replace('<', '&lt;')
            .replace('>', '&gt;').replace('"', '&quot;'))


def embed_json(obj):
    """JSON safe for inline <script> embedding: '<', '>' and '&' are encoded
    as \\uXXXX escapes so CSV-derived strings (algorithm labels, titles) can
    never terminate the script tag or inject markup."""
    return (json.dumps(obj).replace('&', '\\u0026')
            .replace('<', '\\u003c').replace('>', '\\u003e'))


def build_metric_table(metrics_str_df, columns, styles, table_id):
    """Grouped table: per algorithm a MEAN/STDDEV summary + collapsible
    per-seed rows. Values are the CSV strings verbatim."""
    df = metrics_str_df
    cols = [c for c in columns if c in df.columns]
    head = ''.join(f'<th>{esc(c)}</th>' for c in cols)
    bodies = []
    for algo in df['Algorithm'].unique():
        sub = df[df['Algorithm'] == algo]
        st = styles.get(algo)
        slug = st['slug'] if st else slugify(algo)
        label = st['label'] if st else algo
        summary_rows, seed_rows = [], []
        for _, r in sub.iterrows():
            seed = str(r['Seed'])
            cells = []
            for c in cols:
                if c == 'Algorithm':
                    cells.append('')  # filled below for the first row only
                else:
                    v = r[c]
                    cells.append('' if pd.isna(v) else str(v))
            if seed in ('MEAN', 'STDDEV', 'ALL'):
                summary_rows.append((f'summary s-{seed.lower()}', seed, cells))
            else:
                seed_rows.append(('seed', seed, cells))
        n_seeds = len(seed_rows)
        rows_html = []
        first = True
        for klass, seed, cells in summary_rows + seed_rows:
            tds = []
            for c, v in zip(cols, cells):
                if c == 'Algorithm':
                    if first:
                        btn = (f'<button class="tgl" data-alg="{slug}" '
                               f'title="show/hide per-seed rows">'
                               f'&#9656; {n_seeds} seeds</button>' if n_seeds else '')
                        tds.append(
                            f'<td class="alg"><span class="chip" '
                            f'style="background:var(--c-{slug})"></span>'
                            f'{esc(label)} {btn}</td>')
                    else:
                        tds.append('<td class="alg dim"></td>')
                else:
                    tds.append(f'<td>{esc(v)}</td>')
            rows_html.append(f'<tr class="{klass}">{"".join(tds)}</tr>')
            first = False
        bodies.append(f'<tbody class="alg-group" data-alg="{slug}">'
                      + ''.join(rows_html) + '</tbody>')
    return (f'<div class="tablewrap"><table class="metrics" id="{table_id}">'
            f'<thead><tr>{head}</tr></thead>{"".join(bodies)}</table></div>')


def build_full_table(metrics_str_df, styles, table_id):
    """Verbatim CSV mirror (all columns, original row order). Rows carry
    data-alg so the per-algorithm visibility toggle dims them too."""
    df = metrics_str_df
    head = ''.join(f'<th>{esc(c)}</th>' for c in df.columns)
    rows = []
    for _, r in df.iterrows():
        algo = str(r['Algorithm']) if 'Algorithm' in df.columns else ''
        st = styles.get(algo)
        slug = st['slug'] if st else slugify(algo)
        tds = ''.join(
            f'<td>{"" if pd.isna(v) else esc(v)}</td>' for v in r.tolist())
        rows.append(f'<tr data-alg="{slug}">{tds}</tr>')
    return (f'<div class="tablewrap"><table class="metrics raw" id="{table_id}">'
            f'<thead><tr>{head}</tr></thead><tbody>{"".join(rows)}</tbody>'
            f'</table></div>')


# =============================================================================
# PAGE ASSEMBLY
# =============================================================================

CSS = """
:root { --fg:#1a1a1a; --bg:#fafaf8; --card:#ffffff; --line:#d8d8d2;
        --accent:#0072B2; --dim:#666; }
* { box-sizing: border-box; }
body { margin:0; font-family: Georgia, 'Times New Roman', serif;
       color:var(--fg); background:var(--bg); }
header.page { padding:18px 26px 10px; border-bottom:1px solid var(--line);
              background:var(--card); }
header.page h1 { margin:0 0 4px; font-size:21px; }
header.page .sub { color:var(--dim); font-size:13px; }
.layout { display:grid; grid-template-columns: 290px 1fr; gap:0;
          align-items:start; }
@media (max-width: 950px) { .layout { grid-template-columns: 1fr; } }
aside.controls { position:sticky; top:0; max-height:100vh; overflow-y:auto;
    padding:14px 16px 30px; border-right:1px solid var(--line);
    background:var(--card); font-family: Helvetica, Arial, sans-serif;
    font-size:13px; }
aside.controls h2 { font-size:13px; text-transform:uppercase;
    letter-spacing:.06em; color:var(--dim); margin:16px 0 6px; }
aside.controls label.row { display:flex; align-items:center; gap:8px;
    padding:3px 0; cursor:pointer; }
aside.controls label.row.sub { padding-left:20px; }
.pcrow { display:flex; align-items:center; gap:8px; padding:3px 0; }
.pcrow > span { width:72px; color:var(--dim); flex-shrink:0; }
.pcrow select { flex:1; min-width:0; font-size:12px; padding:2px 4px;
    border:1px solid var(--line); border-radius:3px; background:var(--card); }
.pcrow select:disabled { opacity:.45; cursor:not-allowed; }
.algo-row { display:flex; align-items:center; gap:7px; padding:2.5px 0; }
.algo-row input[type=color] { width:26px; height:20px; padding:0;
    border:1px solid var(--line); background:none; cursor:pointer; }
.algo-row .nm { flex:1; overflow:hidden; text-overflow:ellipsis;
    white-space:nowrap; }
button.small { font-size:12px; padding:4px 10px; border:1px solid var(--line);
    background:#f0f0ea; border-radius:4px; cursor:pointer; }
button.small:hover { background:#e6e6df; }
button.small:disabled { opacity:.45; cursor:not-allowed; }
main.content { padding:20px 26px 80px; min-width:0; }
section.block { margin:0 0 34px; }
section.block > h2 { font-size:18px; border-bottom:2px solid var(--line);
    padding-bottom:5px; }
.figcard { background:var(--card); border:1px solid var(--line);
    border-radius:6px; padding:12px 14px 8px; margin:14px 0; }
.figbar { display:flex; gap:10px; align-items:center; flex-wrap:wrap;
    font-family: Helvetica, Arial, sans-serif; font-size:12.5px;
    margin-bottom:6px; }
.figbar input.title { flex:1; min-width:220px; padding:4px 8px;
    border:1px solid var(--line); border-radius:4px; font-size:12.5px; }
.figbar .axes-state { color:var(--dim); }
.svgbox svg { width:100%; height:auto; display:block; }
.caption { font-size:12px; color:var(--dim); font-style:italic;
    margin:4px 2px 2px; }
.chip { display:inline-block; width:11px; height:11px; border-radius:2px;
    margin-right:6px; border:1px solid rgba(0,0,0,.25);
    vertical-align:baseline; }
.tablewrap { overflow-x:auto; margin:10px 0; }
table.metrics { border-collapse:collapse; font-family:Helvetica,Arial,sans-serif;
    font-size:12px; min-width:480px; background:var(--card); }
table.metrics th, table.metrics td { border:1px solid var(--line);
    padding:3.5px 9px; text-align:right; white-space:nowrap; }
table.metrics th { background:#efefe9; text-align:center; }
table.metrics td.alg { text-align:left; }
table.metrics tr.summary.s-mean td { font-weight:bold; background:#f6f6f0; }
table.metrics tr.summary.s-stddev td { color:var(--dim); background:#f6f6f0; }
table.metrics tr.summary.s-all td { font-weight:bold; background:#eef3f6; }
table.metrics tr.seed { display:none; }
table.metrics tbody.expanded tr.seed { display:table-row; }
table.metrics tbody.alg-hidden, table.metrics tr.alg-hidden { opacity:.35; }
button.tgl { font-size:10.5px; margin-left:7px; padding:1px 6px;
    border:1px solid var(--line); border-radius:3px; background:#f0f0ea;
    cursor:pointer; }
details.rawcsv summary { cursor:pointer; font-family:Helvetica,Arial,sans-serif;
    font-size:13px; color:var(--dim); margin:8px 0 2px; }
h3.scen { font-size:15px; margin:20px 0 4px; }
.note { font-size:12.5px; color:var(--dim);
    font-family:Helvetica,Arial,sans-serif; }
"""

JS = """
'use strict';
const M = window.REPORT_MANIFEST;
const state = {
  legend: M.options.show_legend, labels: M.options.show_labels, values: true,
  hv: true, hvUniv: true, pcMode: 'all',
  view: Object.fromEntries(M.figs.map(f => [f.base, 'eaf'])),
  norm: Object.fromEntries(M.figs.map(f => [f.base, false])),
  algoVisible: Object.fromEntries(M.algos.map(a => [a.slug, true])),
  swapped: Object.fromEntries(M.figs.map(f => [f.base, false])),
};

// PowerCap views: presets over the per-algorithm visibility. Capped arms are
// only directly comparable within a tier, so the two filtered modes show
// either all algorithms at one tier or all tiers of one algorithm.
function applyPcMode() {
  const pcTier = document.getElementById('pc-tier');
  const pcBase = document.getElementById('pc-base');
  const enabled = M.powercap && M.powercap.enabled;
  pcTier.disabled = !(enabled && state.pcMode === 'tier');
  pcBase.disabled = !(enabled && state.pcMode === 'algo');
  if (!enabled) return;
  M.algos.forEach(a => {
    let vis = true;
    if (a.key !== 'Universal_Pareto') {
      if (state.pcMode === 'tier') {
        vis = (pcTier.value === 'uncapped') ? !a.tier : (a.tier === pcTier.value);
      } else if (state.pcMode === 'algo') {
        vis = (a.baseKey === pcBase.value);
      }
    }
    state.algoVisible[a.slug] = vis;
    const ck = document.getElementById('vis-' + a.slug);
    if (ck) ck.checked = vis;
  });
  refreshVisibility();
}

function applyHvSuffixes() {
  M.figs.filter(f => f.kind === 'pareto').forEach(f => {
    FIG_VARIANTS.forEach(v => {
      M.algos.forEach(a => {
        const want = state.hv &&
          (a.key !== 'Universal_Pareto' || state.hvUniv);
        document.querySelectorAll(
          'g[id^="' + f.base + v + '--legt--' + a.slug + '--"]'
        ).forEach(g => {
          const t = g.querySelector('text');
          if (t) t.textContent = a.label + (want ? (a.hvSuffix || '') : '');
        });
      });
    });
  });
}

// ---- downloads: PNG (current view) and XLSX (verbatim table data) ----
function sanitizeName(s) {
  return String(s).replace(/[^A-Za-z0-9._-]+/g, '_')
    .replace(/^_+|_+$/g, '') || 'export';
}
function downloadBlob(blob, name) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 10000);
}
function savePng(base) {
  const wrap = document.getElementById('wrap-' + base + '-' + variantSuffix(base));
  const svg = wrap && wrap.querySelector('svg');
  if (!svg) return;
  const vb = svg.viewBox.baseVal;
  const scale = 3;  // viewBox is in points (72/in) -> ~216 dpi
  const clone = svg.cloneNode(true);
  clone.setAttribute('width', vb.width);
  clone.setAttribute('height', vb.height);
  const url = URL.createObjectURL(new Blob(
    [new XMLSerializer().serializeToString(clone)],
    {type: 'image/svg+xml;charset=utf-8'}));
  const img = new Image();
  img.onload = () => {
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(vb.width * scale);
    canvas.height = Math.round(vb.height * scale);
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    URL.revokeObjectURL(url);
    const ttl = document.getElementById('ttl-' + base);
    const name = sanitizeName(M.expName + '_' + (ttl ? ttl.value : base))
      + (state.view[base] === 'points' ? '_points' : '')
      + (state.view[base] === 'points' && state.norm[base] ? '_normalized' : '')
      + (state.swapped[base] ? '_swapped' : '') + '.png';
    canvas.toBlob(b => { if (b) downloadBlob(b, name); }, 'image/png');
  };
  img.src = url;
}

// Minimal XLSX writer: a stored (uncompressed) ZIP of OOXML parts with
// inline-string cells; numeric-looking values are written as numbers.
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(bytes) {
  let c = 0xFFFFFFFF;
  for (let i = 0; i < bytes.length; i++) {
    c = CRC_TABLE[(c ^ bytes[i]) & 0xFF] ^ (c >>> 8);
  }
  return (c ^ 0xFFFFFFFF) >>> 0;
}
function makeZip(files) {
  const enc = new TextEncoder();
  const chunks = [], central = [];
  let offset = 0;
  const now = new Date();
  const dosTime = ((now.getHours() << 11) | (now.getMinutes() << 5) |
                   (now.getSeconds() >> 1)) & 0xFFFF;
  const dosDate = (((now.getFullYear() - 1980) << 9) |
                   ((now.getMonth() + 1) << 5) | now.getDate()) & 0xFFFF;
  const le = (n, len) => {
    const a = [];
    for (let i = 0; i < len; i++) a.push((n >>> (8 * i)) & 0xFF);
    return a;
  };
  for (const f of files) {
    const name = enc.encode(f.name);
    const data = enc.encode(f.data);
    const crc = crc32(data);
    const header = new Uint8Array([
      0x50, 0x4B, 0x03, 0x04, ...le(20, 2), ...le(0, 2), ...le(0, 2),
      ...le(dosTime, 2), ...le(dosDate, 2), ...le(crc, 4),
      ...le(data.length, 4), ...le(data.length, 4),
      ...le(name.length, 2), ...le(0, 2)]);
    chunks.push(header, name, data);
    central.push({name, crc, size: data.length, offset});
    offset += header.length + name.length + data.length;
  }
  const cdStart = offset;
  for (const e of central) {
    const rec = new Uint8Array([
      0x50, 0x4B, 0x01, 0x02, ...le(20, 2), ...le(20, 2), ...le(0, 2),
      ...le(0, 2), ...le(dosTime, 2), ...le(dosDate, 2), ...le(e.crc, 4),
      ...le(e.size, 4), ...le(e.size, 4), ...le(e.name.length, 2),
      ...le(0, 2), ...le(0, 2), ...le(0, 2), ...le(0, 2), ...le(0, 4),
      ...le(e.offset, 4)]);
    chunks.push(rec, e.name);
    offset += rec.length + e.name.length;
  }
  const eocd = new Uint8Array([
    0x50, 0x4B, 0x05, 0x06, ...le(0, 2), ...le(0, 2),
    ...le(central.length, 2), ...le(central.length, 2),
    ...le(offset - cdStart, 4), ...le(cdStart, 4), ...le(0, 2)]);
  chunks.push(eocd);
  return new Blob(chunks, {type:
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'});
}
function xmlEsc(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}
function colRef(i) {
  let s = '';
  i++;
  while (i > 0) {
    const m = (i - 1) % 26;
    s = String.fromCharCode(65 + m) + s;
    i = Math.floor((i - 1) / 26);
  }
  return s;
}
const NUM_RE = /^-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?$/;
function sheetXml(cols, rows) {
  let xml = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<worksheet xmlns="http://schemas.openxmlformats.org/' +
    'spreadsheetml/2006/main"><sheetData>';
  [cols, ...rows].forEach((row, r) => {
    xml += '<row r="' + (r + 1) + '">';
    row.forEach((v, c) => {
      const s = v == null ? '' : String(v);
      if (s === '') return;
      const ref = colRef(c) + (r + 1);
      if (r > 0 && NUM_RE.test(s)) {
        xml += '<c r="' + ref + '"><v>' + s + '</v></c>';
      } else {
        xml += '<c r="' + ref + '" t="inlineStr"><is><t>' + xmlEsc(s) +
          '</t></is></c>';
      }
    });
    xml += '</row>';
  });
  return xml + '</sheetData></worksheet>';
}
function saveXlsx(tables, filename) {
  const BAD = '[]*?:/' + String.fromCharCode(92);  // chars invalid in names
  const names = new Set();
  const clean = tables.map((t, i) => {
    let n = Array.from(String(t.sheet || ('Sheet' + (i + 1))))
      .map(ch => BAD.includes(ch) ? ' ' : ch).join('').slice(0, 31).trim()
      || ('Sheet' + (i + 1));
    const base = n;
    let k = 2;
    while (names.has(n)) { n = base.slice(0, 28) + ' ' + k++; }
    names.add(n);
    return {sheet: n, cols: t.cols, rows: t.rows};
  });
  const XMLH = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>';
  const workbook = XMLH +
    '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"' +
    ' xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">' +
    '<sheets>' + clean.map((t, i) =>
      '<sheet name="' + xmlEsc(t.sheet) + '" sheetId="' + (i + 1) +
      '" r:id="rId' + (i + 1) + '"/>').join('') + '</sheets></workbook>';
  const wbRels = XMLH +
    '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
    clean.map((t, i) => '<Relationship Id="rId' + (i + 1) + '" Type=' +
      '"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"' +
      ' Target="worksheets/sheet' + (i + 1) + '.xml"/>').join('') +
    '<Relationship Id="rId' + (clean.length + 1) + '" Type=' +
    '"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"' +
    ' Target="styles.xml"/></Relationships>';
  const rootRels = XMLH +
    '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
    '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/' +
    'officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>' +
    '</Relationships>';
  const styles = XMLH +
    '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">' +
    '<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>' +
    '<fills count="1"><fill><patternFill patternType="none"/></fill></fills>' +
    '<borders count="1"><border/></borders>' +
    '<cellStyleXfs count="1"><xf/></cellStyleXfs>' +
    '<cellXfs count="1"><xf/></cellXfs>' +
    '<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/>' +
    '</cellStyles></styleSheet>';
  const types = XMLH +
    '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' +
    '<Default Extension="rels" ContentType=' +
    '"application/vnd.openxmlformats-package.relationships+xml"/>' +
    '<Default Extension="xml" ContentType="application/xml"/>' +
    '<Override PartName="/xl/workbook.xml" ContentType=' +
    '"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>' +
    '<Override PartName="/xl/styles.xml" ContentType=' +
    '"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>' +
    clean.map((t, i) => '<Override PartName="/xl/worksheets/sheet' + (i + 1) +
      '.xml" ContentType="application/vnd.openxmlformats-officedocument.' +
      'spreadsheetml.worksheet+xml"/>').join('') + '</Types>';
  const files = [
    {name: '[Content_Types].xml', data: types},
    {name: '_rels/.rels', data: rootRels},
    {name: 'xl/workbook.xml', data: workbook},
    {name: 'xl/_rels/workbook.xml.rels', data: wbRels},
    {name: 'xl/styles.xml', data: styles},
  ];
  clean.forEach((t, i) => files.push({
    name: 'xl/worksheets/sheet' + (i + 1) + '.xml',
    data: sheetXml(t.cols, t.rows)}));
  downloadBlob(makeZip(files), filename);
}

function groupsFor(role, slug) {
  return document.querySelectorAll('g[id*="--' + role + '--' + slug + '--"]');
}
function roleGroups(role) {
  return document.querySelectorAll('g[id*="--' + role + '--"]');
}
function isPaintable(v) {
  if (!v) return false;
  v = String(v).trim().toLowerCase();
  return v !== 'none' && v !== '' && !v.startsWith('url') &&
         v !== '#ffffff' && v !== 'white' && v !== 'rgb(255, 255, 255)';
}
function setPaint(el, color) {
  if (el.style) {
    if (isPaintable(el.style.fill)) el.style.fill = color;
    if (isPaintable(el.style.stroke)) el.style.stroke = color;
  }
  if (el.getAttribute) {
    if (isPaintable(el.getAttribute('fill'))) el.setAttribute('fill', color);
    if (isPaintable(el.getAttribute('stroke'))) el.setAttribute('stroke', color);
  }
}
function recolorAlgo(slug, color) {
  ['algo', 'lbl', 'legh'].forEach(role => {
    groupsFor(role, slug).forEach(g => {
      setPaint(g, color);
      g.querySelectorAll('path, use, text, tspan, rect, circle, line')
        .forEach(el => setPaint(el, color));
    });
  });
  document.documentElement.style.setProperty('--c-' + slug, color);
}
function refreshVisibility() {
  roleGroups('legend').forEach(g => {
    g.style.display = state.legend ? '' : 'none';
  });
  M.algos.forEach(a => {
    const vis = state.algoVisible[a.slug];
    ['algo', 'legh', 'legt'].forEach(role =>
      groupsFor(role, a.slug).forEach(g => {
        g.style.display = vis ? '' : 'none';
      }));
    groupsFor('lbl', a.slug).forEach(g => {
      g.style.display = (vis && state.labels) ? '' : 'none';
    });
    groupsFor('val', a.slug).forEach(g => {
      g.style.display = (vis && state.values) ? '' : 'none';
    });
    document.querySelectorAll(
      'tbody[data-alg="' + a.slug + '"], tr[data-alg="' + a.slug + '"]'
    ).forEach(el => el.classList.toggle('alg-hidden', !vis));
  });
}
const FIG_VARIANTS = ['n', 's', 'pn', 'ps', 'qn', 'qs'];
function setTitle(base, text) {
  FIG_VARIANTS.forEach(v => {
    const g = document.querySelector(
      'g[id^="' + base + v + '--title--main--"]');
    if (!g) return;
    const t = g.querySelector('text');
    if (t) t.textContent = text;
  });
}
function variantSuffix(base) {
  const o = state.swapped[base] ? 's' : 'n';
  if (state.view[base] !== 'points') return o;
  return (state.norm[base] ? 'q' : 'p') + o;
}
function refreshFigVariant(base) {
  const cur = variantSuffix(base);
  FIG_VARIANTS.forEach(v => {
    const w = document.getElementById('wrap-' + base + '-' + v);
    if (w) w.style.display = (v === cur) ? '' : 'none';
  });
  const f = M.figs.find(x => x.base === base);
  const points = state.view[base] === 'points';
  const st = document.getElementById('axst-' + base);
  if (st) st.textContent = (state.swapped[base] ? f.axesSwapped : f.axesNormal)
    + (points && state.norm[base] ? ' — normalized 0-1' : '');
  const vb = document.getElementById('view-' + base);
  if (vb) vb.innerHTML = points ? '&#9646; EAF view' : '&#9679; Points view';
  const nb = document.getElementById('norm-' + base);
  if (nb) {
    nb.disabled = !points;
    nb.innerHTML = state.norm[base] ? '&#8645; Raw units' : '&#8645; Normalize';
  }
}
function setSwap(base, swapped) {
  state.swapped[base] = swapped;
  refreshFigVariant(base);
}
function setView(base, view) {
  state.view[base] = view;
  refreshFigVariant(base);
}
function init() {
  document.getElementById('ck-legend').addEventListener('change', e => {
    state.legend = e.target.checked; refreshVisibility();
  });
  document.getElementById('ck-labels').addEventListener('change', e => {
    state.labels = e.target.checked; refreshVisibility();
  });
  document.getElementById('ck-values').addEventListener('change', e => {
    state.values = e.target.checked; refreshVisibility();
  });
  document.getElementById('ck-hv').addEventListener('change', e => {
    state.hv = e.target.checked; applyHvSuffixes();
  });
  document.getElementById('ck-hv-univ').addEventListener('change', e => {
    state.hvUniv = e.target.checked; applyHvSuffixes();
  });
  document.getElementById('pc-mode').addEventListener('change', e => {
    state.pcMode = e.target.value; applyPcMode();
  });
  document.getElementById('pc-tier').addEventListener('change', applyPcMode);
  document.getElementById('pc-base').addEventListener('change', applyPcMode);
  M.algos.forEach(a => {
    const c = document.getElementById('col-' + a.slug);
    if (c) c.addEventListener('input', e => recolorAlgo(a.slug, e.target.value));
    const v = document.getElementById('vis-' + a.slug);
    if (v) v.addEventListener('change', e => {
      state.algoVisible[a.slug] = e.target.checked; refreshVisibility();
    });
  });
  document.getElementById('btn-reset').addEventListener('click', () => {
    M.algos.forEach(a => {
      const c = document.getElementById('col-' + a.slug);
      if (c) { c.value = a.color; recolorAlgo(a.slug, a.color); }
      const v = document.getElementById('vis-' + a.slug);
      if (v) v.checked = true;
      state.algoVisible[a.slug] = true;
    });
    ['ck-legend', 'ck-labels', 'ck-values', 'ck-hv', 'ck-hv-univ']
      .forEach((id, i) => {
        const el = document.getElementById(id);
        const def = [M.options.show_legend, M.options.show_labels,
                     true, true, true][i];
        el.checked = def;
      });
    state.legend = M.options.show_legend;
    state.labels = M.options.show_labels;
    state.values = true;
    state.hv = true;
    state.hvUniv = true;
    applyHvSuffixes();
    state.pcMode = 'all';
    document.getElementById('pc-mode').value = 'all';
    document.getElementById('pc-tier').selectedIndex = 0;
    document.getElementById('pc-base').selectedIndex = 0;
    applyPcMode();
    M.figs.forEach(f => {
      state.view[f.base] = 'eaf';
      state.norm[f.base] = false;
      setSwap(f.base, false);
      const inp = document.getElementById('ttl-' + f.base);
      if (inp) { inp.value = f.title; setTitle(f.base, f.title); }
    });
    refreshVisibility();
  });
  M.figs.forEach(f => {
    const inp = document.getElementById('ttl-' + f.base);
    if (inp) inp.addEventListener('input', e => setTitle(f.base, e.target.value));
    const btn = document.getElementById('swap-' + f.base);
    if (btn) btn.addEventListener('click', () =>
      setSwap(f.base, !state.swapped[f.base]));
    const pngBtn = document.getElementById('png-' + f.base);
    if (pngBtn) pngBtn.addEventListener('click', () => savePng(f.base));
    const viewBtn = document.getElementById('view-' + f.base);
    if (viewBtn) viewBtn.addEventListener('click', () =>
      setView(f.base, state.view[f.base] === 'points' ? 'eaf' : 'points'));
    const normBtn = document.getElementById('norm-' + f.base);
    if (normBtn) normBtn.addEventListener('click', () => {
      state.norm[f.base] = !state.norm[f.base];
      refreshFigVariant(f.base);
    });
  });
  document.querySelectorAll('button.dl-xlsx').forEach(b =>
    b.addEventListener('click', e => {
      e.preventDefault();
      e.stopPropagation();
      const t = (M.tables || []).find(x => x.id === b.dataset.table);
      if (t) saveXlsx([t], sanitizeName(M.expName + '_' + t.sheet) + '.xlsx');
    }));
  const allBtn = document.getElementById('btn-xlsx-all');
  if (allBtn) allBtn.addEventListener('click', () =>
    saveXlsx(M.tables || [], sanitizeName(M.expName + '_tables') + '.xlsx'));
  document.querySelectorAll('button.tgl').forEach(b =>
    b.addEventListener('click', () => {
      const tb = b.closest('tbody');
      tb.classList.toggle('expanded');
      b.innerHTML = (tb.classList.contains('expanded') ? '&#9662; ' : '&#9656; ')
        + b.textContent.replace(/^[^\\d]*/, '');
    }));
  refreshVisibility();
}
document.addEventListener('DOMContentLoaded', init);
"""


def fig_card(base, title, axes_normal, axes_swapped, svg_n, svg_s,
             svg_pn=None, svg_ps=None, svg_qn=None, svg_qs=None, caption=''):
    cap = f'<p class="caption">{esc(caption)}</p>' if caption else ''
    view_btn = ''
    extra = ''
    if svg_pn is not None:
        view_btn = (f'<button class="small" id="view-{base}" title="Switch '
                    f'between the EAF publication view and a plain scatter '
                    f'of the Pareto sets">&#9679; Points view</button>'
                    f'<button class="small" id="norm-{base}" disabled '
                    f'title="Points view only: switch between raw units and '
                    f'globally normalized ideal/nadir axes">'
                    f'&#8645; Normalize</button>')
        extra = (f'<div class="svgbox" id="wrap-{base}-pn" '
                 f'style="display:none">{svg_pn}</div>'
                 f'<div class="svgbox" id="wrap-{base}-ps" '
                 f'style="display:none">{svg_ps}</div>'
                 f'<div class="svgbox" id="wrap-{base}-qn" '
                 f'style="display:none">{svg_qn}</div>'
                 f'<div class="svgbox" id="wrap-{base}-qs" '
                 f'style="display:none">{svg_qs}</div>')
    return f'''
<div class="figcard" id="card-{base}">
  <div class="figbar">
    <input class="title" id="ttl-{base}" type="text" value="{esc(title)}"
           title="Custom plot title" />
    <button class="small" id="swap-{base}" title="Interchange the X and Y axes">
      &#8646; Swap X&#8596;Y</button>
    {view_btn}
    <button class="small" id="png-{base}"
      title="Download the current view (orientation, colours, toggles, title) as a PNG">
      &#8595; PNG</button>
    <span class="axes-state" id="axst-{base}">{esc(axes_normal)}</span>
  </div>
  <div class="svgbox" id="wrap-{base}-n">{svg_n}</div>
  <div class="svgbox" id="wrap-{base}-s" style="display:none">{svg_s}</div>
  {extra}
  {cap}
</div>'''


def build_powercap_controls(manifest):
    """Sidebar section for PowerCeiling tier/algorithm comparison views.
    Rendered for every experiment; the controls stay disabled when the data
    has no _PC<tier> arms."""
    pc = manifest['powercap']
    dis = '' if pc['enabled'] else ' disabled'
    tier_options = ('<option value="uncapped">Uncapped (no cap)</option>'
                    + ''.join(f'<option value="{esc(t)}">@PC{esc(t)}</option>'
                              for t in pc['tiers']))
    base_options = ''.join(
        f'<option value="{esc(b["key"])}">{esc(b["label"])}</option>'
        for b in pc['bases'])
    if pc['enabled']:
        note = ('Capped arms are directly comparable only within a tier '
                '(same power cap). &ldquo;Algorithms at one tier&rdquo; is '
                'the fair within-tier comparison; &ldquo;Tiers of one '
                'algorithm&rdquo; shows how the cap degrades a single '
                'algorithm. The Universal Pareto reference (pooled over ALL '
                'arms) stays visible. Quality-indicator values are unchanged '
                '— only visibility is filtered.')
    else:
        note = ('Only available for PowerCeiling experiments — this data '
                'has no _PC&lt;tier&gt; arms.')
    return f'''
  <h2>PowerCap Views</h2>
  <div class="pcrow"><span>Mode</span>
    <select id="pc-mode"{dis}>
      <option value="all">All arms</option>
      <option value="tier">Algorithms at one tier</option>
      <option value="algo">Tiers of one algorithm</option>
    </select></div>
  <div class="pcrow"><span>Tier</span>
    <select id="pc-tier" disabled>{tier_options}</select></div>
  <div class="pcrow"><span>Algorithm</span>
    <select id="pc-base" disabled>{base_options}</select></div>
  <p class="note">{note}</p>'''


def build_html(exp_name, x_col, y_col, styles, manifest,
               pareto_cards, metric_cards, table_sections, generated):
    controls_algos = '\n'.join(
        f'<div class="algo-row">'
        f'<input type="checkbox" id="vis-{st["slug"]}" checked '
        f'title="show/hide algorithm" />'
        f'<input type="color" id="col-{st["slug"]}" value="{st["color"]}" '
        f'title="algorithm colour" />'
        f'<span class="nm" title="{esc(st["label"])}">{esc(st["label"])}</span>'
        f'</div>'
        for st in styles.values())
    css_vars = ' '.join(
        f'--c-{st["slug"]}: {st["color"]};' for st in styles.values())
    return f'''<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>{esc(exp_name)} — Interactive Report</title>
<style>:root {{ {css_vars} }}</style>
<style>{CSS}</style>
</head>
<body>
<header class="page">
  <h1>{esc(exp_name)} — Interactive Experiment Report</h1>
  <div class="sub">Objectives: {esc(psp.axis_label(x_col))} vs
    {esc(psp.axis_label(y_col))} (both minimized) &middot;
    Plots rendered with matplotlib (EAF publication style, as
    plot_scenario_pareto.py) &middot; generated {esc(generated)}</div>
</header>
<div class="layout">
<aside class="controls">
  <h2>Display</h2>
  <label class="row"><input type="checkbox" id="ck-legend"
    {'checked' if manifest['options']['show_legend'] else ''}/> Show legends</label>
  <label class="row"><input type="checkbox" id="ck-labels"
    {'checked' if manifest['options']['show_labels'] else ''}/> Show algorithm name labels</label>
  <label class="row"><input type="checkbox" id="ck-values" checked/>
    Show bar value labels</label>
  <label class="row"><input type="checkbox" id="ck-hv" checked/>
    Show HV values in legends</label>
  <label class="row sub"><input type="checkbox" id="ck-hv-univ" checked/>
    &hellip;including Universal Pareto</label>
  {build_powercap_controls(manifest)}
  <h2>Algorithms — colour &amp; visibility</h2>
  {controls_algos}
  <p style="margin-top:12px"><button class="small" id="btn-reset">
    Reset all settings</button></p>
  <p class="note">Colour and visibility apply to every plot, legend entry and
  table at once. Each plot has its own title box and an axis-swap button.</p>
</aside>
<main class="content">
{pareto_cards}
{metric_cards}
{table_sections}
</main>
</div>
<script>window.REPORT_MANIFEST = {embed_json(manifest)};</script>
<script>{JS}</script>
</body>
</html>'''


# =============================================================================
# MAIN
# =============================================================================

def compute_universal_hv_stats(all_pareto, all_metrics, uses_hv_fixed):
    """(median, q25, q75) of the Universal Pareto set's HV across scenarios,
    in the SAME definition the algorithm legend entries use: recomputed
    HV_fixed (recompute_hv.py math, which excludes the universal front from
    its own CSV) when quality indicators are present, otherwise the Java HV
    column. None if no universal data exists."""
    vals = []
    if uses_hv_fixed:
        try:
            import recompute_hv as rhv
        except ImportError:
            rhv = None
        if rhv is not None:
            for df in all_pareto.values():
                x_col, y_col = rhv.detect_objective_columns(df)
                univ = df[df['Algorithm'] == UNIVERSAL_KEY]
                if univ.empty:
                    continue
                pts = df[[x_col, y_col]].values.astype(float)
                bounds = (float(pts[:, 0].min()), float(pts[:, 1].min()),
                          float(pts[:, 0].max()), float(pts[:, 1].max()))
                univ_norm = rhv.normalize(
                    univ[[x_col, y_col]].values.astype(float), bounds)
                vals.append(rhv.hv_2d(univ_norm, (1.1, 1.1)) / (1.1 * 1.1))
    if not vals:
        for df in all_metrics.values():
            row = df[df['Algorithm'] == UNIVERSAL_KEY]
            if not row.empty and 'HV' in row.columns:
                v = float(row.iloc[0]['HV'])
                if np.isfinite(v):
                    vals.append(v)
    if not vals:
        return None
    return (float(np.median(vals)),
            float(np.percentile(vals, 25)),
            float(np.percentile(vals, 75)))


def discover_scenarios(reports_dir, config):
    found = set()
    for f in os.listdir(reports_dir):
        m = re.match(r'scenario_(\d+)_pareto_graph_data\.csv$', f)
        if m:
            found.add(int(m.group(1)))
    for s in range(1, int(config.get('scenarios', 3)) + 1):
        if os.path.exists(os.path.join(
                reports_dir, f'scenario_{s}_pareto_graph_data.csv')):
            found.add(s)
    return sorted(found)


def process_directory(reports_dir, out_path=None):
    config = psp.load_config(reports_dir)
    scenarios = discover_scenarios(reports_dir, config)
    if not scenarios:
        print('ERROR: no scenario_<N>_pareto_graph_data.csv files found.')
        return 1

    all_pareto, all_metrics, all_metrics_str, all_fronts = {}, {}, {}, {}
    all_collab_str = {}
    x_col = y_col = None
    for s in scenarios:
        pf = os.path.join(reports_dir, f'scenario_{s}_pareto_graph_data.csv')
        mf = os.path.join(reports_dir, f'scenario_{s}_performance_metrics.csv')
        df = pd.read_csv(pf)
        all_pareto[s] = df
        if x_col is None:
            x_col, y_col = psp.detect_objective_columns(df)
        print(f'  Loaded: {os.path.basename(pf)}')
        if os.path.exists(mf):
            all_metrics[s] = pd.read_csv(mf)
            all_metrics_str[s] = pd.read_csv(mf, dtype=str)
            print(f'  Loaded: {os.path.basename(mf)}')
        ff = os.path.join(reports_dir, f'scenario_{s}_algorithm_pareto_fronts.csv')
        if os.path.exists(ff):
            all_fronts[s] = pd.read_csv(ff)
            print(f'  Loaded: {os.path.basename(ff)}')
        cf = os.path.join(reports_dir, f'scenario_{s}_seed_collaboration.csv')
        if os.path.exists(cf):
            all_collab_str[s] = pd.read_csv(cf, dtype=str)
            print(f'  Loaded: {os.path.basename(cf)}')

    print(f'  Objectives detected: X={x_col}, Y={y_col}')
    bounds = psp.compute_global_bounds(all_pareto, x_col, y_col)
    bounds_swapped = (bounds[1], bounds[0], bounds[3], bounds[2])
    quality_df = psp.load_quality_indicators(reports_dir)
    hv_summary = psp.compute_hv_summary(all_metrics, quality_df)
    uses_hv_fixed = (quality_df is not None and not quality_df.empty
                     and 'HV_fixed' in quality_df.columns)
    hv_source = (
        'recomputed HV_fixed (scenario-fixed reference point, from '
        'quality_indicators_all_scenarios.csv)' if uses_hv_fixed
        else 'the HV column of scenario_&lt;N&gt;_performance_metrics.csv')
    univ_stats = compute_universal_hv_stats(all_pareto, all_metrics,
                                            uses_hv_fixed)
    if univ_stats is not None:
        hv_summary = dict(hv_summary)
        hv_summary[UNIVERSAL_KEY] = univ_stats

    # Algorithm order: pareto-data encounter order, then any metrics-only ones.
    algo_order = []
    for s in scenarios:
        for a in all_pareto[s]['Algorithm'].unique():
            if a not in algo_order:
                algo_order.append(a)
        if s in all_metrics:
            for a in all_metrics[s]['Algorithm'].unique():
                if a not in algo_order:
                    algo_order.append(a)
    # Universal set always listed last in the controls.
    if UNIVERSAL_KEY in algo_order:
        algo_order.remove(UNIVERSAL_KEY)
    algo_order.append(UNIVERSAL_KEY)
    styles = resolve_styles(algo_order)

    exp_name = os.path.basename(os.path.normpath(reports_dir))
    figs_manifest = []

    # --- Pareto figures (normal + swapped orientation) ---
    pareto_cards = ['<section class="block"><h2>Pareto Fronts</h2>'
                    '<p class="note">Multi-objective algorithms: 50% empirical '
                    'attainment surface with [25%, 75%] band across seeds. '
                    'Single-objective algorithms: median with IQR error bars. '
                    'Axes globally normalized to ideal (0,0) and nadir (1,1); '
                    'raw units on the secondary axes. HV in the legend is the '
                    'pooled median [IQR] per algorithm across ALL (scenario, '
                    f'seed) runs, computed from {hv_source} — the same figure '
                    'plot_scenario_pareto.py publishes. It intentionally '
                    'differs from the per-scenario HV means shown in the '
                    'Quality Indicators section below. The Points-view '
                    'button on each plot switches to a plain raw-unit '
                    'scatter of every algorithm&rsquo;s pooled Pareto set, '
                    'with the Universal Pareto set overlaid as open black '
                    'rings — the coloured marker inside a ring is the '
                    'algorithm that contributed that point.</p>']
    for i, s in enumerate(scenarios):
        base = f'f{i}'
        name = psp.SCENARIO_NAMES.get(s, f'Scenario {s}')
        title = f'Scenario {s}: {name}'
        print(f'  Rendering Pareto figure (scenario {s}) ...')
        svg_n = render_pareto_figure(
            base + 'n', all_pareto[s], s, name, x_col, y_col,
            bounds, styles, hv_summary, config)
        svg_s = render_pareto_figure(
            base + 's', all_pareto[s], s, name, y_col, x_col,
            bounds_swapped, styles, hv_summary, config)
        svg_pn = render_points_figure(
            base + 'pn', all_pareto[s], all_fronts.get(s), s, name,
            x_col, y_col, styles, hv_summary, config)
        svg_ps = render_points_figure(
            base + 'ps', all_pareto[s], all_fronts.get(s), s, name,
            y_col, x_col, styles, hv_summary, config)
        svg_qn = render_points_figure(
            base + 'qn', all_pareto[s], all_fronts.get(s), s, name,
            x_col, y_col, styles, hv_summary, config, bounds=bounds)
        svg_qs = render_points_figure(
            base + 'qs', all_pareto[s], all_fronts.get(s), s, name,
            y_col, x_col, styles, hv_summary, config, bounds=bounds_swapped)
        ax_n = f'X: {psp.axis_label(x_col)} / Y: {psp.axis_label(y_col)}'
        ax_s = f'X: {psp.axis_label(y_col)} / Y: {psp.axis_label(x_col)}'
        pareto_cards.append(fig_card(base, title, ax_n, ax_s, svg_n, svg_s,
                                     svg_pn, svg_ps, svg_qn, svg_qs))
        figs_manifest.append({'base': base, 'title': title, 'kind': 'pareto',
                              'hasPoints': True,
                              'axesNormal': ax_n, 'axesSwapped': ax_s})
    pareto_cards.append('</section>')

    # --- Metric figures (vertical + horizontal orientation) ---
    metric_cards = []
    if all_metrics:
        metric_cards.append(
            '<section class="block"><h2>Quality Indicators — Plots</h2>'
            '<p class="note">Bars are per-algorithm MEAN values per scenario, '
            'taken verbatim from scenario_&lt;N&gt;_performance_metrics.csv '
            '(the Java-computed indicators — NOT the pooled HV_fixed used in '
            'the Pareto plot legends), plus the Universal_Pareto reference. '
            'GD and IGD of the universal set are 0 by definition (it is the '
            'reference front). For Pareto contribution, the CSV MEAN row '
            'holds the TOTAL number of universal-front points contributed '
            'across all seeds (the universal bar is the front size). '
            'Swapping axes turns the chart horizontal.</p>')
        for metric, title_txt, ylab, is_int in METRIC_SPECS:
            if not any(metric in df.columns for df in all_metrics.values()):
                continue
            base = f'f{metric.lower()}'
            print(f'  Rendering metric figure ({metric}) ...')
            svg_n = render_metric_figure(base + 'n', metric, title_txt, ylab,
                                         all_metrics, styles, config, False,
                                         is_int)
            svg_s = render_metric_figure(base + 's', metric, title_txt, ylab,
                                         all_metrics, styles, config, True,
                                         is_int)
            if svg_n is None:
                continue
            unit = 'total' if is_int else 'mean'
            ax_n = f'X: scenario / Y: {metric} ({unit})'
            ax_s = f'X: {metric} ({unit}) / Y: scenario'
            metric_cards.append(fig_card(base, title_txt, ax_n, ax_s,
                                         svg_n, svg_s))
            figs_manifest.append({'base': base, 'title': title_txt,
                                  'kind': 'metric',
                                  'axesNormal': ax_n, 'axesSwapped': ax_s})
        metric_cards.append('</section>')

    # --- Tables (HV; GD & IGD; full CSV mirror) ---
    def table_rows(df, cols):
        return [['' if pd.isna(r[c]) else str(r[c]) for c in cols]
                for _, r in df.iterrows()]

    tables = []
    tables_manifest = []
    if all_metrics_str:
        tables.append('<section class="block"><h2>Quality Indicators — '
                      'Tables</h2><p class="note">Values verbatim from '
                      'scenario_&lt;N&gt;_performance_metrics.csv (the same '
                      'tables the scripts in scripts/ consume): one row per '
                      'seed plus MEAN and STDDEV, and the Universal_Pareto '
                      'reference row. Click a seeds button to expand.</p>'
                      '<p><button class="small" id="btn-xlsx-all">&#8595; '
                      'Download all tables (.xlsx)</button></p>')
        for s in scenarios:
            if s not in all_metrics_str:
                continue
            name = psp.SCENARIO_NAMES.get(s, '')
            tables.append(f'<h3 class="scen">Scenario {s}'
                          f'{": " + esc(name) if name else ""}</h3>')
            df = all_metrics_str[s]
            hv_cols = [c for c in ('Algorithm', 'Seed', 'HV', 'HV_fixed')
                       if c in df.columns]
            gd_cols = [c for c in ('Algorithm', 'Seed', 'GD', 'IGD')
                       if c in df.columns]
            pc_cols = [c for c in ('Algorithm', 'Seed', 'ParetoContribution')
                       if c in df.columns]
            full_cols = list(df.columns)
            tables_manifest.append({'id': f'tbl-hv-{s}', 'sheet': f'S{s} HV',
                                    'cols': hv_cols,
                                    'rows': table_rows(df, hv_cols)})
            tables_manifest.append({'id': f'tbl-gdigd-{s}',
                                    'sheet': f'S{s} GD-IGD', 'cols': gd_cols,
                                    'rows': table_rows(df, gd_cols)})
            if 'ParetoContribution' in df.columns:
                tables_manifest.append({'id': f'tbl-pc-{s}',
                                        'sheet': f'S{s} ParetoCount',
                                        'cols': pc_cols,
                                        'rows': table_rows(df, pc_cols)})
            tables_manifest.append({'id': f'tbl-full-{s}', 'sheet': f'S{s} Full',
                                    'cols': full_cols,
                                    'rows': table_rows(df, full_cols)})
            tables.append(f'<h4>Hypervolume (HV) <button class="small dl-xlsx" '
                          f'data-table="tbl-hv-{s}">&#8595; XLSX</button></h4>')
            tables.append(build_metric_table(
                df, hv_cols, styles, f'tbl-hv-{s}'))
            tables.append('<h4>Generational Distance (GD) &amp; Inverted '
                          'Generational Distance (IGD) '
                          f'<button class="small dl-xlsx" '
                          f'data-table="tbl-gdigd-{s}">&#8595; XLSX</button></h4>')
            tables.append(build_metric_table(
                df, ['Algorithm', 'Seed', 'GD', 'IGD'], styles, f'tbl-gdigd-{s}'))
            if 'ParetoContribution' in df.columns:
                tables.append('<h4>Pareto Count (points contributed to the '
                              'Universal Pareto set) '
                              f'<button class="small dl-xlsx" '
                              f'data-table="tbl-pc-{s}">&#8595; XLSX</button>'
                              '</h4>')
                tables.append(build_metric_table(
                    df, ['Algorithm', 'Seed', 'ParetoContribution'], styles,
                    f'tbl-pc-{s}'))
            if s in all_collab_str:
                cdf = all_collab_str[s]
                collab_cols = list(cdf.columns)
                tables_manifest.append({'id': f'tbl-collab-{s}',
                                        'sheet': f'S{s} Collab',
                                        'cols': collab_cols,
                                        'rows': table_rows(cdf, collab_cols)})
                tables.append('<h4>Seed Collaboration (per-seed universal '
                              'front sharing, near-tie credit) '
                              f'<button class="small dl-xlsx" '
                              f'data-table="tbl-collab-{s}">&#8595; XLSX'
                              '</button></h4>')
                tables.append('<p class="note">Each seed&#39;s own all-arms '
                              'universal front and each algorithm&#39;s share '
                              'of it (near-tie credit at 0.3% relative '
                              'tolerance, nearest-match) — distributional '
                              'collaboration evidence, unlike the pooled '
                              'best-of-all-seeds universal front above.</p>')
                tables.append(build_metric_table(
                    cdf, collab_cols, styles, f'tbl-collab-{s}'))
            tables.append(
                f'<details class="rawcsv"><summary>Full metrics table '
                f'(scenario_{s}_performance_metrics.csv, verbatim)</summary>'
                f'<p><button class="small dl-xlsx" data-table="tbl-full-{s}">'
                f'&#8595; XLSX (full table)</button></p>'
                + build_full_table(df, styles, f'tbl-full-{s}') + '</details>')
        tables.append('</section>')

    pc_tiers = sorted({pc_parts(k)[1] for k in styles if pc_parts(k)[1]},
                      key=int, reverse=True)
    pc_bases = [k for k in styles
                if k != UNIVERSAL_KEY and pc_parts(k)[1] is None]
    manifest = {
        'expName': exp_name,
        'algos': [{'slug': st['slug'], 'key': k, 'label': st['label'],
                   'color': st['color'],
                   'tier': pc_parts(k)[1], 'baseKey': pc_parts(k)[0],
                   'hvSuffix': psp.format_hv_suffix(hv_summary.get(k))}
                  for k, st in styles.items()],
        'figs': figs_manifest,
        'tables': tables_manifest,
        'powercap': {
            'enabled': bool(pc_tiers),
            'tiers': pc_tiers,
            'bases': [{'key': k, 'label': styles[k]['label']}
                      for k in pc_bases],
        },
        'options': {
            'show_legend': bool(config.get('show_legend', True)),
            'show_labels': bool(config.get('show_labels', True)),
        },
    }
    generated = datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M UTC')
    html_doc = build_html(exp_name, x_col, y_col, styles, manifest,
                          '\n'.join(pareto_cards), '\n'.join(metric_cards),
                          '\n'.join(tables), generated)

    out = out_path or os.path.join(reports_dir, HTML_NAME)
    with open(out, 'w', encoding='utf-8') as f:
        f.write(html_doc)
    print(f'  Saved: {out}  ({os.path.getsize(out) / 1e6:.1f} MB)')
    return 0


def default_initial_dir():
    """Sensible starting folder for the picker: the repo's results folders."""
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    for cand in ('results', 'newExperimentResults'):
        path = os.path.join(repo, cand)
        if os.path.isdir(path):
            return path
    return repo


def choose_paths_console(preset_out=None):
    """Console fallback when no GUI is available. Returns (dir, out) or
    (None, None) on cancel / non-interactive stdin."""
    if not sys.stdin.isatty():
        print('ERROR: no results directory given, and neither a GUI nor an '
              'interactive console is available to ask for one.')
        print(f'Usage: python {os.path.basename(__file__)} '
              '<reports_directory> [--out FILE]')
        return None, None
    d = input('Experiment results folder: ').strip().strip('\'"')
    if not d:
        return None, None
    if preset_out:
        return d, preset_out
    default_out = os.path.join(d, HTML_NAME)
    o = input(f'Output HTML path [{default_out}]: ').strip().strip('\'"')
    return d, (o or default_out)


def choose_paths_interactively(preset_out=None):
    """File-picker dialogs for the results folder and the output HTML.
    Falls back to console prompts when tkinter or a display is missing.
    Returns (reports_dir, out_path); (None, None) means the user cancelled."""
    try:
        import tkinter as tk
        from tkinter import filedialog
    except ImportError:
        return choose_paths_console(preset_out)
    try:
        root = tk.Tk()
    except Exception:  # no display / broken Tk — fall back to the console
        return choose_paths_console(preset_out)
    root.withdraw()
    root.update()
    try:
        reports_dir = filedialog.askdirectory(
            title='Select the experiment results folder '
                  '(contains scenario_*_pareto_graph_data.csv)',
            initialdir=default_initial_dir())
        if not reports_dir:
            return None, None
        if preset_out:
            return reports_dir, preset_out
        out = filedialog.asksaveasfilename(
            title='Save the interactive report as...',
            initialdir=reports_dir, initialfile=HTML_NAME,
            defaultextension='.html',
            filetypes=[('HTML files', '*.html'), ('All files', '*.*')])
        if not out:
            return None, None
        return reports_dir, out
    finally:
        root.destroy()


def main():
    p = argparse.ArgumentParser(description=__doc__.split('\n')[1])
    p.add_argument('reports_dir', nargs='?', default=None,
                   help='experiment results directory (omit to pick both '
                        'the folder and the output file via dialogs)')
    p.add_argument('--out', default=None, help='output HTML path '
                   f'(default: <reports_dir>/{HTML_NAME})')
    args = p.parse_args()
    reports_dir, out_path = args.reports_dir, args.out
    if reports_dir is None:
        reports_dir, out_path = choose_paths_interactively(preset_out=args.out)
        if not reports_dir:
            print('No folder selected — nothing to do.')
            return 1
    if not os.path.isdir(reports_dir):
        print(f'ERROR: {reports_dir} is not a directory')
        return 1
    print(f'Processing: {reports_dir}')
    rc = process_directory(reports_dir, out_path)
    if rc == 0:
        print('Done.')
    return rc


if __name__ == '__main__':
    sys.exit(main())
