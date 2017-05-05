import com.martiansoftware.jsap.*;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.Transform;
import it.unimi.dsi.webgraph.algo.ConnectedComponents;
import org.apache.commons.lang.ArrayUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
                        //new UnflaggedOption("k", JSAP.INTSIZE_PARSER, JSAP.NO_DEFAULT, true, false, "The number of top closeness centralities to be computed."),
                });

        JSAPResult jsapResult = jsap.parse(args);
        if(jsap.messagePrinted()) {
            System.exit(1);
        }

        String graphName = jsapResult.getString("graphBasename");
        String graphBasename = "./Graphs/" + graphName + "/" + graphName;
        int numberOfThreads = jsapResult.getInt("threads");
        // int k = jsapResult.getInt("k");
        ProgressLogger progressLogger = new ProgressLogger(LOGGER, "nodes");
        progressLogger.displayFreeMemory = true;
        progressLogger.displayLocalSpeed = true;
        ImmutableGraph graph = (new GraphReader(graphBasename, jsapResult.getBoolean("mapped", false), jsapResult.userSpecified("expand"), progressLogger)).getGraph();
        graph = Transform.symmetrize(graph);
        //System.out.println("Number of SCCs = " + ConnectedComponents.compute(graph, numberOfThreads, null).numberOfComponents);
        int[] topk = new int[100];
        topk[0] = 1;
        for (int i = 1; i < topk.length; ++i) {
            topk[i] = 20 * i;
        }
        boolean correct = true;
        String resultPath = "./results/chechikResult.json";
        JSONObject obj = new JSONObject();
        for (int k : topk) {
            ChechikTopCloseness topCloseness = new ChechikTopCloseness(graph, progressLogger, numberOfThreads, k, graphName);
            try {
                topCloseness.compute();
                GTLoader loader = new GTLoader(graphName, graph.numNodes());
                try {
                    loader.load();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Integer[] computed_top_k = topCloseness.getTopk();
                //  double[] computedtopk = topCloseness.getApxCloseness();
                // double[] closeness = loader.getCloseness();
                int[] exact = loader.getTopKNodes(k);

                Set<Integer> computed_set = new HashSet<>(Arrays.asList(computed_top_k));
                Set<Integer> exact_set = new HashSet<>(Arrays.asList(ArrayUtils.toObject(exact)));
                double size = computed_set.size();//Math.min(computed_set.size(), k);
                //double size = exact_set.size();
                computed_set.retainAll(exact_set);
                //exact_set.retainAll(computed_set);

                double precision = (double) computed_set.size() / k;
                if (precision != 1.0D) {
                    correct = false;
                }

                System.out.println("K = " + k);
                System.out.println("Precision = " + precision);
                System.out.println("Computed set size = " + (int)size + " exact top k size = " + exact_set.size());
                System.out.println("Number of BFS = " + topCloseness.getNumberOfBFS());
                obj.put(String.valueOf(k), topCloseness.getNumberOfBFS());

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try (FileWriter file = new FileWriter(resultPath)) {
            obj.write(file);
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println(correct ? "All computation correct" : "Something went wrong");
    }
}
