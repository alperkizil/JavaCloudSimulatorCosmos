#!/usr/bin/env python3
"""
Cross-Experiment Comparison Tool for JavaCloudSimulatorCosmos

Compares algorithm performance across different timestamped experiment runs
in reports/<timestamp>/.

Reads experiment_summary.csv from each run and produces:
  1. Console table showing metric deltas (before vs after)
  2. A CSV with side-by-side metrics for all selected runs

Usage:
    # Auto-detect the two most recent runs and compare:
    python scripts/compare_experiments.py

    # Compare two specific runs:
    python scripts/compare_experiments.py reports/15_04_2026_10_38 reports/16_04_2026_14_22

    # Compare all available runs:
    python scripts/compare_experiments.py --all
"""

import csv
import os
import sys
from datetime import datetime
from pathlib import Path

REPORTS_BASE = Path("reports")
SUMMARY_FILE = "experiment_summary.csv"

# Metrics where lower is better
LOWER_IS_BETTER = {"GD", "IGD", "Spacing", "Makespan_Best", "Energy_Best", "TimeMs"}
# Metrics where higher is better
HIGHER_IS_BETTER = {"HV", "NonDomSolutions", "ParetoContribution"}

METRIC_COLS = ["HV", "GD", "IGD", "Spacing", "NonDomSolutions", "ParetoContribution", "TimeMs", "Makespan_Best", "Energy_Best"]


TIMESTAMP_FORMAT = "%d_%m_%Y_%H_%M"


def _parse_run_timestamp(run_dir):
    """Try to parse folder name as dd_MM_yyyy_HH_mm. Returns datetime or None."""
    try:
        return datetime.strptime(run_dir.name, TIMESTAMP_FORMAT)
    except ValueError:
        return None


def discover_runs(base_dir):
    """Find all experiment run directories, sorted chronologically (oldest-first).

    Folders with dd_MM_yyyy_HH_mm names are sorted by parsed timestamp.
    Non-timestamped folders (e.g. manually renamed) are placed at the front
    in lexicographic order, since their actual date is unknown.
    """
    if not base_dir.exists():
        return []
    timestamped = []
    other = []
    for entry in base_dir.iterdir():
        if entry.is_dir() and (entry / SUMMARY_FILE).exists():
            ts = _parse_run_timestamp(entry)
            if ts is not None:
                timestamped.append((ts, entry))
            else:
                other.append(entry)
    timestamped.sort(key=lambda x: x[0])
    other.sort(key=lambda x: x.name)
    return [e for e in other] + [e for _, e in timestamped]


def load_summary(run_dir):
    """Load experiment_summary.csv, returning only MEAN rows keyed by (Scenario, Algorithm)."""
    path = run_dir / SUMMARY_FILE
    data = {}
    with open(path, "r") as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row.get("Seed") != "MEAN":
                continue
            key = (row["Scenario"], row["ScenarioName"], row["Algorithm"])
            parsed = {}
            for col in METRIC_COLS:
                val = row.get(col, "")
                try:
                    parsed[col] = float(val)
                except (ValueError, TypeError):
                    parsed[col] = None
            data[key] = parsed
    return data


def format_delta(old_val, new_val, metric):
    """Format a delta value with direction indicator."""
    if old_val is None or new_val is None:
        return "N/A"
    delta = new_val - old_val
    if abs(old_val) > 1e-12:
        pct = (delta / abs(old_val)) * 100
    else:
        pct = 0.0 if abs(delta) < 1e-12 else float("inf")

    if metric in LOWER_IS_BETTER:
        indicator = "+" if delta < -1e-12 else ("-" if delta > 1e-12 else "=")
    elif metric in HIGHER_IS_BETTER:
        indicator = "+" if delta > 1e-12 else ("-" if delta < -1e-12 else "=")
    else:
        indicator = "~"

    return f"{delta:+.4f} ({pct:+.1f}%) [{indicator}]"


def format_val(val, metric):
    """Format a metric value for display."""
    if val is None:
        return "N/A"
    if metric in ("TimeMs",):
        return f"{val:.0f}"
    if metric in ("NonDomSolutions", "ParetoContribution"):
        return f"{val:.0f}"
    if metric == "Energy_Best":
        return f"{val:.6f}"
    return f"{val:.4f}"


def print_comparison(run_a, run_b, data_a, data_b):
    """Print a formatted comparison table between two runs."""
    name_a = run_a.name
    name_b = run_b.name

    all_keys = sorted(set(data_a.keys()) | set(data_b.keys()))
    if not all_keys:
        print("No MEAN rows found in either run.")
        return

    print()
    print("=" * 100)
    print(f"  EXPERIMENT COMPARISON")
    print(f"  Old: {name_a}")
    print(f"  New: {name_b}")
    print("=" * 100)

    current_scenario = None
    for key in all_keys:
        scenario_num, scenario_name, algorithm = key
        if scenario_num != current_scenario:
            current_scenario = scenario_num
            print()
            print(f"--- Scenario {scenario_num}: {scenario_name} ---")
            print()

        vals_a = data_a.get(key, {})
        vals_b = data_b.get(key, {})

        print(f"  [{algorithm}]")
        for metric in METRIC_COLS:
            old_v = vals_a.get(metric)
            new_v = vals_b.get(metric)
            delta_str = format_delta(old_v, new_v, metric)
            old_str = format_val(old_v, metric)
            new_str = format_val(new_v, metric)
            print(f"    {metric:<22s}  {old_str:>14s}  ->  {new_str:>14s}   {delta_str}")
        print()


