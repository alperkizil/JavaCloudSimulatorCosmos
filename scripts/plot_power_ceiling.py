#!/usr/bin/env python3
"""
Power-ceiling feasibility + 3D Pareto plotter.

Consumes the three CSVs written by PowerCeilingReporter:
    feasibility_summary.csv
    pareto_3d_feasible.csv
    pareto_3d_all.csv

Plus the existing power_ceiling_feasibility.csv (per-seed).

Produces, per scenario:
  * power_ceiling_feasibility_bars_scenario_<N>.png
      Stacked feasibility-rate bars per algorithm, one bar per cap level
      (errorbars = std across seeds).
  * power_ceiling_3d_projection_scenario_<N>.png
      3D scatter of (waiting time, energy, peak power) with points coloured
      by feasibility under the primary cap; red dashed plane = cap.
  * power_ceiling_constrained_front_scenario_<N>_cap<X>kW.png
      2D Pareto scatter (wt vs energy) restricted to feasible points at a
      single cap, one curve per algorithm.

Usage:
    python plot_power_ceiling.py <reports_directory>
"""

import os
import sys
import json

import matplotlib.pyplot as plt
import matplotlib as mpl
import numpy as np
import pandas as pd
from matplotlib.lines import Line2D

# -----------------------------------------------------------------------------
# Styling — matches plot_scenario_pareto.py
# -----------------------------------------------------------------------------

mpl.rcParams.update({
    'font.family': 'serif',
    'font.serif': ['STIX Two Text', 'STIX', 'DejaVu Serif', 'Times New Roman', 'serif'],
    'mathtext.fontset': 'stix',
    'axes.linewidth': 0.6,
    'axes.spines.top': False,
    'axes.spines.right': False,
    'axes.labelsize': 10,
    'axes.titlesize': 11,
    'xtick.labelsize': 9,
    'ytick.labelsize': 9,
    'legend.fontsize': 8,
    'figure.dpi': 120,
})

# Okabe–Ito + extensions for many algorithms
PALETTE = [
    '#0072B2', '#E69F00', '#009E73', '#CC79A7',
    '#56B4E9', '#D55E00', '#F0E442', '#000000',
    '#8E44AD', '#16A085', '#C0392B', '#2C3E50',
    '#F39C12', '#27AE60', '#7F8C8D', '#E74C3C',
    '#3498DB', '#9B59B6', '#1ABC9C', '#D35400',
    '#34495E', '#BDC3C7',
]


def _algo_color(algo, algos_sorted):
    idx = algos_sorted.index(algo) if algo in algos_sorted else 0
    return PALETTE[idx % len(PALETTE)]


# -----------------------------------------------------------------------------
# Stacked feasibility bars
# -----------------------------------------------------------------------------

def plot_feasibility_bars(reports_dir, summary_df):
    for scenario, sub in summary_df.groupby('Scenario'):
        scenario_name = sub['ScenarioName'].iloc[0]
        caps = sorted(sub['CapWatts'].unique())
        algos = sorted(sub['Algorithm'].unique())

        fig, ax = plt.subplots(figsize=(max(10, 0.5 * len(algos)), 5))
        bar_width = 0.8 / max(1, len(caps))
        x = np.arange(len(algos))

        for ci, cap in enumerate(caps):
            means = []
            stds = []
            for algo in algos:
                row = sub[(sub['Algorithm'] == algo) & (sub['CapWatts'] == cap)]
                if row.empty:
                    means.append(0.0)
                    stds.append(0.0)
                else:
                    means.append(float(row['MeanFeasibilityRate'].iloc[0]))
                    stds.append(float(row['StdFeasibilityRate'].iloc[0]))
            offset = (ci - (len(caps) - 1) / 2.0) * bar_width
            ax.bar(
                x + offset, means, bar_width, yerr=stds,
                label=f'{int(cap/1000)} kW',
                capsize=2, edgecolor='black', linewidth=0.3,
            )

        ax.set_xticks(x)
        ax.set_xticklabels(algos, rotation=45, ha='right', fontsize=8)
        ax.set_ylabel('Feasibility rate (mean ± std across seeds)')
        ax.set_ylim(0.0, 1.05)
        ax.set_title(f'Scenario {scenario} — {scenario_name}: feasibility by cap')
        ax.legend(title='P_cap', loc='upper right', framealpha=0.9)
        ax.grid(True, axis='y', linestyle=':', alpha=0.4)

        fig.tight_layout()
        out = os.path.join(reports_dir, f'power_ceiling_feasibility_bars_scenario_{scenario}.png')
        fig.savefig(out, dpi=300, bbox_inches='tight')
        plt.close(fig)
        print(f'  Wrote: {os.path.basename(out)}')


# -----------------------------------------------------------------------------
# 3D projection coloured by feasibility
# -----------------------------------------------------------------------------

