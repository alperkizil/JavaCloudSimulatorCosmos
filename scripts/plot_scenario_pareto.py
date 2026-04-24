#!/usr/bin/env python3
"""
Scenario Comparison Pareto Front Plotter for JavaCloudSimulatorCosmos.

Consumes CSV data produced by either ScenarioComparisonExperimentRunner.java
(Makespan vs Energy) or WaitingTimeExperimentRunner.java (WaitingTime vs Energy).
The X-axis objective is auto-detected from the Pareto CSV header, so the same
script serves both runners.

Usage:
    python plot_scenario_pareto.py <reports_directory>
"""

import matplotlib.pyplot as plt
import matplotlib as mpl
import pandas as pd
import numpy as np
import sys
import os
import json


# =============================================================================
# PUBLICATION STYLING (serif, thin spines, Okabe-Ito palette)
# =============================================================================

mpl.rcParams.update({
    'font.family': 'serif',
    'font.serif': ['STIX Two Text', 'STIX', 'DejaVu Serif', 'Times New Roman', 'serif'],
    'mathtext.fontset': 'stix',
    'axes.titlesize': 12,
    'axes.labelsize': 11,
    'xtick.labelsize': 9,
    'ytick.labelsize': 9,
    'legend.fontsize': 9,
    'figure.titlesize': 14,
    'axes.spines.top': False,
    'axes.spines.right': False,
    'axes.linewidth': 0.8,
    'xtick.direction': 'in',
    'ytick.direction': 'in',
    'xtick.minor.visible': True,
    'ytick.minor.visible': True,
    'xtick.major.size': 4,
    'ytick.major.size': 4,
    'xtick.minor.size': 2,
    'ytick.minor.size': 2,
    'grid.color': '#cccccc',
    'grid.linewidth': 0.5,
    'grid.alpha': 0.7,
    'axes.axisbelow': True,
})

DEFAULT_CONFIG = {
    'dpi': 300,
    'width': 18,
    'height': 7,
    'marker_size': 10,
    'show_legend': True,
    'show_labels': True,
    'scenarios': 3,
}

# Okabe-Ito palette — colorblind-safe; survives grayscale via shape+fill coding.
OKABE_ITO = {
    'black':          '#000000',
    'orange':         '#E69F00',
    'sky_blue':       '#56B4E9',
    'bluish_green':   '#009E73',
    'yellow':         '#F0E442',
    'blue':           '#0072B2',
    'vermillion':     '#D55E00',
    'reddish_purple': '#CC79A7',
}

# Color = algorithm family; shape = family; fill = objective variant.
# Triply-redundant encoding: figure remains readable in B/W and for colorblind viewers.
# Tuple = (color, marker, filled, display_label)
ALGORITHM_STYLE = {
    'GA_Makespan':    (OKABE_ITO['blue'],           'D', False, 'GA (Makespan)'),
    'GA_WaitingTime': (OKABE_ITO['blue'],           'D', False, 'GA (Waiting Time)'),
    'GA_Energy':      (OKABE_ITO['blue'],           'D', True,  'GA (Energy)'),
    'SA_Makespan':    (OKABE_ITO['vermillion'],     's', False, 'SA (Makespan)'),
    'SA_WaitingTime': (OKABE_ITO['vermillion'],     's', False, 'SA (Waiting Time)'),
    'SA_Energy':      (OKABE_ITO['vermillion'],     's', True,  'SA (Energy)'),
    # Dominance-archive variants produce Pareto fronts (rendered as EAF contours).
    # Family-related shades (blues for GA, reds/pinks for SA) keep the visual
    # grouping while distinguishing them from the black Universal Pareto line.
    'GA_Makespan_Dominance':    ('#56B4E9', 'D', True, 'GA Dominance (Makespan)'),
    'GA_WaitingTime_Dominance': ('#56B4E9', 'D', True, 'GA Dominance (Waiting Time)'),
    'GA_Energy_Dominance':      ('#1B3A6B', 'D', True, 'GA Dominance (Energy)'),
    'SA_Makespan_Dominance':    ('#FF6B9D', 's', True, 'SA Dominance (Makespan)'),
    'SA_WaitingTime_Dominance': ('#FF6B9D', 's', True, 'SA Dominance (Waiting Time)'),
    'SA_Energy_Dominance':      ('#8B0000', 's', True, 'SA Dominance (Energy)'),
    'NSGA-II':        (OKABE_ITO['bluish_green'],   'o', True,  'NSGA-II'),
    'SPEA-II':        (OKABE_ITO['reddish_purple'], '^', True,  'SPEA-II'),
    'AMOSA':          (OKABE_ITO['orange'],         'v', True,  'AMOSA'),
    # Heuristic baselines: shared marker (thick plus) + close grayscale family
    # so the group visually reads as "reference baselines".
    'FirstAvailable': ('#404040',                   'P', True,  'First Available'),
    'ShortestQueue':  ('#707070',                   'P', True,  'Shortest Queue'),
    'WorkloadAware':  ('#A0A0A0',                   'P', True,  'Workload-Aware'),
    'RoundRobin':     ('#555555',                   'P', False, 'Round Robin'),
}

