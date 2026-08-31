#!/usr/bin/env python3
"""Post-campaign analysis for the 90/80/70/60/50 power-cap ladder.

Answers, in order:
  1. Is the 50% tier feasible at all?  (the open question)
  2. What does each tier cost in waiting time?  (mean over seeds, not min)
  3. Is the cost monotone?  (min-over-seeds was not, in the 28 Aug run)
  4. Is the energy "cost" real or a search artifact?
  5. Does HV report the wrong sign?
"""
import sys, glob, os
import pandas as pd, numpy as np
from scipy import stats

d_dir = sys.argv[1] if len(sys.argv) > 1 else sorted(glob.glob('results/PowerCeiling*'))[-1]
print(f"# Campaign: {os.path.basename(d_dir)}\n")

d   = pd.read_csv(f'{d_dir}/pareto_3d_all.csv')
cal = pd.read_csv(f'{d_dir}/power_cap_calibration.csv')
feas= pd.read_csv(f'{d_dir}/feasibility_summary.csv')
capmap = {(r.ScenarioName, r.Tier): r.CapWatts for r in cal.itertuples()}
pref   = {r.ScenarioName: r.ReferencePeakWatts for r in cal.itertuples()}
d['Tier']    = d.Algorithm.str.extract(r'_(PC\d+)$')[0]
d['BaseArm'] = d.Algorithm.str.replace(r'_PC\d+$', '', regex=True)
TIERS = [t for t in ['PC90','PC80','PC70','PC60','PC50'] if t in set(d.Tier.dropna())]
SCN   = ['Balanced','GPU_Stress','CPU_Stress']
print(f"tiers present: {TIERS}\nsolutions: {len(d)}\n")

print("="*70); print("1. FEASIBILITY — is 50% reachable?"); print("="*70)
cap = d[d.Tier.notna()].copy()
cap['CapW'] = [capmap[(s,t)] for s,t in zip(cap.ScenarioName, cap.Tier)]
viol = (cap.PeakPowerWatts - cap.CapW > 1e-9).sum()
print(f"cap violations across all constrained solutions: {viol}")
cells = cap[cap.Tier.isin(TIERS)].groupby(['ScenarioName','BaseArm','Tier']).size()
expected = 3*7*len(TIERS)
print(f"non-empty (scenario x arm x tier) cells: {len(cells)} of {expected}"
      + ("   <- EMPTY CELLS PRESENT" if len(cells) < expected else ""))
print(f"\n{'Scenario':<12}" + "".join(f"{t:>12}" for t in TIERS) + "   <- solutions produced")
for s in SCN:
    print(f"{s:<12}", end='')
    for t in TIERS:
        n = len(cap[(cap.ScenarioName==s)&(cap.Tier==t)])
        print(f"{n:>12}" if n else f"{'EMPTY':>12}", end='')
    print()
print(f"\n{'Scenario':<12}" + "".join(f"{t:>12}" for t in TIERS) + "   <- arms with output (of 7)")
for s in SCN:
    print(f"{s:<12}", end='')
    for t in TIERS:
        k = cap[(cap.ScenarioName==s)&(cap.Tier==t)].BaseArm.nunique()
        print(f"{k:>12}", end='')
    print()
print("\ncaps (W):")
for s in SCN:
    print(f"  {s:<12} P_ref {pref[s]:8.0f} | " +
          " ".join(f"{t}={capmap[(s,t)]:.0f}" for t in TIERS))

print("\n" + "="*70); print("2. COST OF THE CAP — mean over seeds (robust)"); print("="*70)
print("\n| Scenario | uncapped | " + " | ".join(TIERS) + " |")
print("|---" * (len(TIERS)+2) + "|")
for s in SCN:
    sub = d[d.ScenarioName==s]
    u = sub[sub.Tier.isna()].groupby('Seed').WaitingTime.min().mean()
    row = f"| {s} | {u:.3f}s |"
    for t in TIERS:
        g = sub[sub.Tier==t].groupby('Seed').WaitingTime.min()
        row += f" {g.mean():.3f}s ({g.mean()/u-1:+.1%}) |" if len(g) else " n/a |"
    print(row)