def plot_3d_projection(reports_dir, all_df, primary_cap_watts):
    try:
        from mpl_toolkits.mplot3d import Axes3D  # noqa: F401
    except ImportError:
        print('  Skipping 3D projection: mpl_toolkits.mplot3d not available')
        return

    for scenario, sub in all_df.groupby('Scenario'):
        scenario_name = sub['ScenarioName'].iloc[0]
        algos = sorted(sub['Algorithm'].unique())

        fig = plt.figure(figsize=(10, 7))
        ax = fig.add_subplot(111, projection='3d')

        for algo in algos:
            pts = sub[sub['Algorithm'] == algo]
            feasible_mask = pts['PeakPowerWatts'] <= primary_cap_watts
            c = _algo_color(algo, algos)
            # Feasible
            if feasible_mask.any():
                f = pts[feasible_mask]
                ax.scatter(f['WaitingTime'], f['Energy'], f['PeakPowerWatts'],
                           color=c, marker='o', s=18, alpha=0.75,
                           edgecolors='black', linewidths=0.2, label=algo)
            # Infeasible — same color, hollow marker
            if (~feasible_mask).any():
                inf = pts[~feasible_mask]
                ax.scatter(inf['WaitingTime'], inf['Energy'], inf['PeakPowerWatts'],
                           facecolors='none', edgecolors=c, marker='^', s=18,
                           linewidths=0.6, alpha=0.6)

        # Cap plane
        xr = np.linspace(sub['WaitingTime'].min(), sub['WaitingTime'].max(), 2)
        yr = np.linspace(sub['Energy'].min(), sub['Energy'].max(), 2)
        X, Y = np.meshgrid(xr, yr)
        Z = np.full_like(X, primary_cap_watts)
        ax.plot_surface(X, Y, Z, alpha=0.12, color='red')

        ax.set_xlabel('Waiting time (s)')
        ax.set_ylabel('Energy (kWh)')
        ax.set_zlabel('Peak power (W)')
        ax.set_title(f'Scenario {scenario} — {scenario_name}\n'
                     f'3D solutions; cap plane at {int(primary_cap_watts/1000)} kW')
        # Legend (algorithms only — feasibility via marker style)
        handles, labels = ax.get_legend_handles_labels()
        seen = {}
        for h, l in zip(handles, labels):
            if l not in seen:
                seen[l] = h
        if seen:
            ax.legend(seen.values(), seen.keys(),
                      loc='upper left', bbox_to_anchor=(1.05, 1.0),
                      fontsize=7, framealpha=0.9)

        # Feasibility marker legend
        marker_legend = [
            Line2D([0], [0], marker='o', color='gray', label='feasible',
                   markerfacecolor='gray', markersize=5, linewidth=0),
            Line2D([0], [0], marker='^', color='gray', label='infeasible',
                   markerfacecolor='none', markersize=5, linewidth=0),
        ]
        ax.add_artist(ax.legend(handles=marker_legend, loc='lower left',
                                bbox_to_anchor=(1.05, 0.0), fontsize=7,
                                framealpha=0.9))
        # Re-attach algorithm legend (add_artist replaced it)
        if seen:
            ax.legend(seen.values(), seen.keys(),
                      loc='upper left', bbox_to_anchor=(1.05, 1.0),
                      fontsize=7, framealpha=0.9)

        fig.tight_layout()
        out = os.path.join(reports_dir,
                           f'power_ceiling_3d_projection_scenario_{scenario}.png')
        fig.savefig(out, dpi=300, bbox_inches='tight')
        plt.close(fig)
        print(f'  Wrote: {os.path.basename(out)}')


# -----------------------------------------------------------------------------
# Constrained 2D Pareto front per cap
# -----------------------------------------------------------------------------

def plot_constrained_fronts(reports_dir, feasible_df):
    for (scenario, cap), sub in feasible_df.groupby(['Scenario', 'CapWatts']):
        scenario_name = sub['ScenarioName'].iloc[0]
        algos = sorted(sub['Algorithm'].unique())

        fig, ax = plt.subplots(figsize=(9, 6))

        for algo in algos:
            pts = sub[sub['Algorithm'] == algo]
            if pts.empty:
                continue
            pts = pts.sort_values('WaitingTime')
            c = _algo_color(algo, algos)
            ax.plot(pts['WaitingTime'], pts['Energy'],
                    marker='o', linestyle='-', linewidth=1.0, markersize=4,
                    color=c, alpha=0.85, label=algo)

        ax.set_xlabel('Waiting time (s)')
        ax.set_ylabel('Energy (kWh)')
        ax.set_title(f'Scenario {scenario} — {scenario_name}\n'
                     f'Feasibility-filtered constrained Pareto front at '
                     f'{int(cap/1000)} kW')
        ax.grid(True, linestyle=':', alpha=0.4)
        ax.legend(fontsize=7, loc='best', framealpha=0.9)
        fig.tight_layout()
        out = os.path.join(
            reports_dir,
            f'power_ceiling_constrained_front_scenario_{scenario}_cap{int(cap/1000)}kW.png'
        )
        fig.savefig(out, dpi=300, bbox_inches='tight')
        plt.close(fig)
        print(f'  Wrote: {os.path.basename(out)}')


# -----------------------------------------------------------------------------
# Entry point
# -----------------------------------------------------------------------------

def main():
    if len(sys.argv) < 2:
        print('Usage: python plot_power_ceiling.py <reports_directory>')
        sys.exit(1)

    reports_dir = sys.argv[1]

    summary_path = os.path.join(reports_dir, 'feasibility_summary.csv')
    feasible_path = os.path.join(reports_dir, 'pareto_3d_feasible.csv')
    all_path = os.path.join(reports_dir, 'pareto_3d_all.csv')

    missing = [p for p in (summary_path, feasible_path, all_path) if not os.path.exists(p)]
    if missing:
        print(f'  Missing input CSVs: {missing}')
        sys.exit(0)

    summary_df = pd.read_csv(summary_path)
    feasible_df = pd.read_csv(feasible_path)
    all_df = pd.read_csv(all_path)

    # Primary cap = median of the distinct caps present (usually the middle tier).
    caps_present = sorted(summary_df['CapWatts'].unique())
    primary_cap = caps_present[len(caps_present) // 2] if caps_present else 190000.0

    print(f'  Primary cap for 3D projection: {int(primary_cap/1000)} kW')
    plot_feasibility_bars(reports_dir, summary_df)
    plot_3d_projection(reports_dir, all_df, primary_cap)
    plot_constrained_fronts(reports_dir, feasible_df)

    print('  power_ceiling plots complete.')


if __name__ == '__main__':
    main()
