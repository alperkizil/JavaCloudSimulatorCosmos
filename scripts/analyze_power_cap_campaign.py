#!/usr/bin/env python3
"""Post-campaign analysis for the 90/80/70/60/50 power-cap ladder.

Answers, in order:
  1. Is the 50% tier feasible at all?  (the open question)
  2. What does each tier cost in waiting time?  (mean over seeds, not min)
  3. Is the cost monotone?  (min-over-seeds was not, in the 28 Aug run)
  4. Is the energy "cost" real or a search artifact?
  5. Does HV report the wrong sign?
  6. Does constrained search beat post-hoc filtering of the uncapped runs?
  7. Do the base-vs-cap contrasts survive Holm on the *planned* family?

Every cost figure is computed on cap-FEASIBLE solutions only. pareto_3d_all.csv
is the raw archive dump: when a run finds nothing feasible, Deb's rules retain
its least-violating solutions, so that file legitimately contains points above
the cap. Section 1 audits them; nothing downstream of it uses them.
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
LADDER = ['PC90','PC80','PC70','PC60','PC50']
present = set(d.Tier.dropna())
TIERS = [t for t in LADDER if t in present]
if present - set(LADDER):
    print(f"!! WARNING: this folder has tiers outside the {'/'.join(LADDER)} ladder: "
          f"{sorted(present - set(LADDER))}\n   They are IGNORED below, so every count "
          f"here is a lower bound for this campaign.\n")
SCN   = ['Balanced','GPU_Stress','CPU_Stress']
d['CapW'] = [capmap.get((s_, t_), np.nan) for s_, t_ in zip(d.ScenarioName, d.Tier)]
d['Feasible'] = np.where(d.Tier.isna(), True, d.PeakPowerWatts <= d.CapW + 1e-9)
D = d[d.Feasible]           # feasible-only view: every cost figure below uses this
print(f"tiers present: {TIERS}\nsolutions: {len(d)}  (cap-feasible: {int(d.Feasible.sum())})\n")

print("="*70); print("1. FEASIBILITY — is 50% reachable?"); print("="*70)
cap = d[d.Tier.notna()].copy()
nviol = int((~cap.Feasible).sum())
print(f"solutions above their own cap in pareto_3d_all.csv: {nviol} of {len(cap)}"
      f" ({100*nviol/len(cap):.3f}%)")
if nviol:
    v = cap[~cap.Feasible]
    print("  these are least-violating points retained where a run found nothing feasible:")
    for (s_, a_, t_), g in v.groupby(['ScenarioName','BaseArm','Tier']):
        c = capmap[(s_, t_)]
        print(f"    {s_:<11} {a_:<26} {t_}  n={len(g):<3} max excess "
              f"{(g.PeakPowerWatts-c).max():7.1f} W ({100*(g.PeakPowerWatts-c).max()/c:5.2f}% of cap)")
    stolen = sum(not d.loc[g.WaitingTime.idxmin(), 'Feasible']
                 for _, g in cap.groupby(['ScenarioName','Tier','Seed']))
    print(f"  union best-WT slots won by an infeasible point: {stolen} of "
          f"{cap.groupby(['ScenarioName','Tier','Seed']).ngroups}"
          + ("   <- COST TABLE IS CONTAMINATED" if stolen else
             "   -> the cost table is unaffected by them"))
runs = cap.groupby(['ScenarioName','BaseArm','Tier','Seed']).Feasible.sum()
print(f"\nruns that found NOTHING feasible: {int((runs==0).sum())} of {len(runs)}"
      "   (must appear as NaN indicators, not as HV 0)")
if (runs==0).any():
    print(runs[runs==0].reset_index().groupby(['ScenarioName','BaseArm','Tier'])
          .size().to_string().replace('\n', '\n    ').rjust(4))
cells = cap[cap.Feasible & cap.Tier.isin(TIERS)].groupby(['ScenarioName','BaseArm','Tier']).size()
expected = 3*7*len(TIERS)
print(f"\ncells with >=1 FEASIBLE solution: {len(cells)} of {expected}"
      + ("   <- DEAD CELLS PRESENT" if len(cells) < expected else ""))
print(f"\n{'Scenario':<12}" + "".join(f"{t:>12}" for t in TIERS) + "   <- FEASIBLE solutions produced")
for s in SCN:
    print(f"{s:<12}", end='')
    for t in TIERS:
        n = int(cap[(cap.ScenarioName==s)&(cap.Tier==t)].Feasible.sum())
        print(f"{n:>12}" if n else f"{'EMPTY':>12}", end='')
    print()
print(f"\n{'Scenario':<12}" + "".join(f"{t:>12}" for t in TIERS) + "   <- arms with output (of 7)")
for s in SCN:
    print(f"{s:<12}", end='')
    for t in TIERS:
        k = cap[(cap.ScenarioName==s)&(cap.Tier==t)&cap.Feasible].BaseArm.nunique()
        print(f"{k:>12}" if k==7 else f"{str(k)+' <-':>12}", end='')
    print()
print("\ncaps (W):")
for s in SCN:
    print(f"  {s:<12} P_ref {pref[s]:8.0f} | " +
          " ".join(f"{t}={capmap[(s,t)]:.0f}" for t in TIERS))

print("\n" + "="*70); print("2. COST OF THE CAP — mean over seeds (robust)"); print("="*70)
print("\n| Scenario | uncapped | " + " | ".join(TIERS) + " |")
print("|---" * (len(TIERS)+2) + "|")
for s in SCN:
    sub = D[D.ScenarioName==s]
    u = sub[sub.Tier.isna()].groupby('Seed').WaitingTime.min().mean()
    row = f"| {s} | {u:.3f}s |"
    for t in TIERS:
        g = sub[sub.Tier==t].groupby('Seed').WaitingTime.min()
        mark = "" if len(g)==10 else f" [{len(g)}/10 seeds]"
        row += f" {g.mean():.3f}s ({g.mean()/u-1:+.1%}){mark} |" if len(g) else " none |"
    print(row)
print("\n(cap-feasible solutions only; a seed with no feasible solution is excluded,")
print(" which biases a tier OPTIMISTICALLY -- check the seed counts above)")

print("\nwith seed spread (mean +/- sd):")
for s in SCN:
    sub=D[D.ScenarioName==s]
    u=sub[sub.Tier.isna()].groupby('Seed').WaitingTime.min()
    print(f"  {s:<12} uncapped {u.mean():.3f}+/-{u.std():.3f}", end='')
    for t in TIERS:
        g=sub[sub.Tier==t].groupby('Seed').WaitingTime.min()
        print(f" | {t} {g.mean():.3f}+/-{g.std():.3f}" if len(g) else f" | {t} n/a", end='')
    print()

print("\n" + "="*70); print("3. MONOTONICITY (min vs mean)"); print("="*70)
for s in SCN:
    sub=D[D.ScenarioName==s]
    for stat,f in [('MIN',np.min),('MEAN',np.mean)]:
        v=[f(sub[sub.Tier==t].groupby('Seed').WaitingTime.min()) for t in TIERS
           if len(sub[sub.Tier==t])]
        mono = all(v[i] <= v[i+1]+1e-9 for i in range(len(v)-1))
        print(f"  {s:<12}{stat:<6}" + "".join(f"{x:>9.3f}" for x in v) + f"   monotone={mono}")

print("\nadjacent-tier paired tests (are neighbouring tiers separable?):")
for s in SCN:
    for a,b in zip(TIERS, TIERS[1:]):
        x=D[(D.ScenarioName==s)&(D.Tier==a)].groupby('Seed').WaitingTime.min()
        y=D[(D.ScenarioName==s)&(D.Tier==b)].groupby('Seed').WaitingTime.min()
        common=x.index.intersection(y.index)
        if len(common)<5: continue
        p=stats.wilcoxon(x[common],y[common]).pvalue
        print(f"  {s:<12}{a} vs {b}: diff {y[common].mean()-x[common].mean():+.3f}s  p={p:.3f}"
              + ("" if p<0.05 else "   <- NOT separable"))

print("\n" + "="*70); print("4. ENERGY — real cost, or search artifact?"); print("="*70)
for s in SCN:
    base=D[(D.ScenarioName==s)&(D.Tier.isna())]
    r=base.loc[base.Energy.idxmin()]
    print(f"\n{s}: best uncapped energy {r.Energy:.6f} at peak {r.PeakPowerWatts:.0f} W")
    for t in TIERS:
        c=capmap[(s,t)]
        g=D[(D.ScenarioName==s)&(D.Tier==t)].Energy
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
    # empty runs are DROPPED from this file and NaN-ed in the per-cap metrics;
    # either is honest, but only the second is visible here, so cross-check both.
    missing = 3 * 10 * len(set(q.Algorithm)) - len(q)
    nan_bycap = 0
    for i in (1, 2, 3):
        f_ = f'{d_dir}/scenario_{i}_performance_metrics_by_cap.csv'
        if os.path.exists(f_):
            m_ = pd.read_csv(f_)
            nan_bycap += int(m_[m_.Seed.astype(str).str.isdigit()].HV_fixed.isna().sum())
    print(f"runs dropped from quality_indicators (no feasible solution): {missing}")
    print(f"runs NaN-ed in scenario_N_performance_metrics_by_cap.csv:    {nan_bycap}"
          + ("   <- consistent" if missing == nan_bycap else "   <- MISMATCH"))
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

print("\n" + "="*70)
print("6. CONSTRAINED SEARCH vs POST-HOC FILTERING")
print("="*70)
unc = D[D.Tier.isna()]
print("\nFraction of each UNCAPPED arm's output that clears the cap")
print("(what a filter-only baseline would have left to work with):")
print(f"{'Arm':<28}" + "".join(f"{t:>9}" for t in TIERS))
for a in sorted(unc.BaseArm.unique()):
    row = f"{a:<28}"
    for t in TIERS:
        num = den = 0
        for s in SCN:
            g = unc[(unc.BaseArm==a) & (unc.ScenarioName==s)]
            num += int((g.PeakPowerWatts <= capmap[(s,t)]).sum()); den += len(g)
        row += f"{num/den:>9.3f}" if den else f"{'-':>9}"
    print(row)

print("\nSeed-runs where filtering keeps NOTHING (of 3 scenarios x 7 arms x 10 seeds):")
for t in TIERS:
    empty = tot = 0
    for (s, a, sd), g in unc.groupby(['ScenarioName','BaseArm','Seed']):
        tot += 1; empty += int((g.PeakPowerWatts <= capmap[(s,t)]).sum()) == 0
    print(f"  {t}: {empty}/{tot} empty ({100*empty/tot:.0f}%)")

print("\nBest cap-feasible waiting time, union over arms, mean over seeds:")
print(f"{'Scenario':<12}{'Tier':<7}{'filter-only':>20}{'constrained':>13}{'gain':>9}")
for s in SCN:
    for t in TIERS:
        c  = capmap[(s,t)]
        fu = unc[(unc.ScenarioName==s) & (unc.PeakPowerWatts<=c)].groupby('Seed').WaitingTime.min()
        cs = D[(D.ScenarioName==s) & (D.Tier==t)].groupby('Seed').WaitingTime.min()
        lhs  = f"{fu.mean():.3f}s [{len(fu)}/10]" if len(fu) else "none [0/10]"
        gain = f"{(1-cs.mean()/fu.mean())*100:>8.1f}%" if len(fu) and len(cs) else f"{'n/a':>9}"
        rhs  = f"{cs.mean():>12.3f}s" if len(cs) else f"{'none':>13}"
        print(f"{s:<12}{t:<7}{lhs:>20}{rhs}{gain}")
print("\n'none' on the left is the load-bearing result: there the unconstrained run")
print("never visits the feasible region, so filtering has nothing to select from.")

print("\n" + "="*70)
print("7. HOLM FAMILY — planned contrasts only")
print("="*70)
st = f'{d_dir}/statistical_tests_summary.csv'
if os.path.exists(st):
    T = pd.read_csv(st)
    T['baseA'] = T.A.str.replace(r'_PC\d+$','',regex=True)
    T['baseB'] = T.B.str.replace(r'_PC\d+$','',regex=True)
    T['tierA'] = T.A.str.extract(r'_(PC\d+)$')[0]
    T['tierB'] = T.B.str.extract(r'_(PC\d+)$')[0]
    plan = T[(T.baseA==T.baseB) & T.tierA.isna() & T.tierB.notna()]
    print(f"\nshipped family: {len(T)//T.metric.nunique()} comparisons per metric "
          f"(all pairs over {len(set(T.A)|set(T.B))} arms)")
    print(f"planned family: {len(plan)//max(plan.metric.nunique(),1)} base-vs-cap contrasts per metric\n")
    print(f"{'metric':<8}{'raw p<.05':>12}{'shipped Holm':>15}{'planned Holm':>15}")
    for m in sorted(T.metric.unique()):
        q = plan[plan.metric==m].dropna(subset=['p_raw']).sort_values('p_raw').reset_index(drop=True)
        n, run, adj = len(q), 0.0, []
        for i, pv in enumerate(q.p_raw):
            run = max(run, (n-i)*pv); adj.append(min(run, 1.0))
        raw     = int((q.p_raw < 0.05).sum())
        shipped = int(q['significant_0.05'].sum())
        planned = sum(1 for x in adj if x < 0.05)
        print(f"{m:<8}{f'{raw}/{n}':>12}{f'{shipped}/{n}':>15}{f'{planned}/{n}':>15}")
    print("\nThe shipped column corrects across every pairwise comparison, including")
    print("the ~96% nobody planned to make. Report the planned-family column, or")
    print("state the correction family explicitly.")
else:
    print("statistical_tests_summary.csv not found")
