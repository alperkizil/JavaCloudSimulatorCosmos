#!/usr/bin/env python3
"""
Pareto Front Plotting Script for Multi-Objective Optimization Results

This script creates visualizations of Pareto fronts from optimization algorithms.
It is called by the Java TaskProcessor application.

Usage:
    python3 plot_pareto.py --data <json_file> [options]

Options:
    --data          Path to JSON data file (required)
    --output        Output image file path (default: pareto_plot.png)
    --title         Plot title (default: auto-generated from data)
    --legend        Show legend: true/false (default: true)
    --labels        Show point labels: true/false (default: false)
    --marker-size   Size of markers (default: 8)
    --marker-shape  Shape of markers: circle, square, triangle, diamond (default: circle)
    --dpi           Image DPI (default: 150)
    --width         Figure width in inches (default: 12)
    --height        Figure height in inches (default: 8)
    --XMode         X Mode: true/false (default: false)
                    When enabled, single-objective points are only shown if they
                    are part of the universal Pareto set, and labels are shown
                    for single-objective points (but not multi-objective).
    --YMode         Y Mode: true/false (default: false)
                    When enabled, SO algorithm variants are grouped:
                    - SA_AvgWait, SA_Energy, SA_Makespan -> "Simulated Annealing"
                    - GA_AvgWait, GA_Energy, GA_MAKESPAN -> "Classic GA"
                    - GA_ISL_* variants -> "Island Model GA"
                    Groups are plotted with lines like multi-objective algorithms.
"""

import argparse
import json
import sys
import matplotlib.pyplot as plt
import matplotlib.markers as mmarkers
import numpy as np
from typing import Dict, List, Tuple, Any

# Algorithm color mapping
ALGORITHM_COLORS = {
    'MOEA_AMOSA': '#228B22',      # Green (Forest Green)
    'AMOSA': '#228B22',
    'MOEA_SPEAII': '#0000FF',     # Blue
    'SPEAII': '#0000FF',
    'MOEA_NSGAII': '#FFD700',     # Yellow (Gold)
    'NSGAII': '#FFD700',
    'MOEA_eNSGAII': '#800080',    # Purple
    'MOEA_eNSGA2': '#800080',
    'eNSGAII': '#800080',
    'eNSGA2': '#800080',
    'Universal_Pareto': '#FF0000', # Red
    'Universal Pareto Set': '#FF0000',
    # Ymode grouped algorithms (distinct colors for combined SO groups)
    'Simulated Annealing': '#FF6600',   # Orange
    'Classic GA': '#00CED1',             # Dark Turquoise
    'Island Model GA': '#9932CC',        # Dark Orchid (distinct purple)
}

# Default color for single-objective algorithms
SINGLE_OBJECTIVE_COLOR = '#000000'  # Black

# Marker shape mapping
MARKER_SHAPES = {
    'circle': 'o',
    'square': 's',
    'triangle': '^',
    'diamond': 'D',
    'star': '*',
    'plus': '+',
    'x': 'x',
}


# Ymode grouped algorithm names (these get treated like multi-objective for plotting)
YMODE_GROUP_NAMES = ['Simulated Annealing', 'Classic GA', 'Island Model GA']


def get_algorithm_color(algo_name: str) -> str:
    """Get color for an algorithm, defaulting to black for single-objective."""
    # Check exact match first
    if algo_name in ALGORITHM_COLORS:
        return ALGORITHM_COLORS[algo_name]

    # Check if it's a multi-objective algorithm by partial match
    for key, color in ALGORITHM_COLORS.items():
        if key in algo_name or algo_name in key:
            return color

    # Single-objective algorithms get black
    if algo_name.startswith('SO_'):
        return SINGLE_OBJECTIVE_COLOR

    # Default fallback
    return SINGLE_OBJECTIVE_COLOR


def is_ymode_group(algo_name: str) -> bool:
    """Check if algorithm is a Ymode grouped algorithm."""
    return algo_name in YMODE_GROUP_NAMES


def get_algorithm_display_name(algo_name: str) -> str:
    """Get display name for algorithm legend."""
    display_names = {
        'MOEA_AMOSA': 'AMOSA',
        'MOEA_SPEAII': 'SPEAII',
        'MOEA_NSGAII': 'NSGA-II',
        'MOEA_eNSGAII': 'ε-NSGA-II',
        'MOEA_eNSGA2': 'ε-NSGA-II',
        'Universal_Pareto': 'Universal Pareto Set',
    }

    if algo_name in display_names:
        return display_names[algo_name]

    # For single-objective, remove SO_ prefix
    if algo_name.startswith('SO_'):
        return algo_name[3:]

    return algo_name


def sort_pareto_front(points: List[List[float]]) -> List[List[float]]:
    """Sort Pareto front points by first objective for line drawing."""
    if not points:
        return points
    return sorted(points, key=lambda p: p[0])


