#!/usr/bin/env python3
"""
Scenario Comparison Pareto Front Plotter for JavaCloudSimulatorCosmos

Generates 2D Pareto front visualizations from scenario comparison CSV data
produced by ScenarioComparisonExperimentRunner.java.

Features:
    - 3 scenario subplots (Balanced, GPU Stress, CPU Stress)
    - Distinct colors per algorithm
    - Single-obj points: larger markers with text labels
    - Multi-obj fronts: connected scatter lines
    - Universal Pareto: red dashed overlay
    - Metrics comparison bar charts

Usage:
    python plot_scenario_pareto.py <reports_directory>

Example:
    python plot_scenario_pareto.py reports/scenario_comparison
"""

import matplotlib.pyplot as plt
import pandas as pd
import numpy as np
import sys
import os
import json
from pathlib import Path

# =============================================================================
# CONFIGURATION
# =============================================================================

DEFAULT_CONFIG = {
    'dpi': 300,
    'width': 18,
    'height': 7,
    'marker_size': 10,
    'show_legend': True,
    'show_labels': True,
    'scenarios': 3,
}

ALGORITHM_COLORS = {
    'GA_Makespan':      '#9467bd',   # Purple
    'GA_Energy':        '#9932CC',   # Dark orchid
    'SA_Makespan':      '#8c564b',   # Brown
    'SA_Energy':        '#D2691E',   # Chocolate
    'NSGA-II':          '#17becf',   # Cyan
    'SPEA-II':          '#bcbd22',   # Yellow-green
    'AMOSA':            '#2ca02c',   # Green
}

UNIVERSAL_PARETO_COLOR = '#FF0000'

ALGORITHM_DISPLAY_NAMES = {
    'GA_Makespan':      'GA (Makespan)',
    'GA_Energy':        'GA (Energy)',
    'SA_Makespan':      'SA (Makespan)',
    'SA_Energy':        'SA (Energy)',
    'NSGA-II':          'NSGA-II',
    'SPEA-II':          'SPEA-II',
    'AMOSA':            'AMOSA',
    'Universal_Pareto': 'Universal Pareto',
}

ALGORITHM_MARKERS = {
    'GA_Makespan':  'D',   # Diamond
    'GA_Energy':    'D',
    'SA_Makespan':  's',   # Square
    'SA_Energy':    's',
    'NSGA-II':      'o',   # Circle
    'SPEA-II':      '^',   # Triangle
    'AMOSA':        'v',   # Inverted triangle
}

# Single-objective algorithms get larger markers
SINGLE_OBJ_ALGORITHMS = {'GA_Makespan', 'GA_Energy', 'SA_Makespan', 'SA_Energy'}
MULTI_OBJ_ALGORITHMS = {'NSGA-II', 'SPEA-II', 'AMOSA'}

SCENARIO_NAMES = {1: 'Balanced', 2: 'GPU Stress', 3: 'CPU Stress'}


# =============================================================================
# NON-DOMINANCE FILTERING
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
# CONFIG LOADING
# =============================================================================

def load_config(reports_dir):
    config = dict(DEFAULT_CONFIG)
    json_path = os.path.join(reports_dir, 'plot_options.json')
    if os.path.exists(json_path):
        with open(json_path) as f:
            config.update(json.load(f))
    return config


def get_color(algo):
    if algo == 'Universal_Pareto':
        return UNIVERSAL_PARETO_COLOR
    return ALGORITHM_COLORS.get(algo, '#000000')


def get_display_name(algo):
    return ALGORITHM_DISPLAY_NAMES.get(algo, algo)


def get_marker(algo):
    return ALGORITHM_MARKERS.get(algo, 'o')


# =============================================================================
# PARETO FRONT PLOTTING
# =============================================================================

def plot_scenario_pareto(ax, df, scenario_num, config):
    """Plot a single scenario's Pareto front on the given axes."""
    ax.set_title(
        f'Scenario {scenario_num}: {SCENARIO_NAMES.get(scenario_num, "Unknown")}',
        fontsize=13, fontweight='bold'
    )
    ax.set_xlabel('Makespan (seconds)', fontsize=11)
    ax.set_ylabel('Energy (kWh)', fontsize=11)
    ax.grid(True, alpha=0.3)

    algorithms = [a for a in df['Algorithm'].unique() if a != 'Universal_Pareto']
    marker_size = config.get('marker_size', 10)

    for algo in algorithms:
        algo_df = df[df['Algorithm'] == algo]
        points = algo_df[['Makespan', 'Energy']].values
        color = get_color(algo)
        marker = get_marker(algo)
        display = get_display_name(algo)

        if algo in SINGLE_OBJ_ALGORITHMS:
            # Single-obj: larger markers with text label
            ax.scatter(
                points[:, 0], points[:, 1],
                c=color, marker=marker, s=marker_size * 20,
                label=display, zorder=5, edgecolors='black', linewidths=0.8
            )
            if config.get('show_labels', True):
                obj_label = '(M)' if 'Makespan' in algo else '(E)'
                for pt in points:
                    ax.annotate(
                        f'{display} {obj_label}',
                        (pt[0], pt[1]),
                        textcoords='offset points',
                        xytext=(8, 5), fontsize=7, color=color,
                        fontweight='bold'
                    )
        else:
            # Multi-obj: scatter + connected front line
            nd_points = get_non_dominated(points)
            ax.scatter(
                points[:, 0], points[:, 1],
                c=color, marker=marker, s=marker_size * 5,
                alpha=0.6, zorder=3
            )
            if len(nd_points) > 1:
                ax.plot(
                    nd_points[:, 0], nd_points[:, 1],
                    c=color, linewidth=1.5, alpha=0.8,
                    label=display, zorder=4
                )
            elif len(nd_points) == 1:
                ax.scatter(
                    nd_points[:, 0], nd_points[:, 1],
                    c=color, marker=marker, s=marker_size * 8,
                    label=display, zorder=4, edgecolors='black', linewidths=0.5
                )

    # Universal Pareto front: red dashed overlay
    univ_df = df[df['Algorithm'] == 'Universal_Pareto']
    if not univ_df.empty:
        univ_pts = univ_df[['Makespan', 'Energy']].values
        univ_sorted = univ_pts[np.argsort(univ_pts[:, 0])]
        ax.plot(
            univ_sorted[:, 0], univ_sorted[:, 1],
            c=UNIVERSAL_PARETO_COLOR, linewidth=2.0, linestyle='--',
            alpha=0.7, label='Universal Pareto', zorder=6
        )
        ax.scatter(
            univ_sorted[:, 0], univ_sorted[:, 1],
            c=UNIVERSAL_PARETO_COLOR, marker='x', s=marker_size * 3,
            alpha=0.5, zorder=6
        )


