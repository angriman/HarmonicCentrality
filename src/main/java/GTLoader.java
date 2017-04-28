import com.google.gson.JsonSerializer;
import jdk.nashorn.internal.parser.JSONParser;
import org.json.JSONObject;
import sun.misc.IOUtils;

import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Created by Eugenio on 4/28/17.
 */
public class GTLoader {
    private String graphName;
    private int[] nodes;
    private double[] closeness;
    
    public GTLoader() {
        graphName = "";
    }
    
    public GTLoader(String graphName) {
        this.graphName = graphName;
    }

    public void load() {
        InputStream inputStream = JSONParser.class.getResourceAsStream("./Ground Truth/" + graphName + ".json");
        JSONObject obj = new JSONObject(inputStream.toString());
        closeness = (double[]) obj.get("closeness");
        nodes = (int[]) obj.get("nodes");

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