def is_point_in_pareto(point: List[float], pareto_set: List[List[float]], tolerance: float = 1e-9) -> bool:
    """Check if a point is in the universal Pareto set."""
    for p in pareto_set:
        if abs(point[0] - p[0]) < tolerance and abs(point[1] - p[1]) < tolerance:
            return True
    return False


def filter_points_in_pareto(points: List[List[float]], pareto_set: List[List[float]]) -> List[List[float]]:
    """Filter points to only include those in the universal Pareto set."""
    return [p for p in points if is_point_in_pareto(p, pareto_set)]


def plot_pareto_fronts(data: Dict[str, Any], args: argparse.Namespace) -> None:
    """Create the Pareto front plot."""

    # Extract data
    algorithms = data.get('algorithms', {})
    universal_pareto = data.get('universal_pareto', [])
    objective1_name = data.get('objective1', 'Objective 1')
    objective2_name = data.get('objective2', 'Objective 2')
    num_tasks = data.get('num_tasks', '')

    # Create figure
    fig, ax = plt.subplots(figsize=(args.width, args.height))

    # Set title
    if args.title:
        title = args.title
    else:
        title = f"{num_tasks} Tasks - {objective1_name} vs {objective2_name}"
    ax.set_title(title, fontsize=14, fontweight='bold')

    # Get marker shape
    marker = MARKER_SHAPES.get(args.marker_shape, 'o')
    marker_size = args.marker_size

    # Track plotted algorithms for legend
    legend_handles = []
    legend_labels = []

    # Define plot order: multi-objective first, then Ymode groups, then single-objective
    # Ymode grouped algorithms are treated like multi-objective (with lines)
    mo_algorithms = [a for a in algorithms.keys() if not a.startswith('SO_') and not is_ymode_group(a)]
    ymode_algorithms = [a for a in algorithms.keys() if is_ymode_group(a)]
    so_algorithms = [a for a in algorithms.keys() if a.startswith('SO_')]

    # Sort multi-objective algorithms in specific order
    mo_order = ['MOEA_AMOSA', 'MOEA_NSGAII', 'MOEA_SPEAII', 'MOEA_eNSGAII', 'MOEA_eNSGA2']
    mo_algorithms_sorted = []
    for algo in mo_order:
        if algo in mo_algorithms:
            mo_algorithms_sorted.append(algo)
    # Add any remaining
    for algo in mo_algorithms:
        if algo not in mo_algorithms_sorted:
            mo_algorithms_sorted.append(algo)

    # Plot multi-objective algorithms
    for algo_name in mo_algorithms_sorted:
        algo_data = algorithms[algo_name]
        points = algo_data.get('non_dominated', [])

        if not points:
            continue

        color = get_algorithm_color(algo_name)
        display_name = get_algorithm_display_name(algo_name)

        # Sort points for line drawing
        sorted_points = sort_pareto_front(points)
        x_vals = [p[0] for p in sorted_points]
        y_vals = [p[1] for p in sorted_points]

        # Plot line connecting points
        line, = ax.plot(x_vals, y_vals, color=color, linewidth=1.5, zorder=1)

        # Plot points
        scatter = ax.scatter(x_vals, y_vals, c=color, s=marker_size**2,
                           marker=marker, edgecolors='white', linewidths=0.5, zorder=2)

        # Add to legend
        legend_handles.append(scatter)
        legend_labels.append(display_name)

        # Add point labels if enabled (but not in XMode for multi-objective)
        if args.labels and not args.XMode:
            for x, y in zip(x_vals, y_vals):
                ax.annotate(display_name, (x, y), textcoords="offset points",
                          xytext=(5, 5), fontsize=6, alpha=0.7)

    # Plot Ymode grouped algorithms (treated like multi-objective with lines)
    for algo_name in ymode_algorithms:
        algo_data = algorithms[algo_name]
        points = algo_data.get('non_dominated', [])

        if not points:
            continue

        color = get_algorithm_color(algo_name)
        display_name = algo_name  # Use the group name as-is

        # Sort points for line drawing
        sorted_points = sort_pareto_front(points)
        x_vals = [p[0] for p in sorted_points]
        y_vals = [p[1] for p in sorted_points]

        # Plot line connecting points
        line, = ax.plot(x_vals, y_vals, color=color, linewidth=1.5, zorder=1)

        # Plot points
        scatter = ax.scatter(x_vals, y_vals, c=color, s=marker_size**2,
                           marker=marker, edgecolors='white', linewidths=0.5, zorder=2)

        # Add to legend
        legend_handles.append(scatter)
        legend_labels.append(display_name)

        # Add point labels if enabled
        if args.labels:
            for x, y in zip(x_vals, y_vals):
                ax.annotate(display_name, (x, y), textcoords="offset points",
                          xytext=(5, 5), fontsize=6, alpha=0.7)

    # Plot single-objective algorithms (if any)
    if so_algorithms:
        so_x_vals = []
        so_y_vals = []
        so_point_labels = []  # Track labels for each point

        for algo_name in so_algorithms:
            algo_data = algorithms[algo_name]
            points = algo_data.get('non_dominated', [])
            display_name = get_algorithm_display_name(algo_name)

            # In XMode, only include points that are in the universal Pareto set
            if args.XMode:
                points = filter_points_in_pareto(points, universal_pareto)

            for p in points:
                so_x_vals.append(p[0])
                so_y_vals.append(p[1])
                so_point_labels.append(display_name)

        if so_x_vals:
            scatter = ax.scatter(so_x_vals, so_y_vals, c=SINGLE_OBJECTIVE_COLOR,
                               s=marker_size**2, marker=marker,
                               edgecolors='white', linewidths=0.5, zorder=2)
            legend_handles.append(scatter)
            legend_labels.append('Single-Objective')

            # Add labels if enabled OR if XMode is on (labels always on for SO in XMode)
            if args.labels or args.XMode:
                for x, y, label in zip(so_x_vals, so_y_vals, so_point_labels):
                    ax.annotate(label, (x, y),
                              textcoords="offset points", xytext=(5, 5),
                              fontsize=6, alpha=0.7)

    # Plot Universal Pareto Set
    if universal_pareto:
        sorted_universal = sort_pareto_front(universal_pareto)
        x_vals = [p[0] for p in sorted_universal]
        y_vals = [p[1] for p in sorted_universal]

        # Plot line
        ax.plot(x_vals, y_vals, color=ALGORITHM_COLORS['Universal_Pareto'],
               linewidth=2, zorder=3)

        # Plot points with special marker (X)
        scatter = ax.scatter(x_vals, y_vals, c=ALGORITHM_COLORS['Universal_Pareto'],
                           s=(marker_size+2)**2, marker='X',
                           edgecolors='darkred', linewidths=0.5, zorder=4)

        legend_handles.append(scatter)
        legend_labels.append('Universal Pareto Set')

    # Set axis labels
    ax.set_xlabel(f"{objective1_name}", fontsize=12)
    ax.set_ylabel(f"{objective2_name}", fontsize=12)

    # Add grid
    ax.grid(True, linestyle='--', alpha=0.3)

    # Add legend
    if args.legend:
        ax.legend(legend_handles, legend_labels, loc='upper right',
                 framealpha=0.9, fontsize=10)

    # Adjust layout
    plt.tight_layout()

    # Save figure
    plt.savefig(args.output, dpi=args.dpi, bbox_inches='tight',
                facecolor='white', edgecolor='none')
    plt.close()

    print(f"Plot saved to: {args.output}")


