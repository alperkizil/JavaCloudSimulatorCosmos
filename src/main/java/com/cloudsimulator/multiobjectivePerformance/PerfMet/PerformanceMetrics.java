package PerfMet;

import java.util.ArrayList;

/**
 *
 * @author kazim.erdogdu
 */
public class PerformanceMetrics {

    double f1min, f1max, f2min, f2max;
    ArrayList<ArrayList<ArrayList<Double>>> allParetos;
    ArrayList<ArrayList<ArrayList<Double>>> normalizedParetos;

    public PerformanceMetrics(ArrayList<ArrayList<ArrayList<Double>>> allParetos) {
        for (int i = 0; i < allParetos.size(); i++) {
            allParetos.get(i).sort(new FitnessComparator());
        }
        this.allParetos = allParetos;
        findMinMax();
        normalize();
    }

    private void findMinMax() {
        f1min = f1max = allParetos.get(0).get(0).get(0); // A[0][0]
        f2min = f2max = allParetos.get(0).get(0).get(1); // A[0][1]
        // Since A, B and Tr are sorted, we only check the first and last elements in them
        // e.g. A = {first=(10,75),second=(12, 71),...,last=(100, 5)}
        for (int i = 0; i < allParetos.size(); i++) { // A, B, Tr
            int first = 0, last = allParetos.get(i).size() - 1;
            // f1 - first - min
            if (allParetos.get(i).get(first).get(0) < f1min) { // A[0][0] or B[0][0] or Tr[0][0] 
                f1min = allParetos.get(i).get(first).get(0);
            }
            // f1 - last - max
            if (allParetos.get(i).get(last).get(0) > f1max) { // A[last][0] or B[last][0] or Tr[last][0] 
                f1max = allParetos.get(i).get(last).get(0);
            }
            // f2 - first - min
            if (allParetos.get(i).get(last).get(1) < f2min) { // A[0][1] or B[0][1] or Tr[0][1] 
                f2min = allParetos.get(i).get(last).get(1);
            }
            // f2 - last - max
            if (allParetos.get(i).get(first).get(1) > f2max) { // A[last][1] or B[last][1] or Tr[last][1] 
                f2max = allParetos.get(i).get(first).get(1);
            }
        }
    }

    private void normalize() {
        normalizedParetos = new ArrayList<>();
        for (int i = 0; i < allParetos.size(); i++) { // A, B, Tr
            ArrayList<ArrayList<Double>> normalizedP = new ArrayList<>(); // nA, nB, nTr
            for (ArrayList<Double> solution : allParetos.get(i)) {
                double nf1 = (solution.get(0) - f1min) / (f1max - f1min);
                double nf2 = (solution.get(1) - f2min) / (f2max - f2min);
                ArrayList<Double> nSolution = new ArrayList<>();
                nSolution.add(nf1);
                nSolution.add(nf2);
                normalizedP.add(nSolution);
            }
            normalizedParetos.add(normalizedP);
        }
    }

    public double HV(int paretoIndex) {
        ArrayList<ArrayList<Double>> p = normalizedParetos.get(paretoIndex);
        double volume = Math.abs(1.0 - p.get(0).get(0)) * Math.abs(1.0 - p.get(0).get(1));
        for (int i = 1; i < p.size(); i++) {
            volume += Math.abs(1.0 - p.get(i).get(0)) * Math.abs(p.get(i - 1).get(1) - p.get(i).get(1));
        }
        return volume;
    }

    private double euclid(ArrayList<Double> x, ArrayList<Double> y) {
        return Math.sqrt(Math.pow(x.get(0) - y.get(0), 2) + Math.pow(x.get(1) - y.get(1), 2));
    }

    public double IGD(int paretoIndex) {
        double distance = 0.0;
        ArrayList<ArrayList<Double>> pareto = normalizedParetos.get(paretoIndex);
        for (ArrayList<Double> trueParetoSolution : normalizedParetos.get(2)) {
            double minDistance = Double.MAX_VALUE;
            for (ArrayList<Double> paretoSolution : pareto) {
                double d = euclid(trueParetoSolution, paretoSolution);
                if (d < minDistance) {
                    minDistance = d;
                }
            }
            distance += minDistance;
        }

        return (double) distance / normalizedParetos.get(2).size();
    }

    public double C_Metric(int first, int second) {
        int count = 0;
        for (ArrayList<Double> secondParetoSolution : normalizedParetos.get(second)) {
            for (ArrayList<Double> firstParetoSolution : normalizedParetos.get(first)) {
                if (Dominance.compare(firstParetoSolution, secondParetoSolution) == -1) {
                    count++;
                    break;
                }
            }
        }
        return (double) count / normalizedParetos.get(second).size();
    }

    public double Spacing(int paretoIndex) {
        // Since normalizedParetos is sorted, 
        // we can just check min(|i-1, i|,|i,i+1|) in total manhattan distance
        ArrayList<ArrayList<Double>> p = normalizedParetos.get(paretoIndex);
        ArrayList<Double> distance = new ArrayList<>();

        // Finding distances of each solution
        distance.add(Math.abs(p.get(1).get(0) - p.get(0).get(0))
                + Math.abs(p.get(0).get(1) - p.get(1).get(1)));
        double total = distance.get(0);
        double prevDist = distance.get(0);
        for (int i = 1; i < p.size() - 1; i++) {
            double nextDist = Math.abs(p.get(i + 1).get(0) - p.get(i).get(0))
                    + Math.abs(p.get(i).get(1) - p.get(i + 1).get(1));
            distance.add(Math.min(prevDist, nextDist));
            total += distance.get(i);
            prevDist = nextDist;
        }
        int last = p.size() - 1;
        distance.add(p.get(last).get(0) - p.get(last - 1).get(0)
                + p.get(last - 1).get(1) - p.get(last).get(1));
        total += distance.get(last);
        double dAvg = (double) total / distance.size();

        double space = 0;
        for (Double d : distance) {
            space += Math.pow(dAvg - d, 2);
        }
        space = Math.sqrt((1.0 / (double)(p.size() - 1)) * space);
        return space;
    }
}