print("\nwith seed spread (mean +/- sd):")
for s in SCN:
    sub=d[d.ScenarioName==s]
    u=sub[sub.Tier.isna()].groupby('Seed').WaitingTime.min()
    print(f"  {s:<12} uncapped {u.mean():.3f}+/-{u.std():.3f}", end='')
    for t in TIERS:
        g=sub[sub.Tier==t].groupby('Seed').WaitingTime.min()
        print(f" | {t} {g.mean():.3f}+/-{g.std():.3f}" if len(g) else f" | {t} n/a", end='')
    print()

print("\n" + "="*70); print("3. MONOTONICITY (min vs mean)"); print("="*70)
for s in SCN:
    sub=d[d.ScenarioName==s]
    for stat,f in [('MIN',np.min),('MEAN',np.mean)]:
        v=[f(sub[sub.Tier==t].groupby('Seed').WaitingTime.min()) for t in TIERS
           if len(sub[sub.Tier==t])]
        mono = all(v[i] <= v[i+1]+1e-9 for i in range(len(v)-1))
        print(f"  {s:<12}{stat:<6}" + "".join(f"{x:>9.3f}" for x in v) + f"   monotone={mono}")

print("\nadjacent-tier paired tests (are neighbouring tiers separable?):")
for s in SCN:
    for a,b in zip(TIERS, TIERS[1:]):
        x=d[(d.ScenarioName==s)&(d.Tier==a)].groupby('Seed').WaitingTime.min()
        y=d[(d.ScenarioName==s)&(d.Tier==b)].groupby('Seed').WaitingTime.min()
        common=x.index.intersection(y.index)
        if len(common)<5: continue
        p=stats.wilcoxon(x[common],y[common]).pvalue
        print(f"  {s:<12}{a} vs {b}: diff {y[common].mean()-x[common].mean():+.3f}s  p={p:.3f}"
              + ("" if p<0.05 else "   <- NOT separable"))

print("\n" + "="*70); print("4. ENERGY — real cost, or search artifact?"); print("="*70)
for s in SCN:
    base=d[(d.ScenarioName==s)&(d.Tier.isna())]
    r=base.loc[base.Energy.idxmin()]
    print(f"\n{s}: best uncapped energy {r.Energy:.6f} at peak {r.PeakPowerWatts:.0f} W")
    for t in TIERS:
        c=capmap[(s,t)]
        g=d[(d.ScenarioName==s)&(d.Tier==t)].Energy
        fits = r.PeakPowerWatts <= c
        print(f"  {t} cap {c:7.0f}W: uncapped-best fits? {str(fits):5}"
              + (f"  constrained best {g.min():.6f} ({g.min()/r.Energy-1:+.2%})" if len(g) else "  EMPTY"))
print("\n  -> 'fits=True' means the cap does NOT forbid the energy optimum,")
print("     so any penalty there is under-convergence, not a cost of the cap.")

print("\n" + "="*70); print("5. HV SIGN CHECK"); print("="*70)
qi=f'{d_dir}/quality_indicators_all_scenarios.csv'
if os.path.exists(qi):
    q=pd.read_csv(qi)
    q['Tier']=q.Algorithm.str.extract(r'_(PC\d+)$')[0]
    q['BaseArm']=q.Algorithm.str.replace(r'_PC\d+$','',regex=True)
    q['Sc']=q.Scenario.map({1:'Balanced',2:'GPU_Stress',3:'CPU_Stress'})
    print(f"NaN HV rows: {q.HV_fixed.isna().sum()} (expected >0 only if a tier was infeasible)")
    up=tot=0
    for t in TIERS:
        for s in SCN:
            for a in q.BaseArm.unique():
                u=q[(q.Sc==s)&(q.BaseArm==a)&(q.Tier.isna())].HV_fixed.mean()
                c=q[(q.Sc==s)&(q.BaseArm==a)&(q.Tier==t)].HV_fixed.mean()
                if u and u>0 and np.isfinite(c):
                    tot+=1; up += c>u
    print(f"cells where HV goes UP under a cap: {up}/{tot}  "
          f"({up/tot*100:.0f}%)   <- wrong sign" if tot else "no cells")
else:
    print("quality_indicators_all_scenarios.csv not found")