UNIVERSAL_PARETO_COLOR = OKABE_ITO['black']
UNIVERSAL_PARETO_LABEL = 'Universal Pareto'

SINGLE_OBJ_ALGORITHMS = {
    'GA_Makespan', 'GA_WaitingTime', 'GA_Energy',
    'SA_Makespan', 'SA_WaitingTime', 'SA_Energy',
}
MULTI_OBJ_ALGORITHMS = {
    'NSGA-II', 'SPEA-II', 'AMOSA',
    'GA_Makespan_Dominance', 'GA_WaitingTime_Dominance', 'GA_Energy_Dominance',
    'SA_Makespan_Dominance', 'SA_WaitingTime_Dominance', 'SA_Energy_Dominance',
}
BASELINE_ALGORITHMS = {'FirstAvailable', 'ShortestQueue', 'WorkloadAware', 'RoundRobin'}

SCENARIO_NAMES = {1: 'Balanced', 2: 'GPU Stress', 3: 'CPU Stress'}

# Human-readable axis labels keyed by CSV column name.
AXIS_LABEL = {
    'Makespan':    'Makespan (s)',
    'WaitingTime': 'Average Waiting Time (s)',
    'Energy':      'Energy (kWh)',
}


def axis_label(col_name):
    return AXIS_LABEL.get(col_name, col_name)


def get_style(algo):
    return ALGORITHM_STYLE.get(algo, (OKABE_ITO['black'], 'o', True, algo))


# =============================================================================
# NON-DOMINANCE FILTERING (both objectives minimized)
# =============================================================================

def is_dominated(point, other_points):
    for other in other_points:
        if np.array_equal(other, point):
            continue
        if other[0] <= point[0] and other[1] <= point[1]:
            if other[0] < point[0] or other[1] < point[1]:
                return True
    return False


def get_non_dominated(points):
    if len(points) == 0:
        return np.array([])
    points = np.array(points)
    non_dominated = []
    for point in points:
        if not is_dominated(point, points):
            is_dup = any(
                abs(nd[0] - point[0]) < 1e-9 and abs(nd[1] - point[1]) < 1e-9
                for nd in non_dominated
            )
            if not is_dup:
                non_dominated.append(point)
    if not non_dominated:
        return np.array([])
    non_dominated = np.array(non_dominated)
    return non_dominated[np.argsort(non_dominated[:, 0])]


# =============================================================================
# CONFIG + OBJECTIVE DETECTION
# =============================================================================

def load_config(reports_dir):
    config = dict(DEFAULT_CONFIG)
    json_path = os.path.join(reports_dir, 'plot_options.json')
    if os.path.exists(json_path):
        with open(json_path) as f:
            config.update(json.load(f))
    return config


def detect_objective_columns(df):
    """Pareto CSV layout is Algorithm,Seed,<ObjA>,<ObjB>,IsUniversalPareto."""
    cols = list(df.columns)
    if len(cols) >= 4 and cols[0] == 'Algorithm' and cols[1] == 'Seed':
        return cols[2], cols[3]
    numeric = [c for c in cols if c not in ('Algorithm', 'Seed', 'IsUniversalPareto')]
    if len(numeric) >= 2:
        return numeric[0], numeric[1]
    return 'Makespan', 'Energy'


