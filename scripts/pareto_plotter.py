#!/usr/bin/env python3
"""
Pareto Front Plotter for JavaCloudSimulatorCosmos

This script generates 2D Pareto front visualizations from CSV data
produced by FlexibleExperimentMain.java.

Features:
    - Distinct colors for each algorithm
    - Per-algorithm Pareto lines connecting non-dominated points
    - All non-dominated points displayed for every algorithm
    - Universal Pareto front overlay

Usage:
    python pareto_plotter.py <reports_directory>

Example:
    python pareto_plotter.py reports/config_1_seed_42
    python pareto_plotter.py reports  # Processes all config folders

Requirements:
    pip install matplotlib pandas
"""

import matplotlib.pyplot as plt
import pandas as pd
import numpy as np
import sys
import os
from pathlib import Path

# Algorithm colors and markers for consistent visualization
# Each algorithm has a distinct color, marker, and size
ALGORITHM_STYLES = {
    'FirstAvailable':   {'color': '#1f77b4', 'marker': 'o', 'size': 80},   # Blue
    'ShortestQueue':    {'color': '#ff7f0e', 'marker': 's', 'size': 80},   # Orange
    'WorkloadAware':    {'color': '#2ca02c', 'marker': '^', 'size': 90},   # Green
    'GA':               {'color': '#d62728', 'marker': 'v', 'size': 90},   # Red
    'SA':               {'color': '#9467bd', 'marker': 'D', 'size': 80},   # Purple
    'LocalSearch':      {'color': '#8c564b', 'marker': 'P', 'size': 100},  # Brown
    'MOEA_NSGAII':      {'color': '#e377c2', 'marker': '*', 'size': 150},  # Pink
    'MOEA_SPEA2':       {'color': '#7f7f7f', 'marker': 'X', 'size': 100},  # Gray
    'Universal_Pareto': {'color': '#000000', 'marker': 'o', 'size': 120},  # Black
}


def is_dominated(point, other_points):
    """
    Check if a point is dominated by any other point.
    A point is dominated if another point is at least as good in all objectives
    and strictly better in at least one.
    Both objectives are minimization (lower is better).
    """
    for other in other_points:
        if other[0] == point[0] and other[1] == point[1]:
            continue  # Skip same point
        # other dominates point if: other <= point in all and other < point in at least one
        if other[0] <= point[0] and other[1] <= point[1]:
            if other[0] < point[0] or other[1] < point[1]:
                return True
    return False


def get_non_dominated(points):
    """
    Filter points to return only non-dominated solutions.
    Returns points sorted by first objective (makespan).
    """
    if len(points) == 0:
        return np.array([])

    points = np.array(points)
    non_dominated = []

    for i, point in enumerate(points):
        if not is_dominated(point, points):
            # Check for duplicates
            is_duplicate = False
            for nd in non_dominated:
                if abs(nd[0] - point[0]) < 1e-9 and abs(nd[1] - point[1]) < 1e-9:
                    is_duplicate = True
                    break
            if not is_duplicate:
                non_dominated.append(point)

    if len(non_dominated) == 0:
        return np.array([])

    # Sort by first objective (makespan)
    non_dominated = np.array(non_dominated)
    sorted_indices = np.argsort(non_dominated[:, 0])
    return non_dominated[sorted_indices]


def plot_pareto_front(csv_path: str, output_path: str, show_legend: bool = True) -> None:
    """
    Generate Pareto front plot from CSV data.

    Features:
    - Each algorithm has distinct color and marker
    - Each algorithm's non-dominated points are connected with a Pareto line
    - Universal Pareto front is shown as a thick black line

    Args:
        csv_path: Path to pareto_graph_data.csv
        output_path: Path for output PNG file
        show_legend: Whether to show legend (default True)
    """
    df = pd.read_csv(csv_path)

    fig, ax = plt.subplots(figsize=(14, 10))

    # Get unique algorithms (excluding Universal_Pareto for now)
    algorithms = [a for a in df['Algorithm'].unique() if a != 'Universal_Pareto']

    # Plot each algorithm's solutions with their Pareto line
    for algo in algorithms:
        algo_data = df[df['Algorithm'] == algo]
        style = ALGORITHM_STYLES.get(algo, {'color': 'gray', 'marker': 'o', 'size': 80})

        # Get all points for this algorithm
        points = algo_data[['Makespan', 'Energy']].values

        # Get non-dominated points for this algorithm
        non_dominated = get_non_dominated(points)

        # Plot all non-dominated points
        if len(non_dominated) > 0:
            ax.scatter(
                non_dominated[:, 0],
                non_dominated[:, 1],
                c=style['color'],
                marker=style['marker'],
                s=style['size'],
                label=algo,
                alpha=0.8,
                edgecolors='black',
                linewidths=0.5,
                zorder=3
            )

            # Draw Pareto line connecting non-dominated points for this algorithm
            if len(non_dominated) > 1:
                ax.plot(
                    non_dominated[:, 0],
                    non_dominated[:, 1],
                    color=style['color'],
                    linewidth=1.5,
                    alpha=0.6,
                    linestyle='-',
                    zorder=2
                )

    # Plot Universal Pareto as thick black connected line
    universal = df[df['Algorithm'] == 'Universal_Pareto'].sort_values('Makespan')
    if not universal.empty:
        # Draw thick connecting line
        ax.plot(
            universal['Makespan'],
            universal['Energy'],
            'k-',
            linewidth=3,
            alpha=0.9,
            label='Universal Pareto',
            zorder=4
        )
        # Draw prominent points
        ax.scatter(
            universal['Makespan'],
            universal['Energy'],
            c='black',
            marker='o',
            s=120,
            zorder=5,
            edgecolors='white',
            linewidths=2
        )

    # Formatting
    ax.set_xlabel('Makespan (seconds)', fontsize=14)
    ax.set_ylabel('Energy (kWh)', fontsize=14)
    ax.set_title('Multi-Algorithm Pareto Front Comparison', fontsize=16, fontweight='bold')
    ax.grid(True, alpha=0.3, linestyle='--')

    # Improve tick label size
    ax.tick_params(axis='both', which='major', labelsize=11)

    if show_legend:
        ax.legend(
            bbox_to_anchor=(1.05, 1),
            loc='upper left',
            fontsize=11,
            framealpha=0.95,
            edgecolor='black'
        )

    plt.tight_layout()
    plt.savefig(output_path, dpi=300, bbox_inches='tight', facecolor='white')
    plt.close()

    print(f'Generated: {output_path}')