def write_comparison_csv(runs, all_data, output_path):
    """Write a CSV with side-by-side metrics for all runs."""
    if not runs:
        return

    # Collect all keys across all runs
    all_keys = set()
    for data in all_data:
        all_keys.update(data.keys())
    all_keys = sorted(all_keys)

    with open(output_path, "w", newline="") as f:
        writer = csv.writer(f)

        # Header
        header = ["Scenario", "ScenarioName", "Algorithm"]
        for run in runs:
            for metric in METRIC_COLS:
                header.append(f"{run.name}_{metric}")
        writer.writerow(header)

        for key in all_keys:
            scenario_num, scenario_name, algorithm = key
            row = [scenario_num, scenario_name, algorithm]
            for data in all_data:
                vals = data.get(key, {})
                for metric in METRIC_COLS:
                    v = vals.get(metric)
                    row.append(f"{v}" if v is not None else "")
            writer.writerow(row)

    print(f"\nComparison CSV written to: {output_path}")


def print_overall_summary(data_a, data_b):
    """Print a compact wins/losses/ties summary across all algorithms and scenarios."""
    wins = {}
    losses = {}
    ties = {}
    for algo in set(k[2] for k in set(data_a.keys()) | set(data_b.keys())):
        wins[algo] = 0
        losses[algo] = 0
        ties[algo] = 0

    all_keys = sorted(set(data_a.keys()) & set(data_b.keys()))
    for key in all_keys:
        algo = key[2]
        vals_a = data_a.get(key, {})
        vals_b = data_b.get(key, {})
        for metric in METRIC_COLS:
            old_v = vals_a.get(metric)
            new_v = vals_b.get(metric)
            if old_v is None or new_v is None:
                continue
            delta = new_v - old_v
            if abs(delta) < 1e-12:
                ties[algo] += 1
            elif metric in LOWER_IS_BETTER:
                if delta < 0:
                    wins[algo] += 1
                else:
                    losses[algo] += 1
            elif metric in HIGHER_IS_BETTER:
                if delta > 0:
                    wins[algo] += 1
                else:
                    losses[algo] += 1

    print()
    print("=" * 60)
    print("  OVERALL WINS / LOSSES / TIES (new vs old)")
    print("=" * 60)
    print(f"  {'Algorithm':<22s}  {'Wins':>6s}  {'Losses':>6s}  {'Ties':>6s}")
    print(f"  {'-'*22}  {'-'*6}  {'-'*6}  {'-'*6}")
    for algo in sorted(wins.keys()):
        print(f"  {algo:<22s}  {wins[algo]:>6d}  {losses[algo]:>6d}  {ties[algo]:>6d}")
    total_w = sum(wins.values())
    total_l = sum(losses.values())
    total_t = sum(ties.values())
    print(f"  {'-'*22}  {'-'*6}  {'-'*6}  {'-'*6}")
    print(f"  {'TOTAL':<22s}  {total_w:>6d}  {total_l:>6d}  {total_t:>6d}")
    print()


def main():
    args = sys.argv[1:]

    if "--all" in args:
        runs = discover_runs(REPORTS_BASE)
        if len(runs) < 2:
            print(f"Need at least 2 runs in {REPORTS_BASE}/, found {len(runs)}.")
            sys.exit(1)
        print(f"Found {len(runs)} experiment runs, comparing sequentially.")
        all_data = [load_summary(r) for r in runs]

        # Pairwise comparisons: each consecutive pair
        for i in range(len(runs) - 1):
            print_comparison(runs[i], runs[i + 1], all_data[i], all_data[i + 1])
            print_overall_summary(all_data[i], all_data[i + 1])

        # Write combined CSV
        output_path = REPORTS_BASE / "cross_experiment_comparison.csv"
        write_comparison_csv(runs, all_data, output_path)

    elif len(args) >= 2:
        # Two explicit paths
        run_a = Path(args[0])
        run_b = Path(args[1])
        for r in (run_a, run_b):
            if not (r / SUMMARY_FILE).exists():
                print(f"ERROR: {r / SUMMARY_FILE} not found.")
                sys.exit(1)
        data_a = load_summary(run_a)
        data_b = load_summary(run_b)
        print_comparison(run_a, run_b, data_a, data_b)
        print_overall_summary(data_a, data_b)
        output_path = REPORTS_BASE / "cross_experiment_comparison.csv"
        write_comparison_csv([run_a, run_b], [data_a, data_b], output_path)

    else:
        # Auto-detect two most recent runs
        runs = discover_runs(REPORTS_BASE)
        if len(runs) < 2:
            print(f"Need at least 2 runs in {REPORTS_BASE}/.")
            if runs:
                print(f"Only found: {runs[0].name}")
            else:
                print("No experiment runs found yet. Run the Java experiment first.")
            print()
            print("Usage:")
            print("  python scripts/compare_experiments.py                    # compare two most recent")
            print("  python scripts/compare_experiments.py <dir_old> <dir_new>  # compare specific runs")
            print("  python scripts/compare_experiments.py --all              # compare all runs")
            sys.exit(1)

        run_a = runs[-2]
        run_b = runs[-1]
        print(f"Auto-detected: comparing {run_a.name} (old) vs {run_b.name} (new)")
        data_a = load_summary(run_a)
        data_b = load_summary(run_b)
        print_comparison(run_a, run_b, data_a, data_b)
        print_overall_summary(data_a, data_b)
        output_path = REPORTS_BASE / "cross_experiment_comparison.csv"
        write_comparison_csv([run_a, run_b], [data_a, data_b], output_path)


if __name__ == "__main__":
    main()
