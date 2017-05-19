import org.apache.commons.lang.ArrayUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Wrote by Eugenio on 4/28/17.
 */
public class GTLoader {
    private String graphName;
    private int[] nodes;
    private final int numNodes;
    private double[] closeness;
    private int[] farness;

    GTLoader(String graphName, int numNodes) {
        this.graphName = graphName;
        this.numNodes = numNodes;
        this.farness = new int[this.numNodes];
        this.nodes = new int[this.numNodes];
        this.closeness = new double[this.numNodes];

    }

    void load() throws IOException {
        String path = "./Ground Truth/" + graphName + ".json";
        String text = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        JSONObject obj = new JSONObject(text);

        List<Object> jNodes = ((JSONArray) obj.get("nodes")).toList();
        List<Object> jClos = ((JSONArray) obj.get("closeness")).toList();
        List<Object> jFar  = ((JSONArray) obj.get("farness")).toList();
        for (int i = 0; i < this.numNodes; ++i) {
            nodes[i] = (int)jNodes.get(i);
           // closeness[i] = (double)jClos.get(i);
            farness[i] = (int)jFar.get(i);
        }
    }

    public int[] getTopKNodes(int k) {
        int to = k;
        double kth = closeness[nodes[k-1]];
        while (to < numNodes && closeness[nodes[to]] == kth) {
            ++to;
        }
        return ArrayUtils.subarray(nodes, 0, to);
    }

    int[] getFarness() {
        return farness;
    }

    public double[] getCloseness() {
        return closeness;   
    }

    public int[] getNodes() {
        return nodes;
    }

    public void setgraphName(String graphName) {
        this.graphName = graphName;
    }
}
