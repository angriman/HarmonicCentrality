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

        String groundTruthPath = "./results/Naive/wordassociation-2011/2017-01-11T15:42:47.860+01:00-89C34CBA90A22CF8C843EEEA44ABC30E66A3FA8C15E5880F7CFABE7C0CDF1531.json";
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
        String groundTruthPath = "./results/Naive/wordassociation-2011/2017-01-15T14:41:51.709+01:00-B4D3321B2FF61C3F990A5CEB0AFADDDAFAB594475D83D168DACD02458D80FB88.json";
        File groundTruthFile = new File(groundTruthPath);
        Scanner scanner = new Scanner(groundTruthFile);
        JSONObject gtExperiment = new JSONObject(scanner.nextLine());
        JSONObject tables = gtExperiment.getJSONObject("tables");
        JSONArray centralityTable = tables.getJSONArray("Centralities");
        JSONObject currentCentrality = (JSONObject)centralityTable.get(0);
        String centralityFile = currentCentrality.getString("Values");
        return BinIO.loadDoubles(centralityFile);
    }

    double[] getClos() throws IOException {
        String gtPath = "./results/Naive/wordassociation-2011/2017-01-21T17:29:08.551+01:00-8B09422DE87510A975D4E16D4398A70F3D4C55261F987DCEE58D0ED04AA6C87B.json";
        File gtFile = new File(gtPath);
        Scanner scanner = new Scanner(gtFile);
        JSONObject gtExperiment = new JSONObject(scanner.nextLine());
        JSONObject tables = gtExperiment.getJSONObject("tables");
        JSONArray centralityTable = tables.getJSONArray("Centralities");
        JSONObject currentCentrality = (JSONObject)centralityTable.get(0);
        String centralityFile = currentCentrality.getString("Closeness");
        return BinIO.loadDoubles(centralityFile);
    }
}
