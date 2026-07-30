#!/usr/bin/env python3
"""Pre-study trace analysis for the multi-DC carbon/peak-power/migration proposal.

Computes, from the EuroSys'24 artifact's hourly carbon-intensity traces
(Sukprasert et al., https://zenodo.org/records/10790855 — CC-BY-4.0; file
shared_data/combined_carbon.csv, 123 zones, hourly, 2020-2022, gCO2/kWh, UTC):

  1. per-zone mean / CV / diurnal swing;
  2. per-portfolio "migration headroom": savings of an oracle that always runs in
     the hourly-cleanest zone vs. the best FIXED zone (upper bound on what
     spatial shifting/migration can add over perfect initial placement);
  3. leader-excluded headroom (proxy for a saturated/power-capped clean zone);
  4. spillover price: mean gap between cleanest and 2nd-cleanest zone (the
     marginal carbon cost of herding once the leader's cap binds);
  5. lead-share and lead-switches/day (how often the cleanest-zone identity
     rotates -> how often migration could ever pay);
  6. brute-force search of all 4-zone portfolios over GCP-region zones.

Usage:  python3 proposal_trace_preanalysis.py /path/to/combined_carbon.csv [YEAR]

Stdlib-only (no numpy/pandas). Results cited in
docs/proposals/MultiDC-Carbon-PeakPower-Migration-Proposal.md were produced
with YEAR=2022.
"""
import csv
import statistics as st
import sys
from itertools import combinations

CSV = sys.argv[1] if len(sys.argv) > 1 else "combined_carbon.csv"
YEAR = sys.argv[2] if len(sys.argv) > 2 else "2022"

# Zones that are Google Cloud regions per the artifact's gcp_dc_zonecode_mapper.json
# (so the artifact's real inter-region latency matrix covers them).
GCP_ZONES = ["GB", "FR", "PL", "DE", "ES", "NL", "FI", "CH", "BE", "IT-NO",
             "JP-TK", "JP-KN", "KR", "SG", "TW", "HK", "IN-MH", "AU-NSW",
             "CA-QC", "CA-ON", "BR-CS", "US-CAL-CISO", "US-TEX-ERCO", "US-MIDA-PJM"]

NAMED_PORTFOLIOS = {
    "S1 rotating-leaders ES/FI/CH/BE": ["ES", "FI", "CH", "BE"],
    "S1-alt global CISO/DE/KR/ERCO": ["US-CAL-CISO", "DE", "KR", "US-TEX-ERCO"],
    "S2 dominant-leader PL/DE/FR/GB": ["PL", "DE", "FR", "GB"],
    "S3 flat-control SG/TW/HK/IN-MH": ["SG", "TW", "HK", "IN-MH"],
    "heritage TR/GB/JP-TK/US-SE-SOCO": ["TR", "GB", "JP-TK", "US-SE-SOCO"],
}


def load():
    with open(CSV) as f:
        r = csv.reader(f)
        hdr = next(r)
        rows = [row for row in r if row[0].startswith(YEAR)]
    zones = [z for z in hdr[1:]]
    series = {}
    for z in zones:
        i = hdr.index(z)
        vals = [float(row[i]) if row[i] else None for row in rows]
        if all(v is not None for v in vals):  # keep fully-covered zones only
            series[z] = vals
    return series, rows


def zone_stats(series, rows, zones):
    print(f"{'zone':<13}{'mean':>7}{'CV':>7}   diurnal swing (min->max of hourly means)")
    for z in zones:
        v = series[z]
        m, s = st.mean(v), st.pstdev(v)
        by_hour = {}
        for t, row in enumerate(rows):
            by_hour.setdefault(int(row[0][11:13]), []).append(v[t])
        hm = [st.mean(x) for _, x in sorted(by_hour.items())]
        print(f"{z:<13}{m:>7.0f}{s / m:>7.2f}   {min(hm):>5.0f} -> {max(hm):>5.0f} gCO2/kWh"
              f" ({(max(hm) - min(hm)) / m * 100:.0f}% of mean)")


def portfolio_stats(series, zs):
    n = len(series[zs[0]])
    means = {z: st.mean(series[z]) for z in zs}
    best_fixed = min(means, key=means.get)
    mins, seconds, argmins = [], [], []
    for t in range(n):
        vals = sorted((series[z][t], z) for z in zs)
        mins.append(vals[0][0])
        seconds.append(vals[1][0])
        argmins.append(vals[0][1])
    headroom = (means[best_fixed] - st.mean(mins)) / means[best_fixed]
    switches = sum(a != b for a, b in zip(argmins, argmins[1:])) / (n / 24)
    spill = st.mean(seconds) - st.mean(mins)
    rest = [z for z in zs if z != best_fixed]
    bf2 = min(rest, key=lambda z: means[z])
    mins2 = [min(series[z][t] for z in rest) for t in range(n)]
    headroom_no_leader = (means[bf2] - st.mean(mins2)) / means[bf2]
    share = {z: argmins.count(z) / n for z in zs}
    lead = ",".join(f"{z}:{share[z]:.0%}" for z in sorted(zs, key=lambda z: -share[z])
                    if share[z] >= 0.01)
    return headroom, switches, spill, best_fixed, headroom_no_leader, lead


def brute_force(series, zones, top=8, subsample=3):
    n = len(series[zones[0]])
    sub = range(0, n, subsample)
    means_all = {z: st.mean(series[z]) for z in zones}
    scored = []
    for zs in combinations(zones, 4):
        bfm = min(means_all[z] for z in zs)
        tot = 0.0
        for t in sub:
            m = series[zs[0]][t]
            for z in zs[1:]:
                v = series[z][t]
                if v < m:
                    m = v
            tot += m
        scored.append(((bfm - tot / len(sub)) / bfm, zs))
    scored.sort(reverse=True)
    return scored[:top]


def main():
    series, rows = load()
    interest = sorted(set(GCP_ZONES) | {z for p in NAMED_PORTFOLIOS.values() for z in p})
    zone_stats(series, rows, [z for z in interest if z in series])

    print(f"\nNamed portfolios ({YEAR}):")
    print(f"{'portfolio':<34}{'headroom':>9}{'sw/day':>8}{'spill':>7}{'leader':>13}"
          f"{'noLeader':>10}  lead shares")
    for name, zs in NAMED_PORTFOLIOS.items():
        if any(z not in series for z in zs):
            print(f"{name:<34}  (zone missing in {YEAR} data)")
            continue
        h, sw, sp, bf, h2, lead = portfolio_stats(series, zs)
        print(f"{name:<34}{h:>8.1%}{sw:>8.1f}{sp:>6.0f}g{bf:>13}{h2:>9.1%}  [{lead}]")

    gcp = [z for z in GCP_ZONES if z in series]
    print(f"\nTop 4-zone portfolios by migration headroom (screened every {3}rd hour,"
          f" verified full-resolution):")
    for _, zs in brute_force(series, gcp):
        h, sw, sp, bf, h2, lead = portfolio_stats(series, list(zs))
        print(f"  {h:5.1%}  {'/'.join(zs):<38} sw/day={sw:4.1f} spill={sp:4.0f}g"
              f" noLeader={h2:5.1%}  [{lead}]")


if __name__ == "__main__":
    main()
