import csv
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import os

REPORTS_DIR = "reports/16_04_2026_08_28"
OUTPUT_FILE = os.path.join(REPORTS_DIR, "waitingtime_vs_energy_pareto.png")

SCENARIOS = [
    (1, "Balanced (250 CPU + 250 GPU)"),
    (2, "GPU Stress (100 CPU + 400 GPU)"),
    (3, "CPU Stress (400 CPU + 100 GPU)")
]

ALGO_STYLES = {
    "GA_WaitingTime": {"color": "#1f77b4", "marker": "^", "label": "GA (WaitingTime)", "alpha": 0.45, "s": 30},
    "GA_Energy":      {"color": "#ff7f0e", "marker": "v", "label": "GA (Energy)",      "alpha": 0.45, "s": 30},
    "SA_WaitingTime": {"color": "#2ca02c", "marker": "<", "label": "SA (WaitingTime)", "alpha": 0.45, "s": 30},
    "SA_Energy":      {"color": "#d62728", "marker": ">", "label": "SA (Energy)",      "alpha": 0.45, "s": 30},
    "NSGA-II":        {"color": "#9467bd", "marker": "o", "label": "NSGA-II",          "alpha": 0.45, "s": 25},
    "SPEA-II":        {"color": "#8c564b", "marker": "s", "label": "SPEA-II",          "alpha": 0.45, "s": 25},
    "Universal_Pareto": {"color": "#e41a1c", "marker": "*", "label": "Universal Pareto", "alpha": 1.0, "s": 120},
}

fig, axes = plt.subplots(1, 3, figsize=(20, 6))
fig.suptitle("Waiting Time vs Energy — Universal Pareto Front\n(6 algorithms, 10 seeds each)", fontsize=14, fontweight='bold')

for idx, (scenario_num, scenario_title) in enumerate(SCENARIOS):
    ax = axes[idx]
    csv_file = os.path.join(REPORTS_DIR, f"scenario_{scenario_num}_pareto_graph_data.csv")

    algo_data = {}
    universal_x, universal_y = [], []

    with open(csv_file, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            algo = row["Algorithm"]
            wt = float(row["WaitingTime"])
            energy = float(row["Energy"])
            is_univ = row["IsUniversalPareto"].strip().lower() == "true"

            if algo == "Universal_Pareto":
                universal_x.append(wt)
                universal_y.append(energy)
            else:
                if algo not in algo_data:
                    algo_data[algo] = {"x": [], "y": []}
                algo_data[algo]["x"].append(wt)
                algo_data[algo]["y"].append(energy)

    # Plot algorithm solutions (behind)
    for algo, style in ALGO_STYLES.items():
        if algo == "Universal_Pareto":
            continue
        if algo in algo_data:
            ax.scatter(algo_data[algo]["x"], algo_data[algo]["y"],
                       c=style["color"], marker=style["marker"], s=style["s"],
                       alpha=style["alpha"], label=style["label"], edgecolors='none', zorder=2)

    # Sort universal Pareto by WaitingTime for line plot
    if universal_x:
        paired = sorted(zip(universal_x, universal_y))
        ux = [p[0] for p in paired]
        uy = [p[1] for p in paired]
        ax.plot(ux, uy, color="#e41a1c", linewidth=1.5, linestyle='-', alpha=0.7, zorder=3)
        ax.scatter(ux, uy, c="#e41a1c", marker="*", s=120, alpha=1.0,
                   label="Universal Pareto", edgecolors='black', linewidths=0.3, zorder=4)

    ax.set_title(f"Scenario {scenario_num}: {scenario_title}", fontsize=11)
    ax.set_xlabel("Average Waiting Time (seconds)", fontsize=10)
    if idx == 0:
        ax.set_ylabel("Energy (kWh)", fontsize=10)
    ax.grid(True, alpha=0.3)
    ax.tick_params(labelsize=9)

# Single legend below plots
handles, labels = axes[0].get_legend_handles_labels()
fig.legend(handles, labels, loc='lower center', ncol=7, fontsize=9,
           bbox_to_anchor=(0.5, -0.02), frameon=True, fancybox=True)

plt.tight_layout(rect=[0, 0.06, 1, 0.93])
plt.savefig(OUTPUT_FILE, dpi=300, bbox_inches='tight')
print(f"Plot saved to: {OUTPUT_FILE}")
