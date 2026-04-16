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
    'NSGA-II':        (OKABE_ITO['bluish_green'],   'o', True,  'NSGA-II'),
    'SPEA-II':        (OKABE_ITO['reddish_purple'], '^', True,  'SPEA-II'),
    'AMOSA':          (OKABE_ITO['orange'],         'v', True,  'AMOSA'),
}

UNIVERSAL_PARETO_COLOR = OKABE_ITO['black']
UNIVERSAL_PARETO_LABEL = 'Universal Pareto'

SINGLE_OBJ_ALGORITHMS = {
    'GA_Makespan', 'GA_WaitingTime', 'GA_Energy',
    'SA_Makespan', 'SA_WaitingTime', 'SA_Energy',
}
MULTI_OBJ_ALGORITHMS = {'NSGA-II', 'SPEA-II', 'AMOSA'}

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
# PARETO FRONT PLOTTING
# =============================================================================

def plot_scenario_pareto(ax, df, scenario_num, x_col, y_col, config):
    ax.set_title(f'Scenario {scenario_num}: {SCENARIO_NAMES.get(scenario_num, "Unknown")}')
    ax.set_xlabel(axis_label(x_col))
    ax.set_ylabel(axis_label(y_col))
    ax.grid(True, which='major')

    algorithms = [a for a in df['Algorithm'].unique() if a != 'Universal_Pareto']
    marker_size = config.get('marker_size', 10)

    for algo in algorithms:
        algo_df = df[df['Algorithm'] == algo]
        points = algo_df[[x_col, y_col]].values
        color, marker, filled, display = get_style(algo)
        face = color if filled else 'none'

        if algo in SINGLE_OBJ_ALGORITHMS:
            ax.scatter(
                points[:, 0], points[:, 1],
                s=marker_size * 18, marker=marker,
                facecolors=face, edgecolors=color, linewidths=1.2,
                label=display, zorder=5,
            )
        else:
            nd_points = get_non_dominated(points)
            # Seed cloud behind the aggregated front.
            ax.scatter(
                points[:, 0], points[:, 1],
                s=marker_size * 3, marker=marker,
                facecolors=face if filled else 'none',
                edgecolors=color, linewidths=0.6,
                alpha=0.35, zorder=3,
            )
            if len(nd_points) > 1:
                ax.plot(
                    nd_points[:, 0], nd_points[:, 1],
                    color=color, linewidth=1.5, alpha=0.9,
                    label=display, zorder=4,
                )
            elif len(nd_points) == 1:
                ax.scatter(
                    nd_points[:, 0], nd_points[:, 1],
                    s=marker_size * 8, marker=marker,
                    facecolors=face, edgecolors=color, linewidths=1.0,
                    label=display, zorder=4,
                )

    univ_df = df[df['Algorithm'] == 'Universal_Pareto']
    if not univ_df.empty:
        univ_pts = univ_df[[x_col, y_col]].values
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


def plot_metrics_comparison(axes, all_metrics, config):
    metrics_to_plot = ['HV', 'GD', 'IGD']
    titles = [
        'Hypervolume (higher is better)',
        'Generational Distance (lower is better)',
        'Inverted Generational Distance (lower is better)',
    ]
    for i, (metric, title) in enumerate(zip(metrics_to_plot, titles)):
        ax = axes[i]
        ax.set_title(title)

        scenario_labels = []
        x_positions = []
        bar_width = 0.1
        pos = 0

        for scenario_num, metrics_df in sorted(all_metrics.items()):
            filtered = metrics_df[metrics_df['Algorithm'] != 'Universal_Pareto']
            algorithms = filtered['Algorithm'].values
            values = filtered[metric].values

            n = len(algorithms)
            x = np.arange(n) * bar_width + pos

            for j, (algo, val) in enumerate(zip(algorithms, values)):
                color, _, filled, display = get_style(algo)
                face = color if filled else 'white'
                label = display if pos == 0 else None
                ax.bar(
                    x[j], val, bar_width * 0.85,
                    facecolor=face, edgecolor=color, linewidth=0.8,
                    label=label, hatch=None if filled else '///',
                )

            mid = pos + (n - 1) * bar_width / 2
            scenario_labels.append(f'S{scenario_num}')
            x_positions.append(mid)
            pos += n * bar_width + bar_width * 2

        ax.set_xticks(x_positions)
        ax.set_xticklabels(scenario_labels)
        ax.grid(True, axis='y')

        if i == 0:
            ax.legend(loc='upper left', ncol=2, fontsize=7, framealpha=0.9)


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

    # --- Figure 1: Pareto fronts ---
    n = len(all_pareto_data)
    fig_width = config.get('width', 18)
    fig_height = config.get('height', 7)
    fig, axes = plt.subplots(1, n, figsize=(fig_width, fig_height))
    if n == 1:
        axes = [axes]

    for i, (scenario_num, df) in enumerate(sorted(all_pareto_data.items())):
        plot_scenario_pareto(axes[i], df, scenario_num, x_col, y_col, config)

    handles, labels = axes[0].get_legend_handles_labels()
    for ax in axes:
        lg = ax.get_legend()
        if lg:
            lg.remove()
    if config.get('show_legend', True) and handles:
        fig.legend(
            handles, labels, loc='lower center',
            ncol=min(len(labels), 8),
            bbox_to_anchor=(0.5, -0.02),
            frameon=True, fancybox=False, edgecolor='#888888',
        )

    suptitle = f'Scenario Comparison: Pareto Fronts ({axis_label(x_col)} vs {axis_label(y_col)})'
    fig.suptitle(suptitle, y=1.02)
    fig.tight_layout()

    pareto_path = os.path.join(reports_dir, 'scenario_pareto_fronts.png')
    fig.savefig(pareto_path, dpi=config.get('dpi', 300), bbox_inches='tight')
    plt.close(fig)
    print(f'  Saved: {pareto_path}')

    # --- Figure 2: Metrics comparison ---
    if all_metrics_data:
        fig2, axes2 = plt.subplots(1, 3, figsize=(fig_width, 5))
        plot_metrics_comparison(axes2, all_metrics_data, config)
        fig2.suptitle('Performance Metrics Comparison')
        fig2.tight_layout()

        metrics_path = os.path.join(reports_dir, 'metrics_comparison.png')
        fig2.savefig(metrics_path, dpi=config.get('dpi', 300), bbox_inches='tight')
        plt.close(fig2)
        print(f'  Saved: {metrics_path}')


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
