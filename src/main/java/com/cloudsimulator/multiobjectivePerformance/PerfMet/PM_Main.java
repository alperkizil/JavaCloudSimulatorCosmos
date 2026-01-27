package PerfMet;

import com.sun.org.apache.bcel.internal.generic.AALOAD;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

/**
 *
 * @author kazim.erdogdu
 */
public class PM_Main {

    public static ArrayList<String> getFileList(String folder) {
        ArrayList<String> list = new ArrayList<>();
        File files = new File(folder);
        File[] listOfFiles = files.listFiles();
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                list.add(listOfFiles[i].getName());
            }
        }
        return list;
    }

    public static void run(){
       String folder = "paretoFiles";
        ArrayList<String> fileList = getFileList(folder);
        ArrayList<ArrayList<Double>> A = MyFileReader.getPareto(folder + "/" + fileList.get(0));
        ArrayList<ArrayList<Double>> B = MyFileReader.getPareto(folder + "/" + fileList.get(1));
        ArrayList<ArrayList<Double>> Tr = MyFileReader.getPareto(folder + "/" + fileList.get(2));
        ArrayList<ArrayList<ArrayList<Double>>> allParetos = new ArrayList<>();
        allParetos.add(A);
        allParetos.add(B);
        allParetos.add(Tr);
        PerformanceMetrics pm = new PerformanceMetrics(allParetos);
        System.out.println("HV(A) = " + pm.HV(0));
        System.out.println("HV(B) = " + pm.HV(1));
        //System.out.println("HV(Tr)" + pm.HV(2));
        System.out.println("Spread(A) = " + pm.Spacing(0));
        System.out.println("Spread(B) = " + pm.Spacing(1));
        System.out.println("IGD(A) = " + pm.IGD(0));
        System.out.println("IGD(B) = " + pm.IGD(1));
        System.out.println("C-Metric(A,B) = " + pm.C_Metric(0, 1));
        System.out.println("C-Metric(B,A) = " + pm.C_Metric(1, 0));
         
    }
    
    public static void main(String[] args) {
        run();
    }

}
