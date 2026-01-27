package PerfMet;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

/**
 *
 * @author kazim.erdogdu
 */
public class MyFileReader {
    
    static ArrayList<ArrayList<Double>> getPareto(String path){
        ArrayList<ArrayList<Double>> pareto =  new ArrayList<>();
        File f = new File(path);
        Scanner sc = null;
        try {
            sc = new Scanner(f);
        } catch (FileNotFoundException ex) {
            System.out.println(ex);
        }
        
        String[] data = null;
        sc.nextLine();
        while (sc.hasNextLine()) {
            //System.out.println(sc.nextLine());
            data = sc.nextLine().split(";");
            ArrayList<Double> row = new ArrayList<>();
            for (int i = 0; i < data.length; i++) {
                row.add(Double.parseDouble(data[i]));
            }
            pareto.add(row);
        }
        
        return pareto;
    }
    
}