# =============================================================================
# GLOBAL NORMALIZATION (single ideal/nadir across all scenarios + seeds)
# =============================================================================

# Algorithms whose extreme values would stretch normalization and compress the
# rest of the field into an unreadable corner. Excluded from bounds computation
# only — their points are still plotted (and may land at/past the axis edge,
# which is the honest visual summary of "this baseline is pathologically bad").
BOUNDS_EXCLUDED_ALGORITHMS = {'FirstAvailable'}


def compute_global_bounds(all_pareto_data, x_col, y_col,
                          exclude_algorithms=BOUNDS_EXCLUDED_ALGORITHMS):
    xs, ys = [], []
    for df in all_pareto_data.values():
        sub = df[~df['Algorithm'].isin(exclude_algorithms)]
        xs.extend(sub[x_col].values.tolist())
        ys.extend(sub[y_col].values.tolist())
    if not xs:
        # Fallback: unfiltered if excluded set removed everything.
        for df in all_pareto_data.values():
            xs.extend(df[x_col].values.tolist())
            ys.extend(df[y_col].values.tolist())
    xs = np.array(xs, dtype=float)
    ys = np.array(ys, dtype=float)
    return float(xs.min()), float(ys.min()), float(xs.max()), float(ys.max())


def normalize_points(pts, bounds):
    ideal_x, ideal_y, nadir_x, nadir_y = bounds
    range_x = max(nadir_x - ideal_x, 1e-12)
    range_y = max(nadir_y - ideal_y, 1e-12)
    pts = np.asarray(pts, dtype=float)
    out = np.empty_like(pts)
    out[:, 0] = (pts[:, 0] - ideal_x) / range_x
    out[:, 1] = (pts[:, 1] - ideal_y) / range_y
    return out


# =============================================================================
# EAF — Empirical Attainment Function across seeds (grid-based)
# =============================================================================

def compute_eaf_grid(seed_fronts, grid_x, grid_y):
    """
    Attainment frequency over a 2D grid. seed_fronts is a list of non-dominated
    point arrays (already normalized). Returns an array of shape
    (len(grid_y), len(grid_x)) with values in [0, 1].
    """
    N = len(seed_fronts)
    if N == 0:
        return np.zeros((len(grid_y), len(grid_x)))
    thresholds = np.full((N, len(grid_x)), np.inf)
    for i, front in enumerate(seed_fronts):
        if len(front) == 0:
            continue
        order = np.argsort(front[:, 0])
        fs = front[order]
        cum_min = np.minimum.accumulate(fs[:, 1])
        idx = np.searchsorted(fs[:, 0], grid_x, side='right') - 1
        valid = idx >= 0
        thresholds[i, valid] = cum_min[idx[valid]]
    freq = np.zeros((len(grid_y), len(grid_x)))
    for j in range(len(grid_x)):
        col = np.sort(thresholds[:, j])
        counts = np.searchsorted(col, grid_y, side='right')
        freq[:, j] = counts / N
    return freq


# =============================================================================
# HV SUMMARY + KNEE POINT + LEGEND LABEL FORMATTING
# =============================================================================

def compute_hv_summary(all_metrics_data, quality_df=None):
    """
    algo -> (median, q25, q75) across all (scenario, seed) pairs.
    Prefers recomputed HV_fixed from recompute_hv.py output (quality_df) when
    present, so the legend reflects fair scenario-fixed reference-point HV.
    """
    if quality_df is not None and not quality_df.empty and 'HV_fixed' in quality_df.columns:
        out = {}
        for algo, sub in quality_df.groupby('Algorithm'):
            vals = sub['HV_fixed'].dropna().values
            if len(vals) == 0:
                continue
            out[algo] = (float(np.median(vals)),
                         float(np.percentile(vals, 25)),
                         float(np.percentile(vals, 75)))
        if out:
            return out

    rows = []
    for df in all_metrics_data.values():
        rows.append(df[df['Algorithm'] != 'Universal_Pareto'][['Algorithm', 'HV']])
    if not rows:
        return {}
    all_hv = pd.concat(rows, ignore_index=True)
    out = {}
    for algo, sub in all_hv.groupby('Algorithm'):
        vals = sub['HV'].values
        out[algo] = (float(np.median(vals)),
                     float(np.percentile(vals, 25)),
                     float(np.percentile(vals, 75)))
    return out


