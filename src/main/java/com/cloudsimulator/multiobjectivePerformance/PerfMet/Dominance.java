package PerfMet;

import java.util.ArrayList;

/**
 *
 * @author kazim.erdogdu
 */
public class Dominance {
    public static int compare(ArrayList<Double> f1, ArrayList<Double> f2){
        
        if (f1.get(0) < f2.get(0) && f1.get(1) < f2.get(1))
            return -1;
        else if (f2.get(0) < f1.get(0) && f2.get(1) < f1.get(1))
            return 1;
        else
            return 0;
    }
    
}
