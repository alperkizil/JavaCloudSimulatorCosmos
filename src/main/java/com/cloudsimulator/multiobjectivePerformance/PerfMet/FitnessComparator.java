package PerfMet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 *
 * @author kazim.erdogdu
 */
public class FitnessComparator implements Comparator<ArrayList<Double>> {
    
    @Override
    public int compare(ArrayList<Double> f1, ArrayList<Double> f2) {
        if (f1.get(0) < f2.get(0)) {
            return -1;
        } else if (f1.get(0) > f2.get(0)) {
            return 1;
        } else {
            return 0;
        }
    }

}