def format_hv_suffix(hv_stats):
    if hv_stats is None:
        return ''
    med, q25, q75 = hv_stats
    return f'  HV={med:.2f} [{q25:.2f},{q75:.2f}]'


def knee_point(front):
    if len(front) < 3:
        return None
    x0, y0 = front[0]
    xn, yn = front[-1]
    a = yn - y0
    b = -(xn - x0)
    c = -(a * x0 + b * y0)
    denom = np.hypot(a, b)
    if denom < 1e-12:
        return None
    dists = np.abs(a * front[:, 0] + b * front[:, 1] + c) / denom
    return front[int(np.argmax(dists))]


# =============================================================================
# PARETO FRONT PLOTTING
# =============================================================================

def plot_scenario_pareto(ax, df, scenario_num, x_col, y_col, bounds, hv_summary, config):
    ax.set_title(f'Scenario {scenario_num}: {SCENARIO_NAMES.get(scenario_num, "Unknown")}')
    ax.set_xlabel(f'{axis_label(x_col)}  (normalized)')
    ax.set_ylabel(f'{axis_label(y_col)}  (normalized)')
    ax.set_xlim(-0.03, 1.05)
    ax.set_ylim(-0.03, 1.05)
    ax.grid(True, which='major')

    algorithms = [a for a in df['Algorithm'].unique() if a != 'Universal_Pareto']
    marker_size = config.get('marker_size', 10)
    grid_x = np.linspace(0.0, 1.05, 220)
    grid_y = np.linspace(0.0, 1.05, 220)

    for algo in algorithms:
        algo_df = df[df['Algorithm'] == algo]
        color, marker, filled, display = get_style(algo)
        face = color if filled else 'none'
        label = f'{display}{format_hv_suffix(hv_summary.get(algo))}'

        if algo in SINGLE_OBJ_ALGORITHMS or algo in BASELINE_ALGORITHMS:
            pts = normalize_points(algo_df[[x_col, y_col]].values, bounds)
            if len(pts) == 0:
                continue
            mx, my = float(np.median(pts[:, 0])), float(np.median(pts[:, 1]))
            qx = np.percentile(pts[:, 0], [25, 75])
            qy = np.percentile(pts[:, 1], [25, 75])
            # Baselines with extreme values (e.g. FirstAvailable) are excluded
            # from bounds computation and may land past the axis. Clamp the
            # marker to the corner and tag the legend so it stays visible.
            x_max, y_max = 1.02, 1.02
            off_chart = mx > x_max or my > y_max
            if off_chart:
                mx_disp = min(mx, x_max)
                my_disp = min(my, y_max)
                label = f'{label}  (off-chart)'
                xerr = None
                yerr = None
            else:
                mx_disp, my_disp = mx, my
                xerr = [[max(mx - qx[0], 0.0)], [max(qx[1] - mx, 0.0)]]
                yerr = [[max(my - qy[0], 0.0)], [max(qy[1] - my, 0.0)]]
            ax.errorbar(
                mx_disp, my_disp,
                xerr=xerr, yerr=yerr,
                fmt=marker, markersize=float(np.sqrt(marker_size * 18)),
                markerfacecolor=face, markeredgecolor=color, markeredgewidth=1.2,
                ecolor=color, elinewidth=0.9, capsize=3, capthick=0.9,
                label=label, zorder=5,
            )
        else:
            seed_fronts = []
            for _, seed_df in algo_df.groupby('Seed'):
                pts = normalize_points(seed_df[[x_col, y_col]].values, bounds)
                nd = get_non_dominated(pts)
                if len(nd) > 0:
                    seed_fronts.append(nd)
            if not seed_fronts:
                continue
            freq = compute_eaf_grid(seed_fronts, grid_x, grid_y)
            try:
                ax.contourf(grid_x, grid_y, freq, levels=[0.25, 0.75],
                            colors=[color], alpha=0.18, zorder=2)
            except ValueError:
                pass
            try:
                ax.contour(grid_x, grid_y, freq, levels=[0.5],
                           colors=[color], linewidths=1.6, zorder=4)
            except ValueError:
                pass
            ax.plot([], [], color=color, linewidth=1.6, label=label)

    univ_df = df[df['Algorithm'] == 'Universal_Pareto']
    if not univ_df.empty:
        univ_pts = normalize_points(univ_df[[x_col, y_col]].values, bounds)
        univ_sorted = univ_pts[np.argsort(univ_pts[:, 0])]
        ax.plot(
            univ_sorted[:, 0], univ_sorted[:, 1],
            color=UNIVERSAL_PARETO_COLOR, linewidth=1.8,
            linestyle='-', alpha=0.9, label=UNIVERSAL_PARETO_LABEL, zorder=6,
        )
        ax.scatter(
            univ_sorted[:, 0], univ_sorted[:, 1],
            s=marker_size * 3, marker='o',
            facecolors=UNIVERSAL_PARETO_COLOR,
            edgecolors=UNIVERSAL_PARETO_COLOR, linewidths=0.4,
            zorder=7,
        )
        kp = knee_point(univ_sorted)
        if kp is not None:
            ax.annotate(
                'knee',
                xy=(kp[0], kp[1]),
                xytext=(18, 18), textcoords='offset points',
                fontsize=8, style='italic', color=UNIVERSAL_PARETO_COLOR,
                arrowprops=dict(arrowstyle='-', color=UNIVERSAL_PARETO_COLOR,
                                lw=0.6, shrinkA=0, shrinkB=2),
                zorder=8,
            )

    # Ideal-point reference marker at (0, 0)
    ax.scatter([0.0], [0.0], marker='*', s=140,
               facecolor='white', edgecolor='black', linewidths=0.9, zorder=8)
    ax.annotate('ideal', xy=(0.0, 0.0), xytext=(6, 6),
                textcoords='offset points', fontsize=8, style='italic')

    # Secondary axes in raw units (global bounds → same ticks on every panel)
    ideal_x, ideal_y, nadir_x, nadir_y = bounds
    range_x = max(nadir_x - ideal_x, 1e-12)
    range_y = max(nadir_y - ideal_y, 1e-12)
    secx = ax.secondary_xaxis(
        'top',
        functions=(lambda u, a=ideal_x, r=range_x: a + u * r,
                   lambda v, a=ideal_x, r=range_x: (v - a) / r),
    )
    secx.set_xlabel(axis_label(x_col), fontsize=9, labelpad=4)
    secx.tick_params(labelsize=8)
    secy = ax.secondary_yaxis(
        'right',
        functions=(lambda u, a=ideal_y, r=range_y: a + u * r,
                   lambda v, a=ideal_y, r=range_y: (v - a) / r),
    )
    secy.tick_params(labelsize=8)


