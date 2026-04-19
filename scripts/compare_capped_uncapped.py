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
import matplotlib.pyplot as plt

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

COLOURS = {
    "GA_WaitingTime_Dominance": "#1f77b4",
    "GA_Energy_Dominance":      "#ff7f0e",
    "SA_WaitingTime_Dominance": "#2ca02c",
    "SA_Energy_Dominance":      "#d62728",
    "NSGA-II":                  "#9467bd",
    "SPEA-II":                  "#8c564b",
    "AMOSA":                    "#e377c2",
}


def parse_algorithm_ids(s):
    if not s:
        return list(ALGO_MAP.keys())
    ids = [int(x.strip()) for x in s.split(",") if x.strip()]
    for i in ids:
        if i not in ALGO_MAP:
            raise ValueError(f"Unknown algorithm id: {i} (valid: 1-7)")
    return ids


def non_dominated_mask(points):
    """Boolean mask of non-dominated rows (minimising both columns)."""
    P = np.asarray(points, float)
    n = len(P)
    keep = np.ones(n, bool)
    for i in range(n):
        if not keep[i]:
            continue
        dominated = np.all(P <= P[i], axis=1) & np.any(P < P[i], axis=1)
        keep[dominated] = False
    return keep


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
                             figsize=(16, 12), sharex=True, sharey=True)

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
            sub_full = full[full["CapLevel"] == cap_label]
            sub_sel = sub_full[sub_full["BaseAlgorithm"].isin(selected)]

            for base in selected:
                s = sub_sel[sub_sel["BaseAlgorithm"] == base]
                if s.empty:
                    continue
                x = (s["WaitingTime"] - wt_min) / wt_rng
                y = (s["Energy"] - e_min) / e_rng
                ax.scatter(x, y, s=10, alpha=0.4,
                           color=COLOURS[base], label=base)

            # Global Pareto: best of best across ALL seven algorithms,
            # independent of --algorithms selection.
            if not sub_full.empty:
                pts = sub_full[["WaitingTime", "Energy"]].to_numpy()
                mask = non_dominated_mask(pts)
                g = pts[mask]
                gx = (g[:, 0] - wt_min) / wt_rng
                gy = (g[:, 1] - e_min) / e_rng
                order = np.argsort(gx)
                ax.plot(gx[order], gy[order], color="black", lw=1.4,
                        marker="o", ms=4, label="Global Pareto (all algos)")

            ax.set_title(f"{sname} - {cap_label}", fontsize=10)
            if row == len(SCENARIOS) - 1:
                ax.set_xlabel("Waiting Time (normalised)")
            if col == 0:
                ax.set_ylabel("Energy (normalised)")
            ax.grid(alpha=0.3)

    handles, labels = axes[0, 0].get_legend_handles_labels()
    by_label = dict(zip(labels, handles))
    fig.legend(by_label.values(), by_label.keys(),
               loc="lower center", ncol=min(len(by_label), 5),
               bbox_to_anchor=(0.5, -0.02))
    fig.suptitle("Normalised Pareto fronts - uncapped vs power-capped",
                 fontsize=14)
    fig.tight_layout(rect=[0, 0.04, 1, 0.97])
    out = os.path.join(out_dir, "pareto_fronts_comparison.png")
    fig.savefig(out, dpi=200, bbox_inches="tight")
    plt.close(fig)
    print(f"Wrote {out}")


# ---------------------------------------------------------------------------
# 2. Comparison table (HV / IGD / GD / Pareto contribution / runtime)
# ---------------------------------------------------------------------------
def build_table(reports_dir, out_dir, algo_ids):
    names = [ALGO_MAP[i] for i in algo_ids]
    unc = pd.read_csv(os.path.join(reports_dir, "new",
                                   "experiment_summary.csv"))
    cap = pd.read_csv(os.path.join(reports_dir, "powerceiling",
                                   "experiment_summary.csv"))

    rows = []
    for sid, sname in SCENARIOS:
        for base in names:
            for cap_label, suffix in CAP_LEVELS:
                src = unc if suffix == "" else cap
                sel = src[(src["Scenario"] == sid)
                         & (src["Algorithm"] == base + suffix)
                         & (src["Seed"].astype(str) == "MEAN")]
                if sel.empty:
                    rows.append({"Scenario": sname, "Algorithm": base,
                                 "Cap": cap_label, "HV": np.nan,
                                 "GD": np.nan, "IGD": np.nan,
                                 "ParetoContribution": np.nan,
                                 "TimeMs": np.nan})
                    continue
                r = sel.iloc[0]
                rows.append({
                    "Scenario": sname, "Algorithm": base, "Cap": cap_label,
                    "HV": float(r["HV"]), "GD": float(r["GD"]),
                    "IGD": float(r["IGD"]),
                    "ParetoContribution": int(r["ParetoContribution"]),
                    "TimeMs": int(r["TimeMs"]),
                })

    df = pd.DataFrame(rows)
    df.to_csv(os.path.join(out_dir, "comparison_table.csv"), index=False)
    print(f"Wrote {os.path.join(out_dir, 'comparison_table.csv')}")

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
