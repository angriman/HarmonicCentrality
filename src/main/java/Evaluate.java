import it.unimi.dsi.fastutil.io.BinIO;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Scanner;

/**
 * Created by eugenio on 22/11/16.
 */

/**
 * Reads results files and computes performance metrics
 */
public class Evaluate {
    public static void main(String[] args) throws IOException {
        String resultPath = "./results/Borassi/dummygraph/1/";
        File[] jsonList = (new File(resultPath)).listFiles();
        for (File currentFile : jsonList) {
            if (currentFile.isFile()) {
                if (currentFile.getName().endsWith(".json")) {
                    Scanner scanner = new Scanner(currentFile);
                    JSONObject currentExperiment = new JSONObject(scanner.nextLine());
                    Iterator<String> set = currentExperiment.keys();
                    JSONObject tables = currentExperiment.getJSONObject("tables");
                    JSONArray centralityTable = tables.getJSONArray("Centralities");
                    for (int i = 1; i < centralityTable.length(); ++i) {
                       //System.out.println(centralityTable.get(i));
                        JSONObject currentCentrality = (JSONObject)centralityTable.get(i);
                        String nodesFile = currentCentrality.getString("Nodes");
                        String centralityFile = currentCentrality.getString("Values");
                        double[] centralities = BinIO.loadDoubles(centralityFile);
                        double[] nodes = BinIO.loadDoubles(nodesFile);
                        System.out.println(Arrays.toString(centralities));
                    }

                }
            }
        }
    }
}