def load_quality_indicators(reports_dir):
    """Load quality_indicators_all_scenarios.csv if recompute_hv.py has been run."""
    path = os.path.join(reports_dir, 'quality_indicators_all_scenarios.csv')
    if not os.path.exists(path):
        return None
    return pd.read_csv(path)


def plot_runtime_and_efficiency(reports_dir, all_metrics_data, quality_df, config):
    """
    Two-panel figure: wall-clock time and HV-per-second (efficiency).
    Uses recomputed HV_fixed when available; otherwise falls back to Java HV.
    """
    if not all_metrics_data:
        return
    rows = []
    for s, df in all_metrics_data.items():
        sub = df[df['Algorithm'] != 'Universal_Pareto'][
            ['Algorithm', 'Seed', 'TimeMs', 'HV']
        ].copy()
        sub['Scenario'] = s
        rows.append(sub)
    time_df = pd.concat(rows, ignore_index=True)

    # Drop MEAN/STDDEV summary rows — Seed then coerces cleanly to int and
    # aligns with the numeric Seed column in quality_indicators_all_scenarios.csv.
    time_df = time_df[pd.to_numeric(time_df['Seed'], errors='coerce').notna()].copy()
    time_df['Seed'] = time_df['Seed'].astype(int)
    time_df['TimeSec'] = time_df['TimeMs'].astype(float) / 1000.0

    if quality_df is not None and 'HV_fixed' in quality_df.columns:
        q = quality_df[['Scenario', 'Algorithm', 'Seed', 'HV_fixed']].copy()
        q['Seed'] = q['Seed'].astype(int)
        merged = time_df.merge(
            q, on=['Scenario', 'Algorithm', 'Seed'], how='left',
        )
        hv_col = 'HV_fixed'
    else:
        merged = time_df
        hv_col = 'HV'
    merged['HVperSec'] = merged[hv_col] / merged['TimeSec'].clip(lower=1e-9)

    fig, (ax_t, ax_e) = plt.subplots(1, 2, figsize=(config.get('width', 18), 5))

    algos = sorted(merged['Algorithm'].unique(),
                   key=lambda a: list(ALGORITHM_STYLE.keys()).index(a)
                   if a in ALGORITHM_STYLE else 99)

    # Panel A: wall-clock time, median with IQR error bars
    for i, algo in enumerate(algos):
        vals = merged[merged['Algorithm'] == algo]['TimeSec'].dropna().values
        if len(vals) == 0:
            continue
        color, _, filled, display = get_style(algo)
        med = float(np.median(vals))
        q25 = float(np.percentile(vals, 25))
        q75 = float(np.percentile(vals, 75))
        ax_t.bar(
            i, med, 0.7,
            facecolor=color if filled else 'white',
            edgecolor=color, linewidth=0.9,
            hatch=None if filled else '///',
        )
        ax_t.errorbar(i, med, yerr=[[med - q25], [q75 - med]],
                      fmt='none', ecolor='black', elinewidth=0.8, capsize=3)
    ax_t.set_xticks(range(len(algos)))
    ax_t.set_xticklabels([get_style(a)[3] for a in algos],
                         rotation=30, ha='right', fontsize=9)
    ax_t.set_ylabel('Wall-clock time (s), median [IQR]')
    ax_t.set_title('Runtime per algorithm (pooled across scenarios/seeds)')
    ax_t.grid(True, axis='y')

    # Panel B: HV-per-second (efficiency)
    for i, algo in enumerate(algos):
        vals = merged[merged['Algorithm'] == algo]['HVperSec'].dropna().values
        if len(vals) == 0:
            continue
        color, _, filled, display = get_style(algo)
        med = float(np.median(vals))
        q25 = float(np.percentile(vals, 25))
        q75 = float(np.percentile(vals, 75))
        ax_e.bar(
            i, med, 0.7,
            facecolor=color if filled else 'white',
            edgecolor=color, linewidth=0.9,
            hatch=None if filled else '///',
        )
        ax_e.errorbar(i, med, yerr=[[max(med - q25, 0)], [max(q75 - med, 0)]],
                      fmt='none', ecolor='black', elinewidth=0.8, capsize=3)
    ax_e.set_xticks(range(len(algos)))
    ax_e.set_xticklabels([get_style(a)[3] for a in algos],
                         rotation=30, ha='right', fontsize=9)
    label_suffix = 'HV_fixed' if hv_col == 'HV_fixed' else 'HV'
    ax_e.set_ylabel(f'{label_suffix} per second  (higher is better)')
    ax_e.set_title('Budget-fair efficiency: quality gained per second of wall time')
    ax_e.grid(True, axis='y')

    fig.suptitle('Runtime and Efficiency Comparison', y=1.02)
    fig.tight_layout()
    out_path = os.path.join(reports_dir, 'runtime_and_efficiency.png')
    fig.savefig(out_path, dpi=config.get('dpi', 300), bbox_inches='tight')
    plt.close(fig)
    print(f'  Saved: {out_path}')


