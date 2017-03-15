import it.unimi.dsi.webgraph.ArcListASCIIGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;

import java.io.IOException;

/**
 * Created by eugenio on 09/02/17.
 */
public class GraphWriter {

    public static void main(String[] args) throws IOException {
        final String graphBasename = "./Graphs/wordassociation-2011/wordassociation-2011";
        ImmutableGraph graph = ImmutableGraph.loadMapped(graphBasename, null);
        ArcListASCIIGraph.store(graph, "./Graphs/wordassociation-2011/word");
    }
}
