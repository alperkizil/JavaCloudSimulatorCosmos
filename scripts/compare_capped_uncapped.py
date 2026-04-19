#!/usr/bin/env python3
"""
compare_capped_uncapped.py

Produces four artefacts comparing the unconstrained baseline (reports/new/)
against the power-capped 190 kW / 120 kW variants (reports/powerceiling/)
across the three scenarios (Balanced, GPU_Stress, CPU_Stress):

  1. pareto_fronts_comparison.png  - 3x3 grid of normalised Pareto fronts
     (rows = scenarios, cols = cap levels). Each subplot shows the selected
     algorithms as dots plus the pooled "global" Pareto line across them.
  2. comparison_table.csv + pivot_{HV,IGD,GD,ParetoContribution}.csv - the
     MEAN values per (scenario x algorithm x cap) in both long and wide form.
  3. runtime_comparison.png - grouped bar chart of mean runtime per
     algorithm, one panel per scenario, three bars per algorithm (cap level).
  4. admission_disaster.png - focused log-scale bar chart contrasting the
     naive runtime-admission baseline against the best constrained MOEA and
     the unconstrained baseline. Makes the 30-50x waiting-time blowup
     visually obvious.

Algorithm IDs (--algorithms 1,2,3 to restrict; default: all):
  1  GA_WaitingTime_Dominance   5  NSGA-II
  2  GA_Energy_Dominance        6  SPEA-II
  3  SA_WaitingTime_Dominance   7  AMOSA
  4  SA_Energy_Dominance

The "Global Pareto" line on each subplot is always computed from ALL
seven base algorithms regardless of the --algorithms filter, so it shows
the true best-of-best front for that (scenario, cap) combination. The
--algorithms filter only affects which per-algorithm scatter points are
drawn.
"""
import argparse
import os
import sys
import numpy as np
import pandas as pd
import matplotlib as mpl
import matplotlib.pyplot as plt

# Publication styling — ported from scripts/plot_scenario_pareto.py so the
# Pareto figure produced here matches the main experiment plots.
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

ALGO_MAP = {
    1: "GA_WaitingTime_Dominance",
    2: "GA_Energy_Dominance",
    3: "SA_WaitingTime_Dominance",
    4: "SA_Energy_Dominance",
    5: "NSGA-II",
    6: "SPEA-II",
    7: "AMOSA",
}

CAP_LEVELS = [
    ("Uncapped", ""),
    ("190kW",    "_PC_190kW"),
    ("120kW",    "_PC_120kW"),
]

SCENARIOS = [(1, "Balanced"), (2, "GPU_Stress"), (3, "CPU_Stress")]

# Okabe-Ito palette — colorblind-safe; shape + fill provide redundancy.
OKABE_ITO = {
    'black':          '#000000',
    'orange':         '#E69F00',
    'sky_blue':       '#56B4E9',
    'bluish_green':   '#009E73',
    'blue':           '#0072B2',
    'vermillion':     '#D55E00',
    'reddish_purple': '#CC79A7',
}

# (color, marker, filled, display_label) — mirrors plot_scenario_pareto.py.
ALGORITHM_STYLE = {
    "GA_WaitingTime_Dominance": ('#56B4E9',                     'D', True, 'GA Dominance (Waiting Time)'),
    "GA_Energy_Dominance":      ('#1B3A6B',                     'D', True, 'GA Dominance (Energy)'),
    "SA_WaitingTime_Dominance": ('#FF6B9D',                     's', True, 'SA Dominance (Waiting Time)'),
    "SA_Energy_Dominance":      ('#8B0000',                     's', True, 'SA Dominance (Energy)'),
    "NSGA-II":                  (OKABE_ITO['bluish_green'],     'o', True, 'NSGA-II'),
    "SPEA-II":                  (OKABE_ITO['reddish_purple'],   '^', True, 'SPEA-II'),
    "AMOSA":                    (OKABE_ITO['orange'],           'v', True, 'AMOSA'),
}