def plot_metrics_comparison(reports_dir, all_metrics, config):
    """
    Write one PNG per metric (HV, GD, IGD, ParetoContribution). Each plot shows
    a single averaged bar per algorithm per scenario, pulled from the MEAN row
    of scenario_<n>_performance_metrics.csv.
    """
    metrics_to_plot = [
        ('HV',                 'metric_hv.png',
         'Hypervolume (higher is better)',                            'HV (mean)'),
        ('GD',                 'metric_gd.png',
         'Generational Distance (lower is better)',                   'GD (mean)'),
        ('IGD',                'metric_igd.png',
         'Inverted Generational Distance (lower is better)',          'IGD (mean)'),
        ('ParetoContribution', 'metric_pareto_contribution.png',
         'Pareto Contribution (higher is better)',                    'Pareto contribution (total across seeds)'),
    ]

    for metric, filename, title, ylabel in metrics_to_plot:
        fig, ax = plt.subplots(figsize=(config.get('width', 18) * 0.6, 5))
        ax.set_title(title)
        ax.set_ylabel(ylabel)

        scenario_labels = []
        x_positions = []
        scenario_boundaries = []  # x-coords midway between scenarios
        bar_width = 0.12
        pos = 0.0
        is_int_metric = (metric == 'ParetoContribution')
        prev_end = None

        for scenario_num, metrics_df in sorted(all_metrics.items()):
            mean_rows = metrics_df[
                (metrics_df['Algorithm'] != 'Universal_Pareto') &
                (metrics_df['Seed'].astype(str) == 'MEAN')
            ]
            if mean_rows.empty:
                continue

            algorithms = mean_rows['Algorithm'].tolist()
            values = mean_rows[metric].tolist()

            n = len(algorithms)
            x = np.arange(n) * bar_width + pos

            # Midpoint between previous scenario's last bar and this one's first bar
            if prev_end is not None:
                scenario_boundaries.append((prev_end + x[0]) / 2)

            scenario_bars = []
            for j, (algo, val) in enumerate(zip(algorithms, values)):
                color, _, filled, display = get_style(algo)
                face = color if filled else 'white'
                label = display if pos == 0 else None
                bar = ax.bar(
                    x[j], val, bar_width * 0.85,
                    facecolor=face, edgecolor=color, linewidth=0.8,
                    label=label, hatch=None if filled else '///',
                )
                scenario_bars.append((bar, val))

            # Value labels on top of each bar
            for bar, val in scenario_bars:
                fmt = f'{int(round(val))}' if is_int_metric else f'{val:.3g}'
                ax.bar_label(bar, labels=[fmt], padding=2, fontsize=7, rotation=0)

            mid = pos + (n - 1) * bar_width / 2
            scenario_labels.append(f'S{scenario_num} ({SCENARIO_NAMES.get(scenario_num, "")})')
            x_positions.append(mid)
            prev_end = x[-1]
            pos += n * bar_width + bar_width * 2

        # Vertical separators between scenarios + shaded group backgrounds
        for xb in scenario_boundaries:
            ax.axvline(xb, color='gray', linestyle='--', linewidth=0.8, alpha=0.6)

        # Headroom so labels don't collide with the top of the axes
        y_min, y_max = ax.get_ylim()
        ax.set_ylim(y_min, y_max * 1.12)

        ax.set_xticks(x_positions)
        ax.set_xticklabels(scenario_labels)
        ax.grid(True, axis='y')
        ax.legend(loc='best', ncol=2, fontsize=8, framealpha=0.9)

        fig.tight_layout()
        out_path = os.path.join(reports_dir, filename)
        fig.savefig(out_path, dpi=config.get('dpi', 300), bbox_inches='tight')
        plt.close(fig)
        print(f'  Saved: {out_path}')


