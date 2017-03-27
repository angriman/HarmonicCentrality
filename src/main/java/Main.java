import com.martiansoftware.jsap.*;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.Transform;
import it.unimi.dsi.webgraph.algo.ConnectedComponents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Created by Eugenio on 3/24/17.
 */
public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    /** The graph under examination. */
    private final ImmutableGraph graph = null;

    public static void main(String[] args) throws JSAPException, IOException {
        SimpleJSAP jsap = new SimpleJSAP(Main.class.getName(), "Computes positive centralities of a graph using multiple parallel breadth-first visits.\n\nPlease note that to compute negative centralities on directed graphs (which is usually what you want) you have to compute positive centralities on the transpose.",
                new Parameter[]{
                        new Switch("expand", 'e', "expand", "Expand the graph to increase speed (no compression)."),
                        new Switch("mapped", 'm', "mapped", "Use loadMapped() to load the graph."),
                        new FlaggedOption("threads", JSAP.INTSIZE_PARSER, "0", false, 'T', "threads", "The number of threads to be used. If 0, the number will be estimated automatically."),
                        new UnflaggedOption("graphBasename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, true, false, "The basename of the graph."),
                });

        JSAPResult jsapResult = jsap.parse(args);
        if(jsap.messagePrinted()) {
            System.exit(1);
        }

        String graphName = jsapResult.getString("graphBasename");
        String graphBasename = "./Graphs/" + graphName + "/" + graphName;
        int numberOfThreads = jsapResult.getInt("threads");
        ProgressLogger progressLogger = new ProgressLogger(LOGGER, "nodes");
        progressLogger.displayFreeMemory = true;
        progressLogger.displayLocalSpeed = true;
        ImmutableGraph graph = (new GraphReader(graphBasename, jsapResult.getBoolean("mapped", false), jsapResult.userSpecified("expand"), progressLogger)).getGraph();
        graph = Transform.symmetrize(graph);
        TopCloseness topCloseness = new TopCloseness(graph, progressLogger, numberOfThreads);
        try {
            topCloseness.compute();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
