import com.google.gson.JsonSerializer;
import jdk.nashorn.internal.parser.JSONParser;
import org.apache.commons.lang.ArrayUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Eugenio on 4/28/17.
 */
public class GTLoader {
    private String graphName;
    private int[] nodes;
    private final int numNodes;
    private double[] closeness;
    
    public GTLoader() {
        graphName = "";
        numNodes = 0;
    }
    
    public GTLoader(String graphName, int numNodes) {
        this.graphName = graphName;
        this.numNodes = numNodes;
        this.nodes = new int[this.numNodes];
        this.closeness = new double[this.numNodes];
    }

    public void load() throws IOException {
        String path = "./Ground Truth/" + graphName + ".json";
        String text = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        JSONObject obj = new JSONObject(text);

        List<Object> jNodes = ((JSONArray) obj.get("nodes")).toList();
        List<Object> jClos = ((JSONArray) obj.get("closeness")).toList();
        for (int i = 0; i < this.numNodes; ++i) {
            nodes[i] = (int)jNodes.get(i);
            closeness[i] = (double)jClos.get(i);
        }
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