def plot_metrics_comparison(axes, all_metrics, config):
    """Plot bar charts comparing HV, GD, IGD across scenarios."""
    metrics_to_plot = ['HV', 'GD', 'IGD']
    titles = ['Hypervolume (higher is better)', 'GD (lower is better)', 'IGD (lower is better)']

    for i, (metric, title) in enumerate(zip(metrics_to_plot, titles)):
        ax = axes[i]
        ax.set_title(title, fontsize=12, fontweight='bold')

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
                color = get_color(algo)
                label = get_display_name(algo) if pos == 0 else None
                ax.bar(x[j], val, bar_width * 0.8, color=color, label=label,
                       edgecolor='black', linewidth=0.3)

            mid = pos + (n - 1) * bar_width / 2
            scenario_labels.append(f'S{scenario_num}')
            x_positions.append(mid)
            pos += n * bar_width + bar_width * 2

        ax.set_xticks(x_positions)
        ax.set_xticklabels(scenario_labels, fontsize=10)
        ax.grid(True, alpha=0.3, axis='y')

        if i == 0:
            ax.legend(fontsize=7, loc='upper left', ncol=2)


# =============================================================================
# MAIN PROCESSING
# =============================================================================

def process_directory(reports_dir):
    """Process all scenario CSVs in the given directory."""
    config = load_config(reports_dir)
    num_scenarios = config.get('scenarios', 3)

    # Load all scenario data
    all_pareto_data = {}
    all_metrics_data = {}

    for s in range(1, num_scenarios + 1):
        pareto_file = os.path.join(reports_dir, f'scenario_{s}_pareto_graph_data.csv')
        metrics_file = os.path.join(reports_dir, f'scenario_{s}_performance_metrics.csv')

        if os.path.exists(pareto_file):
            all_pareto_data[s] = pd.read_csv(pareto_file)
            print(f'  Loaded: scenario_{s}_pareto_graph_data.csv')
        else:
            print(f'  WARNING: {pareto_file} not found')

        if os.path.exists(metrics_file):
            all_metrics_data[s] = pd.read_csv(metrics_file)
            print(f'  Loaded: scenario_{s}_performance_metrics.csv')

    if not all_pareto_data:
        print('ERROR: No scenario data found.')
        return

    # --- Figure 1: Pareto fronts (1 row x N scenarios) ---
    n = len(all_pareto_data)
    fig_width = config.get('width', 18)
    fig_height = config.get('height', 7)
    fig, axes = plt.subplots(1, n, figsize=(fig_width, fig_height))
    if n == 1:
        axes = [axes]

    for i, (scenario_num, df) in enumerate(sorted(all_pareto_data.items())):
        plot_scenario_pareto(axes[i], df, scenario_num, config)

    # Shared legend below
    handles, labels = axes[0].get_legend_handles_labels()
    if config.get('show_legend', True) and handles:
        fig.legend(handles, labels, loc='lower center', ncol=min(len(labels), 8),
                   fontsize=9, bbox_to_anchor=(0.5, -0.02))

    # Remove per-axis legends
    for ax in axes:
        legend = ax.get_legend()
        if legend:
            legend.remove()

    fig.suptitle('Scenario Comparison: Pareto Fronts', fontsize=15, fontweight='bold', y=1.02)
    fig.tight_layout()

    pareto_path = os.path.join(reports_dir, 'scenario_pareto_fronts.png')
    fig.savefig(pareto_path, dpi=config.get('dpi', 300), bbox_inches='tight')
    plt.close(fig)
    print(f'  Saved: {pareto_path}')

    # --- Figure 2: Metrics comparison ---
    if all_metrics_data:
        fig2, axes2 = plt.subplots(1, 3, figsize=(fig_width, 5))
        plot_metrics_comparison(axes2, all_metrics_data, config)
        fig2.suptitle('Performance Metrics Comparison', fontsize=15, fontweight='bold')
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