# =============================================================================
# MAIN PROCESSING
# =============================================================================

def process_directory(reports_dir):
    config = load_config(reports_dir)
    num_scenarios = config.get('scenarios', 3)

    all_pareto_data = {}
    all_metrics_data = {}
    x_col, y_col = None, None

    for s in range(1, num_scenarios + 1):
        pareto_file = os.path.join(reports_dir, f'scenario_{s}_pareto_graph_data.csv')
        metrics_file = os.path.join(reports_dir, f'scenario_{s}_performance_metrics.csv')

        if os.path.exists(pareto_file):
            df = pd.read_csv(pareto_file)
            all_pareto_data[s] = df
            if x_col is None:
                x_col, y_col = detect_objective_columns(df)
            print(f'  Loaded: scenario_{s}_pareto_graph_data.csv')
        else:
            print(f'  WARNING: {pareto_file} not found')

        if os.path.exists(metrics_file):
            all_metrics_data[s] = pd.read_csv(metrics_file)
            print(f'  Loaded: scenario_{s}_performance_metrics.csv')

    if not all_pareto_data:
        print('ERROR: No scenario data found.')
        return

    print(f'  Objectives detected: X={x_col}, Y={y_col}')

    # Global (ideal, nadir) across all scenarios/algorithms/seeds — excluding
    # pathological baselines whose extreme values would compress the rest of
    # the field into an unreadable corner.
    bounds = compute_global_bounds(all_pareto_data, x_col, y_col)
    ideal_x, ideal_y, nadir_x, nadir_y = bounds
    excluded = sorted(BOUNDS_EXCLUDED_ALGORITHMS)
    print(f'  Global bounds (excluding {excluded}): '
          f'{x_col} in [{ideal_x:.3g}, {nadir_x:.3g}], '
          f'{y_col} in [{ideal_y:.3g}, {nadir_y:.3g}]')

    # Recomputed quality indicators (if recompute_hv.py has been run) take
    # precedence over the Java-side HV — scenario-fixed reference point is fair
    # across SO and MO algorithms.
    quality_df = load_quality_indicators(reports_dir)
    hv_summary = compute_hv_summary(all_metrics_data, quality_df)
    if quality_df is not None:
        print(f'  Using recomputed HV_fixed from quality_indicators CSV.')

    # --- Figure 1: Pareto fronts ---
    n = len(all_pareto_data)
    fig_width = config.get('width', 18)
    fig_height = config.get('height', 7)
    fig, axes = plt.subplots(1, n, figsize=(fig_width, fig_height))
    if n == 1:
        axes = [axes]

    for i, (scenario_num, df) in enumerate(sorted(all_pareto_data.items())):
        plot_scenario_pareto(axes[i], df, scenario_num, x_col, y_col,
                             bounds, hv_summary, config)

    handles, labels = axes[0].get_legend_handles_labels()
    for ax in axes:
        lg = ax.get_legend()
        if lg:
            lg.remove()
    if config.get('show_legend', True) and handles:
        fig.legend(
            handles, labels, loc='lower center',
            ncol=min(len(labels), 4),
            bbox_to_anchor=(0.5, -0.06),
            frameon=True, fancybox=False, edgecolor='#888888',
        )

    suptitle = (f'Scenario Comparison: Pareto Fronts '
                f'({axis_label(x_col)} vs {axis_label(y_col)})')
    fig.suptitle(suptitle, y=1.03)
    caption = (
        'Multi-objective algorithms: 50% empirical attainment surface with '
        '[25%, 75%] band across 10 seeds. Single-objective algorithms: '
        'median with IQR error bars. '
        'Axes globally normalized to ideal (0,0) and nadir (1,1); '
        'raw units on the top/right axes. '
        f'HV reported as median [IQR] across all (scenario, seed) runs.'
    )
    fig.text(0.5, 0.965, caption, ha='center', fontsize=8.5, style='italic',
             color='#333333', wrap=True)
    fig.tight_layout(rect=[0, 0, 1, 0.94])

    pareto_path = os.path.join(reports_dir, 'scenario_pareto_fronts.png')
    fig.savefig(pareto_path, dpi=config.get('dpi', 300), bbox_inches='tight')
    plt.close(fig)
    print(f'  Saved: {pareto_path}')

    # --- Figure 2: Per-metric comparison (one PNG per metric) ---
    if all_metrics_data:
        plot_metrics_comparison(reports_dir, all_metrics_data, config)

    # --- Figure 3: Runtime + HV/sec efficiency ---
    plot_runtime_and_efficiency(reports_dir, all_metrics_data, quality_df, config)


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Usage: python plot_scenario_pareto.py <reports_directory>')
        sys.exit(1)

    target_dir = sys.argv[1]
    if not os.path.isdir(target_dir):
        print(f'ERROR: {target_dir} is not a directory')
        sys.exit(1)

    print(f'Processing: {target_dir}')
    process_directory(target_dir)
    print('Done.')
