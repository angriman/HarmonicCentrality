import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Created by Eugenio on 3/24/17.
 */
public class GraphReader {
    private final ImmutableGraph graph;

    public static void main(String args[]) {
        String graphName = "DC";
        try {
            ImmutableGraph g = ImmutableGraph.loadMapped("./Graphs/"+graphName+"/"+graphName, null);
            g = Transform.symmetrize(g);
            File output = new File("./Graphs/"+graphName+"/new_"+graphName+".txt");
            PrintWriter printWriter = new PrintWriter(output);

            for (int next = 0; next < g.numNodes(); ++next) {
                LazyIntIterator succ = g.successors(next);
                int s;
                while ((s = succ.nextInt()) != -1) {
                    printWriter.println(next + " " + s);
                }
            }

            printWriter.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }


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
