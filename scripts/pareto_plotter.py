#!/usr/bin/env python3
"""
Pareto Front Plotter for JavaCloudSimulatorCosmos

This script generates 2D Pareto front visualizations from CSV data
produced by FlexibleExperimentMain.java.

Usage:
    python pareto_plotter.py <reports_directory>

Example:
    python pareto_plotter.py reports/config_1_seed_42
    python pareto_plotter.py reports  # Processes all config folders

Requirements:
    pip install matplotlib pandas

Output:
    Generates pareto_front.png in each config folder.
"""

import matplotlib.pyplot as plt
import pandas as pd
import sys
import os
from pathlib import Path

# Algorithm colors and markers for consistent visualization
ALGORITHM_STYLES = {
    'FirstAvailable':   {'color': '#1f77b4', 'marker': 'o', 'size': 60},
    'ShortestQueue':    {'color': '#ff7f0e', 'marker': 's', 'size': 60},
    'WorkloadAware':    {'color': '#2ca02c', 'marker': '^', 'size': 70},
    'GA':               {'color': '#d62728', 'marker': 'v', 'size': 70},
    'SA':               {'color': '#9467bd', 'marker': 'D', 'size': 60},
    'LocalSearch':      {'color': '#8c564b', 'marker': 'P', 'size': 80},
    'MOEA_NSGAII':      {'color': '#e377c2', 'marker': '*', 'size': 120},
    'MOEA_SPEA2':       {'color': '#7f7f7f', 'marker': 'X', 'size': 80},
    'Universal_Pareto': {'color': '#000000', 'marker': 'o', 'size': 100},
}

def plot_pareto_front(csv_path: str, output_path: str, show_legend: bool = True) -> None:
    """
    Generate Pareto front plot from CSV data.

    Args:
        csv_path: Path to pareto_graph_data.csv
        output_path: Path for output PNG file
        show_legend: Whether to show legend (default True)
    """
    df = pd.read_csv(csv_path)

    fig, ax = plt.subplots(figsize=(12, 8))

    # Get unique algorithms (excluding Universal_Pareto for now)
    algorithms = [a for a in df['Algorithm'].unique() if a != 'Universal_Pareto']

    # Plot each algorithm's solutions
    for algo in algorithms:
        algo_data = df[df['Algorithm'] == algo]
        style = ALGORITHM_STYLES.get(algo, {'color': 'gray', 'marker': 'o', 'size': 60})

        ax.scatter(
            algo_data['Makespan'],
            algo_data['Energy'],
            c=style['color'],
            marker=style['marker'],
            s=style['size'],
            label=algo,
            alpha=0.7,
            edgecolors='black',
            linewidths=0.5
        )

    # Plot Universal Pareto as connected line with highlighted points
    universal = df[df['Algorithm'] == 'Universal_Pareto'].sort_values('Makespan')
    if not universal.empty:
        # Draw connecting line
        ax.plot(
            universal['Makespan'],
            universal['Energy'],
            'k-',
            linewidth=2,
            alpha=0.8,
            label='Universal Pareto',
            zorder=4
        )
        # Draw points
        ax.scatter(
            universal['Makespan'],
            universal['Energy'],
            c='black',
            marker='o',
            s=100,
            zorder=5,
            edgecolors='white',
            linewidths=2
        )

    # Formatting
    ax.set_xlabel('Makespan (seconds)', fontsize=12)
    ax.set_ylabel('Energy (kWh)', fontsize=12)
    ax.set_title('Multi-Algorithm Pareto Front Comparison', fontsize=14, fontweight='bold')
    ax.grid(True, alpha=0.3, linestyle='--')

    if show_legend:
        ax.legend(
            bbox_to_anchor=(1.05, 1),
            loc='upper left',
            fontsize=10,
            framealpha=0.9
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

    fig, axes = plt.subplots(1, 3, figsize=(15, 5))

    metrics = ['HV', 'GD', 'IGD']
    titles = ['Hypervolume (higher is better)', 'GD (lower is better)', 'IGD (lower is better)']
    colors = ['#2ecc71', '#e74c3c', '#3498db']

    for ax, metric, title, color in zip(axes, metrics, titles, colors):
        bars = ax.bar(df['Algorithm'], df[metric], color=color, alpha=0.7, edgecolor='black')
        ax.set_title(title, fontsize=11, fontweight='bold')
        ax.set_ylabel(metric)
        ax.tick_params(axis='x', rotation=45)

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
                fontsize=8
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
