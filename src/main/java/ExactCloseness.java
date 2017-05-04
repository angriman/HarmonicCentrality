import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.Transform;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by Eugenio on 4/28/17.
 */
public class ExactCloseness {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        String grapName = "wordassociation-2011";
        String graphBasename = "./Graphs/" + grapName + "/" + grapName;
        ProgressLogger progressLogger = new ProgressLogger(LOGGER, "nodes");
        progressLogger.displayFreeMemory = progressLogger.displayLocalSpeed = true;
        ImmutableGraph graph = (new GraphReader(graphBasename, true, true, progressLogger)).getGraph();
        graph = Transform.symmetrize(graph);
        GeometricCentralities geo = new GeometricCentralities(graph, progressLogger);
        try {
            geo.compute();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        Sorter sorter = new Sorter(graph);
        Integer[] nodes = new Integer[graph.numNodes()];
        for (int i = 0; i < nodes.length; ++i) {
            nodes[i] = i;
        }
        double[] closeness = new double[graph.numNodes()];
        sorter.closenessSort(geo.closeness, nodes);

        for (int i = 0; i < nodes.length; ++i) {
            closeness[i] = geo.closeness[i];
        }

        String path = "./Ground Truth/"+ grapName+".json";
        JSONArray sortedNodes = new JSONArray(nodes);
        JSONArray sortedCloseness = new JSONArray(closeness);
        JSONObject obj = new JSONObject();
        obj.put("nodes", sortedNodes);
        obj.put("closeness", sortedCloseness);
        try (FileWriter file = new FileWriter(path)) {
            obj.write(file);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean isSorted(double[] arr) {
        double pred = arr[0];
        for (int i = 1; i < arr.length; ++i) {
            if (arr[i] > pred) {
                return false;
            }
            pred = arr[i];
        }
        return true;
    }
}
