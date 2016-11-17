/**
 * Created by eugenio on 16/11/16.
 */


import com.martiansoftware.jsap.*;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/** Implements a test to measure the algorithms performances in terms of time and precision.
 *
 */
public class Test {
    private static final Logger LOGGER = LoggerFactory.getLogger(HarmonicCentrality.class);
    private final static int WARMUP = 0;
    private final static int REPEAT = 1;

    public static void main(String[] args) throws IOException, JSAPException, InterruptedException {
        SimpleJSAP jsap = new SimpleJSAP(HarmonicCentrality.class.getName(), "Computes positive centralities of a graph using multiple parallel breadth-first visits.\n\nPlease note that to compute negative centralities on directed graphs (which is usually what you want) you have to compute positive centralities on the transpose.",
                new Parameter[]{
                        new Switch("expand", 'e', "expand", "Expand the graph to increase speed (no compression)."),
                        new Switch("mapped", 'm', "mapped", "Use loadMapped() to load the graph."),
                        new Switch("naive", 'n', "Use the naive algorithm to compute the exact harmonic centralities"),
                        new Switch("top_k", 'k', "Calculates the exact top-k Harmonic Centralities using the Okamoto et al. algorithm."),
                        new FlaggedOption("threads", JSAP.INTSIZE_PARSER, "0", false, 'T', "threads", "The number of threads to be used. If 0, the number will be estimated automatically."),
                        new UnflaggedOption("graphBasename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, true, false, "The basename of the graph."),
                        new UnflaggedOption("harmonicFilename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, true, false, "The filename where harmonic centrality scores (doubles in binary form) will be stored."),
                        new UnflaggedOption("precision/k", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, false, false, "The precision for the Eppstein algorithm or k for Okamoto")
                });

        JSAPResult jsapResult = jsap.parse(args);
        if (jsap.messagePrinted()) {
            System.exit(1);
        }

        boolean mapped = jsapResult.getBoolean("mapped", false);
        boolean top_k = jsapResult.getBoolean("top_k", false);
        boolean naive = jsapResult.getBoolean("naive", false);
        String graphBasename = "./Graphs/" + jsapResult.getString("graphBasename");
        int threads = jsapResult.getInt("threads");
        ProgressLogger progressLogger = new ProgressLogger(LOGGER, "nodes");
        progressLogger.displayFreeMemory = true;
        progressLogger.displayLocalSpeed = true;
        ImmutableGraph graph = mapped ? ImmutableGraph.loadMapped(graphBasename, progressLogger) : ImmutableGraph.load(graphBasename, progressLogger);

        if (jsapResult.userSpecified("expand")) {
            graph = (new ArrayListMutableGraph(graph)).immutableView();
        }

        long total_time = 0;
        long total_visited_nodes = 0;
        long total_visited_arcs = 0;

        Object centralities;
        centralities = naive ? new GeometricCentralities(graph, threads, progressLogger): new HarmonicCentrality(graph, threads, progressLogger);

        for (int k = WARMUP + REPEAT; k-- != 0; ) {

            if (!naive) {
                ((HarmonicCentrality)centralities).top_k = top_k;
                checkArgs(jsapResult, (HarmonicCentrality) centralities, top_k);
            }

            ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            long time = bean.getCurrentThreadCpuTime();
            if (naive) ((GeometricCentralities)centralities).compute();
            else ((HarmonicCentrality)centralities).compute();
            time = bean.getCurrentThreadCpuTime() - time;

            if (k < REPEAT) {
                total_time += time;
                if (naive) {
                    BinIO.storeDoubles(((GeometricCentralities)centralities).harmonic, jsapResult.getString("harmonicFilename"));
                }

                total_visited_nodes += naive ? ((GeometricCentralities)centralities).visitedNodes() : ((HarmonicCentrality)centralities).visitedNodes();
                total_visited_arcs += naive ? ((GeometricCentralities)centralities).visitedArcs() : ((HarmonicCentrality)centralities).visitedArcs();

            }

        }
        final double averageTime = total_time / (double)REPEAT;
        System.out.println("Num nodes = " + graph.numNodes());
        System.out.println("Num arcs = " + graph.numArcs());
        System.out.printf("Time: %.3fms\nVisited nodes: %d\nVisited arcs: %d\n", averageTime / 1E6, total_visited_nodes / REPEAT, total_visited_arcs / REPEAT);
    }

    /**
     * Checks whether or not the command is well-formatted (i.e. if all parameters are included and of
     * they are in the right interval)
     * @param jsapResult    object that deals with the input parameter string
     * @param centralities  instance of the HarmonicCentrality class
     * @param top_k         tells if the class should compute the exact top-k harmonic centralities using
     *                      the Okamoto et al. algorithm (= true) or if it should use the Eppstein algorithm
     *                      to compute an estimation of the centrality of all the nodes.
     */
    private static void checkArgs(JSAPResult jsapResult, HarmonicCentrality centralities, boolean top_k) {
        String prec_k = jsapResult.getString("precision/k");
        if (prec_k != null) {
            if (top_k) {
                centralities.k = Integer.parseInt(prec_k);
                if (centralities.k <= 0) {
                    System.err.println("k must be > 0.");
                    System.exit(1);
                }
            } else {
                centralities.precision = Double.parseDouble(prec_k);
                if (centralities.precision > 1 || centralities.precision <= 0) {
                    System.err.println("The precision value must lay in the [0,1] interval.");
                    System.exit(1);
                }
            }
        }
        else {

            System.err.println((top_k ? "k" : "precision") + " not specified");
            System.exit(1);
        }
    }
}