UNIVERSAL_PARETO_COLOR = OKABE_ITO['black']


def get_style(algo):
    return ALGORITHM_STYLE.get(algo, (OKABE_ITO['black'], 'o', True, algo))


def parse_algorithm_ids(s):
    if not s:
        return list(ALGO_MAP.keys())
    ids = [int(x.strip()) for x in s.split(",") if x.strip()]
    for i in ids:
        if i not in ALGO_MAP:
            raise ValueError(f"Unknown algorithm id: {i} (valid: 1-7)")
    return ids


def non_dominated_mask(points):
    """Boolean mask of non-dominated rows (minimising both columns).

    Row i is kept iff no other row j satisfies P[j] <= P[i] component-wise
    AND P[j] < P[i] in at least one dimension (i.e. j dominates i).
    """
    P = np.asarray(points, float)
    n = len(P)
    keep = np.ones(n, bool)
    for i in range(n):
        # j=i contributes np.any(P[i] < P[i]) == False, so it's excluded.
        dominated_by_any = (np.all(P <= P[i], axis=1)
                            & np.any(P < P[i], axis=1)).any()
        if dominated_by_any:
            keep[i] = False
    return keep


def get_non_dominated(points):
    """Return non-dominated points sorted by x (ported from plot_scenario_pareto.py)."""
    if len(points) == 0:
        return np.array([])
    P = np.asarray(points, float)
    mask = non_dominated_mask(P)
    nd = P[mask]
    if len(nd) == 0:
        return np.array([])
    # Deduplicate near-identical points before returning (stable sort by x).
    out = []
    for pt in nd:
        if not any(abs(o[0] - pt[0]) < 1e-9 and abs(o[1] - pt[1]) < 1e-9
                   for o in out):
            out.append(pt)
    out = np.asarray(out)
    return out[np.argsort(out[:, 0])]