def plot_metrics_comparison(csv_path: str, output_path: str) -> None:
    """
    Generate bar chart comparing performance metrics across algorithms.

    Args:
        csv_path: Path to performance_metrics.csv
        output_path: Path for output PNG file
    """
    df = pd.read_csv(csv_path)

    # Filter out Universal_Pareto for comparison
    df = df[df['Algorithm'] != 'Universal_Pareto']

    fig, axes = plt.subplots(1, 3, figsize=(16, 6))

    metrics = ['HV', 'GD', 'IGD']
    titles = ['Hypervolume (higher is better)', 'GD (lower is better)', 'IGD (lower is better)']

    for ax, metric, title in zip(axes, metrics, titles):
        # Get colors from ALGORITHM_STYLES
        colors = [ALGORITHM_STYLES.get(algo, {'color': 'gray'})['color'] for algo in df['Algorithm']]

        bars = ax.bar(df['Algorithm'], df[metric], color=colors, alpha=0.8, edgecolor='black')
        ax.set_title(title, fontsize=12, fontweight='bold')
        ax.set_ylabel(metric, fontsize=11)
        ax.tick_params(axis='x', rotation=45, labelsize=9)

        # Add value labels on bars
        for bar, val in zip(bars, df[metric]):
            height = bar.get_height()
            ax.annotate(
                f'{val:.4f}',
                xy=(bar.get_x() + bar.get_width() / 2, height),
                xytext=(0, 3),
                textcoords="offset points",
                ha='center',
                va='bottom',
                fontsize=8,
                fontweight='bold'
            )

    plt.tight_layout()
    plt.savefig(output_path, dpi=300, bbox_inches='tight', facecolor='white')
    plt.close()

    print(f'Generated: {output_path}')


def process_directory(target_dir: str) -> int:
    """
    Process a directory containing experiment results.

    Args:
        target_dir: Path to directory containing CSV files

    Returns:
        Number of plots generated
    """
    target_path = Path(target_dir)
    plots_generated = 0

    # Check if this directory contains pareto_graph_data.csv
    pareto_csv = target_path / 'pareto_graph_data.csv'
    metrics_csv = target_path / 'performance_metrics.csv'

    if pareto_csv.exists():
        output = target_path / 'pareto_front.png'
        plot_pareto_front(str(pareto_csv), str(output))
        plots_generated += 1

        if metrics_csv.exists():
            metrics_output = target_path / 'metrics_comparison.png'
            plot_metrics_comparison(str(metrics_csv), str(metrics_output))
            plots_generated += 1
    else:
        # Check subdirectories
        for folder in target_path.iterdir():
            if folder.is_dir():
                pareto_csv = folder / 'pareto_graph_data.csv'
                metrics_csv = folder / 'performance_metrics.csv'

                if pareto_csv.exists():
                    output = folder / 'pareto_front.png'
                    plot_pareto_front(str(pareto_csv), str(output))
                    plots_generated += 1

                    if metrics_csv.exists():
                        metrics_output = folder / 'metrics_comparison.png'
                        plot_metrics_comparison(str(metrics_csv), str(metrics_output))
                        plots_generated += 1

    return plots_generated


def main():
    """Main entry point."""
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    target = sys.argv[1]

    if os.path.isfile(target) and target.endswith('.csv'):
        # Single CSV file
        if 'pareto_graph_data' in target:
            output = target.replace('.csv', '_pareto.png')
            plot_pareto_front(target, output)
        elif 'performance_metrics' in target:
            output = target.replace('.csv', '_comparison.png')
            plot_metrics_comparison(target, output)
        else:
            print(f"Unknown CSV format: {target}")
            print("Expected: pareto_graph_data.csv or performance_metrics.csv")
            sys.exit(1)

    elif os.path.isdir(target):
        plots = process_directory(target)
        if plots == 0:
            print(f"No pareto_graph_data.csv files found in {target}")
            sys.exit(1)
        print(f"\nTotal plots generated: {plots}")
    else:
        print(f"Error: {target} is not a valid file or directory")
        sys.exit(1)


if __name__ == '__main__':
    main()
