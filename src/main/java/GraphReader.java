import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.Transform;

import java.io.IOException;

/**
 * Created by Eugenio on 3/24/17.
 */
public class GraphReader {
    private final ImmutableGraph graph;

    public GraphReader(String graphBasename, boolean mapped, boolean expand, ProgressLogger progressLogger) throws IOException {
        ImmutableGraph graph1 = mapped?ImmutableGraph.loadMapped(graphBasename, progressLogger):ImmutableGraph.load(graphBasename, progressLogger);
        graph1 = Transform.symmetrize(graph1);
        if(expand) {
            graph1 = (new ArrayListMutableGraph(graph1)).immutableView();
        }
        this.graph = graph1;
    }

    public ImmutableGraph getGraph() {
        return this.graph;
    }
}