def compute_eaf_grid(seed_fronts, grid_x, grid_y):
    """Empirical attainment frequency across seeds on a 2-D grid.

    Ported from scripts/plot_scenario_pareto.py. Returns an array of shape
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


def load_graph(scenario, reports_dir):
    """Concatenate uncapped + capped Pareto-graph rows for one scenario.
    Keeps every base algorithm so the global Pareto can be computed
    across all of them; caller filters for scatter plotting."""
    unc = pd.read_csv(os.path.join(
        reports_dir, "new", f"scenario_{scenario}_pareto_graph_data.csv"))
    unc["CapLevel"] = "Uncapped"
    unc["BaseAlgorithm"] = unc["Algorithm"]

    cap = pd.read_csv(os.path.join(
        reports_dir, "powerceiling",
        f"scenario_{scenario}_pareto_graph_data.csv"))

    def classify(name):
        for label, suffix in CAP_LEVELS[1:]:
            if name.endswith(suffix):
                return name[: -len(suffix)], label
        return name, None

    parsed = cap["Algorithm"].apply(classify)
    cap["BaseAlgorithm"] = parsed.apply(lambda x: x[0])
    cap["CapLevel"] = parsed.apply(lambda x: x[1])
    cap = cap[cap["CapLevel"].notna()]

    return pd.concat([unc, cap], ignore_index=True)


# ---------------------------------------------------------------------------
# 1. Normalised Pareto fronts
# ---------------------------------------------------------------------------
def plot_fronts(reports_dir, out_dir, algo_ids):
    selected = [ALGO_MAP[i] for i in algo_ids]
    all_names = list(ALGO_MAP.values())
    fig, axes = plt.subplots(len(SCENARIOS), len(CAP_LEVELS),
                             figsize=(16, 13), sharex=True, sharey=True)

    grid_x = np.linspace(0.0, 1.05, 220)
    grid_y = np.linspace(0.0, 1.05, 220)

    for row, (sid, sname) in enumerate(SCENARIOS):
        full = load_graph(sid, reports_dir)
        # Only keep the seven base algorithms (drops admission-control and
        # any other non-core variants) so the global front is comparable.
        full = full[full["BaseAlgorithm"].isin(all_names)]
        if full.empty:
            continue
        wt_min, wt_max = full["WaitingTime"].min(), full["WaitingTime"].max()
        e_min, e_max = full["Energy"].min(), full["Energy"].max()
        wt_rng = max(wt_max - wt_min, 1e-9)
        e_rng = max(e_max - e_min, 1e-9)

        for col, (cap_label, _) in enumerate(CAP_LEVELS):
            ax = axes[row, col]
            ax.set_xlim(-0.03, 1.05)
            ax.set_ylim(-0.03, 1.05)
            ax.grid(True, which='major')

            sub_full = full[full["CapLevel"] == cap_label]
            sub_sel = sub_full[sub_full["BaseAlgorithm"].isin(selected)]

            # EAF per algorithm: 50% median attainment line + [25%, 75%] band.
            # All seven algorithms are MO-producing (one Pareto front per
            # seed), so — following plot_scenario_pareto.py — the only
            # summary is the EAF. Marginal median + IQR on x and y would
            # produce a phantom point that can visually dominate the true
            # global Pareto without any real solution landing there.
            for base in selected:
                s = sub_sel[sub_sel["BaseAlgorithm"] == base]
                if s.empty:
                    continue
                color, _marker, _filled, display = get_style(base)
                seed_fronts = []
                for _, seed_df in s.groupby("Seed"):
                    pts = seed_df[["WaitingTime", "Energy"]].to_numpy(float)
                    pts[:, 0] = (pts[:, 0] - wt_min) / wt_rng
                    pts[:, 1] = (pts[:, 1] - e_min) / e_rng
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
                # Proxy line so the legend carries one entry per algorithm.
                ax.plot([], [], color=color, linewidth=1.6, label=display)

            # Global Pareto: best of best across ALL seven algorithms,
            # independent of --algorithms selection. Rendered as a step
            # function (horizontal then vertical between adjacent points)
            # so the curve follows the true non-dominated frontier — a
            # diagonal chord would dip below unattained space and make
            # coloured EAF contours visually appear to cross it.
            if not sub_full.empty:
                pts = sub_full[["WaitingTime", "Energy"]].to_numpy()
                mask = non_dominated_mask(pts)
                g = pts[mask]
                gx = (g[:, 0] - wt_min) / wt_rng
                gy = (g[:, 1] - e_min) / e_rng
                order = np.argsort(gx)
                ax.plot(gx[order], gy[order],
                        color=UNIVERSAL_PARETO_COLOR, linewidth=1.8,
                        linestyle='-', alpha=0.9,
                        drawstyle='steps-post',
                        label="Global Pareto (all algos)", zorder=6)
                ax.scatter(gx[order], gy[order], s=30, marker='o',
                           facecolors=UNIVERSAL_PARETO_COLOR,
                           edgecolors=UNIVERSAL_PARETO_COLOR,
                           linewidths=0.4, zorder=7)

            # Ideal-point reference marker at (0, 0)
            ax.scatter([0.0], [0.0], marker='*', s=120,
                       facecolor='white', edgecolor='black',
                       linewidths=0.9, zorder=8)
            if row == 0 and col == 0:
                ax.annotate('ideal', xy=(0.0, 0.0), xytext=(6, 6),
                            textcoords='offset points',
                            fontsize=8, style='italic')

            ax.set_title(f"{sname} — {cap_label}")
            if row == len(SCENARIOS) - 1:
                ax.set_xlabel('Average Waiting Time (s)  (normalized)')
            if col == 0:
                ax.set_ylabel('Energy (kWh)  (normalized)')

            # Secondary axes in raw units (per-scenario bounds)
            range_x = wt_rng
            range_y = e_rng
            ideal_x_local = wt_min
            ideal_y_local = e_min
            if row == 0:
                secx = ax.secondary_xaxis(
                    'top',
                    functions=(lambda u, a=ideal_x_local, r=range_x: a + u * r,
                               lambda v, a=ideal_x_local, r=range_x: (v - a) / r),
                )
                secx.tick_params(labelsize=8)
            if col == len(CAP_LEVELS) - 1:
                secy = ax.secondary_yaxis(
                    'right',
                    functions=(lambda u, a=ideal_y_local, r=range_y: a + u * r,
                               lambda v, a=ideal_y_local, r=range_y: (v - a) / r),
                )
                secy.tick_params(labelsize=8)

    handles, labels = axes[0, 0].get_legend_handles_labels()
    by_label = dict(zip(labels, handles))
    if by_label:
        fig.legend(
            by_label.values(), by_label.keys(),
            loc="lower center",
            ncol=min(len(by_label), 4),
            bbox_to_anchor=(0.5, -0.04),
            frameon=True, fancybox=False, edgecolor='#888888',
        )
    fig.suptitle(
        'Normalised Pareto Fronts — Uncapped vs Power-Capped',
        y=1.02,
    )
    caption = (
        'Per algorithm: 50% empirical attainment surface (solid) with '
        '[25%, 75%] band (shaded) across 10 seeds. Black line = global '
        'Pareto front pooled across all seven algorithms. Axes normalised '
        'per scenario to [0, 1]; raw units shown on top (x) and right (y) '
        'axes. White star marks the ideal point.'
    )
    fig.text(0.5, 0.965, caption, ha='center', fontsize=8.5,
             style='italic', color='#333333', wrap=True)
    fig.tight_layout(rect=[0, 0.02, 1, 0.94])
    out = os.path.join(out_dir, "pareto_fronts_comparison.png")
    fig.savefig(out, dpi=300, bbox_inches="tight")
    plt.close(fig)
    print(f"Wrote {out}")


# ---------------------------------------------------------------------------
# 2. Comparison table (HV / IGD / GD / Pareto contribution / runtime)
# ---------------------------------------------------------------------------
def compute_global_pareto_contributions(reports_dir, names):
    """For each (scenario, cap level), recompute ParetoContribution against
    the same Global Pareto the plot draws: the union of all `names` at that
    cap level. This matters because Java's CSV reports contributions against
    the scenario-wide Universal Pareto, which in the power-ceiling experiment
    pools uncapped and capped variants together — capped algorithms then
    appear to contribute nothing even when they dominate their own cap tier.

    Returns dict keyed by (sname, cap_label, algo) -> int.
    """
    out = {}
    for sid, sname in SCENARIOS:
        full = load_graph(sid, reports_dir)
        full = full[full["BaseAlgorithm"].isin(names)]
        if full.empty:
            continue
        for cap_label, _ in CAP_LEVELS:
            sub = full[full["CapLevel"] == cap_label].reset_index(drop=True)
            if sub.empty:
                continue
            pts = sub[["WaitingTime", "Energy"]].to_numpy()
            mask = non_dominated_mask(pts)
            on = sub[mask]
            counts = on.groupby("BaseAlgorithm").size().to_dict()
            for base in names:
                out[(sname, cap_label, base)] = int(counts.get(base, 0))
    return out


def build_table(reports_dir, out_dir, algo_ids):
    names = [ALGO_MAP[i] for i in algo_ids]
    unc = pd.read_csv(os.path.join(reports_dir, "new",
                                   "experiment_summary.csv"))
    cap = pd.read_csv(os.path.join(reports_dir, "powerceiling",
                                   "experiment_summary.csv"))
    # Always recompute ParetoContribution across the seven base algorithms,
    # so the pivot table agrees with the Pareto-front figure. The Java-side
    # column in experiment_summary.csv is left untouched on disk but not
    # propagated here.
    contribs = compute_global_pareto_contributions(reports_dir, list(ALGO_MAP.values()))

    rows = []
    for sid, sname in SCENARIOS:
        for base in names:
            for cap_label, suffix in CAP_LEVELS:
                src = unc if suffix == "" else cap
                sel = src[(src["Scenario"] == sid)
                         & (src["Algorithm"] == base + suffix)
                         & (src["Seed"].astype(str) == "MEAN")]
                pc = contribs.get((sname, cap_label, base), 0)
                if sel.empty:
                    rows.append({"Scenario": sname, "Algorithm": base,
                                 "Cap": cap_label, "HV": np.nan,
                                 "GD": np.nan, "IGD": np.nan,
                                 "ParetoContribution": pc,
                                 "TimeMs": np.nan})
                    continue
                r = sel.iloc[0]
                rows.append({
                    "Scenario": sname, "Algorithm": base, "Cap": cap_label,
                    "HV": float(r["HV"]), "GD": float(r["GD"]),
                    "IGD": float(r["IGD"]),
                    "ParetoContribution": pc,
                    "TimeMs": int(r["TimeMs"]),
                })

    df = pd.DataFrame(rows)
    notes = [
        "# HV is the per-algorithm Java-computed hypervolume from",
        "#   reports/{new,powerceiling}/experiment_summary.csv (MEAN rows).",
        "#   Each algorithm uses its own reference point, so HV values are",
        "#   ranked consistently within a (scenario, cap) cell but should",
        "#   be read as relative, not absolute.",
        "# HV_fixed (scenario-fixed reference point, fair across algorithms)",
        "#   is available in quality_indicators_all_scenarios.csv for each",
        "#   run, but the reference points differ ~10x between the uncapped",
        "#   and power-ceiling experiments (the latter has admission-control",
        "#   and infeasible variants that inflate the nadir), so HV_fixed is",
        "#   NOT directly comparable across the Uncapped / 190kW / 120kW",
        "#   columns. Java HV is the less-bad option for this pivot.",
        "# GD / IGD: unitless distance to each run's reference front; lower",
        "#   is better. Values are taken as-is from experiment_summary.csv.",
        "# ParetoContribution: recomputed here per (scenario, cap) against",
        "#   the pooled non-dominated set of the seven MO algorithms at",
        "#   that cap level, so it matches pareto_fronts_comparison.png.",
    ]
    path = os.path.join(out_dir, "comparison_table.csv")
    with open(path, "w") as f:
        f.write("\n".join(notes) + "\n")
        df.to_csv(f, index=False)
    print(f"Wrote {path}")

    cap_order = [c[0] for c in CAP_LEVELS]
    for metric in ["HV", "IGD", "GD", "ParetoContribution", "TimeMs"]:
        pv = (df.pivot_table(index=["Scenario", "Algorithm"],
                             columns="Cap", values=metric)
                .reindex(columns=cap_order))
        p = os.path.join(out_dir, f"pivot_{metric}.csv")
        pv.to_csv(p)
        print(f"Wrote {p}")
    return df


# ---------------------------------------------------------------------------
# 3. Runtime bars
# ---------------------------------------------------------------------------
def plot_runtimes(df, out_dir):
    fig, axes = plt.subplots(1, 3, figsize=(16, 5), sharey=True)
    algos = list(dict.fromkeys(df["Algorithm"].tolist()))
    caps = [c[0] for c in CAP_LEVELS]
    x = np.arange(len(algos))
    w = 0.26

    for i, (_, sname) in enumerate(SCENARIOS):
        ax = axes[i]
        sub = df[df["Scenario"] == sname]
        for j, cap in enumerate(caps):
            vals = [sub[(sub["Algorithm"] == a) & (sub["Cap"] == cap)]
                        ["TimeMs"].mean() for a in algos]
            ax.bar(x + (j - 1) * w, vals, width=w, label=cap)
        ax.set_xticks(x)
        ax.set_xticklabels(algos, rotation=45, ha="right", fontsize=9)
        ax.set_title(sname)
        if i == 0:
            ax.set_ylabel("Runtime (ms, mean of 10 seeds)")
        ax.grid(axis="y", alpha=0.3)

    axes[-1].legend(title="Power cap", loc="upper left")
    fig.suptitle("Runtime per algorithm - uncapped vs 190 kW vs 120 kW",
                 fontsize=13)
    fig.tight_layout(rect=[0, 0, 1, 0.95])
    out = os.path.join(out_dir, "runtime_comparison.png")
    fig.savefig(out, dpi=200, bbox_inches="tight")
    plt.close(fig)
    print(f"Wrote {out}")


# ---------------------------------------------------------------------------
# 4. Admission-control disaster
# ---------------------------------------------------------------------------
def plot_admission_disaster(reports_dir, out_dir):
    """Contrasts naive runtime admission against constrained MOEA and the
    unconstrained baseline, using WaitingTime_Best from the MEAN row."""
    unc = pd.read_csv(os.path.join(reports_dir, "new",
                                   "experiment_summary.csv"))
    cap = pd.read_csv(os.path.join(reports_dir, "powerceiling",
                                   "experiment_summary.csv"))

    picks = [
        ("SPEA-II",                         unc, "Unconstrained SPEA-II"),
        ("SPEA-II_PC_120kW",                cap, "Constrained SPEA-II @120kW"),
        ("WorkloadAware_Admission_PC_120kW", cap, "Admission control @120kW"),
    ]

    labels = [p[2] for p in picks]
    values = {sname: [] for _, sname in SCENARIOS}
    for sid, sname in SCENARIOS:
        for algo, src, _ in picks:
            sel = src[(src["Scenario"] == sid)
                      & (src["Algorithm"] == algo)
                      & (src["Seed"].astype(str) == "MEAN")]
            values[sname].append(
                float(sel.iloc[0]["WaitingTime_Best"]) if not sel.empty
                else np.nan)

    fig, ax = plt.subplots(figsize=(11, 5.5))
    x = np.arange(len(labels))
    w = 0.27
    for i, (_, sname) in enumerate(SCENARIOS):
        ax.bar(x + (i - 1) * w, values[sname], width=w, label=sname)
    ax.set_xticks(x)
    ax.set_xticklabels(labels, fontsize=10)
    ax.set_ylabel("Best waiting time (seconds, log scale)")
    ax.set_yscale("log")
    ax.grid(axis="y", which="both", alpha=0.3)
    ax.legend(title="Scenario")
    ax.set_title("Naive runtime admission vs optimiser-based capping "
                 "(lower = better)")

    for i, (_, sname) in enumerate(SCENARIOS):
        for j, v in enumerate(values[sname]):
            if np.isfinite(v):
                ax.text(x[j] + (i - 1) * w, v * 1.05,
                        f"{v:.1f}s", ha="center", fontsize=8)

    fig.tight_layout()
    out = os.path.join(out_dir, "admission_disaster.png")
    fig.savefig(out, dpi=200, bbox_inches="tight")
    plt.close(fig)
    print(f"Wrote {out}")


# ---------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--algorithms", type=str, default=None,
                    help="Comma-separated IDs 1-7. Default: all.")
    here = os.path.dirname(os.path.abspath(__file__))
    default_reports = os.path.join(os.path.dirname(here), "reports")
    ap.add_argument("--reports-dir", type=str, default=default_reports,
                    help="Root containing new/ and powerceiling/.")
    default_out = os.path.join(os.path.dirname(here), "final_experiment_results")
    ap.add_argument("--out-dir", type=str, default=None,
                    help=f"Output directory. Default: {default_out}")
    args = ap.parse_args()

    ids = parse_algorithm_ids(args.algorithms)
    out = args.out_dir or default_out
    os.makedirs(out, exist_ok=True)
    print(f"Algorithms : {[ALGO_MAP[i] for i in ids]}")
    print(f"Reports dir: {args.reports_dir}")
    print(f"Output dir : {out}\n")

    plot_fronts(args.reports_dir, out, ids)
    df = build_table(args.reports_dir, out, ids)
    plot_runtimes(df, out)
    plot_admission_disaster(args.reports_dir, out)
    print("\nDone.")


if __name__ == "__main__":
    main()
