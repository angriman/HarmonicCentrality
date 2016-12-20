import it.unimi.dsi.fastutil.io.BinIO;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

/**
 * Created by eugenio on 22/11/16.
 */

/**
 * Reads results files and computes performance metrics
 */
public class Evaluate {
    public static void main(String[] args) throws IOException {
        String resultPath = "./results/Progressive/facebook/0.1/";
        File[] jsonList = (new File(resultPath)).listFiles();
        double[] centralities = new double[0];
        assert jsonList != null;
        for (File currentFile : jsonList) {
            if (currentFile.isFile()) {
                if (currentFile.getName().endsWith(".json")) {
                    Scanner scanner = new Scanner(currentFile);
                    JSONObject currentExperiment = new JSONObject(scanner.nextLine());
                    JSONObject tables = currentExperiment.getJSONObject("tables");
                    JSONArray centralityTable = tables.getJSONArray("Centralities");
                    for (int i = 0; i < centralityTable.length(); ++i) {

                        JSONObject currentCentrality = (JSONObject)centralityTable.get(i);
                        String centralityFile = currentCentrality.getString("Values");
                        centralities = BinIO.loadDoubles(centralityFile);
                    }

                }
            }
        }

        String groundTruthPath = "./results/Naive/facebook/2016-12-16T15:45:51.876+01:00-FE5925BB1B98E9F64E49BAEB57DA7490719601B038BF2F8FE68839BF33BA7C86.json";
        File groundTruthFile = new File(groundTruthPath);
        Scanner scanner = new Scanner(groundTruthFile);
        JSONObject gtExperiment = new JSONObject(scanner.nextLine());
        JSONObject tables = gtExperiment.getJSONObject("tables");
        JSONArray centralityTable = tables.getJSONArray("Centralities");
        JSONObject currentCentrality = (JSONObject)centralityTable.get(0);
        String centralityFile = currentCentrality.getString("Values");
        double[] groundTruth = BinIO.loadDoubles(centralityFile);
        double[] absErr = new double[groundTruth.length];
        double[] relErr = new double[groundTruth.length];

        for (int i = 0; i < groundTruth.length; ++i) {
            groundTruth[i] /= (double)(groundTruth.length - 1);
            absErr[i] = Math.abs(groundTruth[i] - centralities[i]);
            relErr[i] = groundTruth[i] == 0 ? centralities[i] : Math.abs(groundTruth[i] - centralities[i]) / groundTruth[i];
        }

        JSONArray abs = new JSONArray(absErr);
        JSONArray rel = new JSONArray(relErr);
        JSONObject errors = new JSONObject();
        errors.put("Absolute", abs);
        errors.put("Relative", rel);


    }

    double[] getGT() throws IOException {
        String groundTruthPath = "./results/Naive/wordassociation-2011/2016-12-19T12:53:44.645+01:00-F6C03DC9564E2B6214C1D825C341CF23D2F6AFE742B1F29D89915C120E2A1D7F.json";
        File groundTruthFile = new File(groundTruthPath);
        Scanner scanner = new Scanner(groundTruthFile);
        JSONObject gtExperiment = new JSONObject(scanner.nextLine());
        JSONObject tables = gtExperiment.getJSONObject("tables");
        JSONArray centralityTable = tables.getJSONArray("Centralities");
        JSONObject currentCentrality = (JSONObject)centralityTable.get(0);
        String centralityFile = currentCentrality.getString("Values");
        return BinIO.loadDoubles(centralityFile);
    }
}
