import it.unimi.dsi.webgraph.ArcListASCIIGraph;
import it.unimi.dsi.webgraph.BVGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;

import java.io.IOException;

/**
 * Created by Eugenio on 4/10/17.
 */
public class GraphBuilder {
    public static void main(String[] args) throws IOException {
        String graphName = "twitter";
        String graphBaseName = "./Graphs/"+graphName+"/"+graphName+".txt";
        ImmutableGraph graph = ArcListASCIIGraph.loadOffline(graphBaseName);
        BVGraph.store(graph, "./Graphs/"+graphName+"/"+graphName);
    }
}