def main():
    parser = argparse.ArgumentParser(
        description='Create Pareto front visualization from optimization results'
    )

    parser.add_argument('--data', required=True,
                       help='Path to JSON data file')
    parser.add_argument('--output', default='pareto_plot.png',
                       help='Output image file path')
    parser.add_argument('--title', default=None,
                       help='Plot title (auto-generated if not specified)')
    parser.add_argument('--legend', type=str, default='true',
                       help='Show legend: true/false')
    parser.add_argument('--labels', type=str, default='false',
                       help='Show point labels: true/false')
    parser.add_argument('--marker-size', type=int, default=8,
                       help='Size of markers')
    parser.add_argument('--marker-shape', default='circle',
                       choices=['circle', 'square', 'triangle', 'diamond', 'star', 'plus', 'x'],
                       help='Shape of markers')
    parser.add_argument('--dpi', type=int, default=150,
                       help='Image DPI')
    parser.add_argument('--width', type=float, default=12,
                       help='Figure width in inches')
    parser.add_argument('--height', type=float, default=8,
                       help='Figure height in inches')
    parser.add_argument('--XMode', type=str, default='false',
                       help='X Mode: true/false - Single-objective points only shown if in universal Pareto, with labels on')
    parser.add_argument('--YMode', type=str, default='false',
                       help='Y Mode: true/false - SO algorithm variants grouped into combined Pareto fronts')

    args = parser.parse_args()

    # Convert string booleans
    args.legend = args.legend.lower() == 'true'
    args.labels = args.labels.lower() == 'true'
    args.XMode = args.XMode.lower() == 'true'
    args.YMode = args.YMode.lower() == 'true'

    # Load data
    try:
        with open(args.data, 'r') as f:
            data = json.load(f)
    except FileNotFoundError:
        print(f"Error: Data file not found: {args.data}", file=sys.stderr)
        sys.exit(1)
    except json.JSONDecodeError as e:
        print(f"Error: Invalid JSON in data file: {e}", file=sys.stderr)
        sys.exit(1)

    # Create plot
    plot_pareto_fronts(data, args)


if __name__ == '__main__':
    main()
