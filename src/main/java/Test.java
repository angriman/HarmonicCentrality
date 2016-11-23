/**
 * Created by eugenio on 16/11/16.
 */


import com.martiansoftware.jsap.*;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.Transform;
import it.unimi.dsi.webgraph.algo.HyperBall;
import it.unipd.dei.experiment.Experiment;
import it.unipd.dei.experiment.JsonFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Scanner;

/** Implements a test to measure the algorithms performances in terms of time and precision.
 *
 */
public class Test {
    /** Progress logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(HarmonicCentrality.class);
    /** Number of warm-up iterations */
    private final static int WARMUP = 1;
    /** Number of repetition runs */
    private final static int REPEAT = 2;

    public static void main(String[] args) throws IOException, JSAPException, InterruptedException {
        SimpleJSAP jsap = new SimpleJSAP(HarmonicCentrality.class.getName(), "Computes positive centralities of a graph using multiple parallel breadth-first visits.\n\nPlease note that to compute negative centralities on directed graphs (which is usually what you want) you have to compute positive centralities on the transpose.",
                new Parameter[]{
                        new Switch("expand", 'e', "expand", "Expand the graph to increase speed (no compression)."),
                        new Switch("mapped", 'm', "mapped", "Use loadMapped() to load the graph."),
                        new Switch("naive", 'n', "Use the naive algorithm to compute the exact harmonic centralities"),
                        new Switch("borassi", 'b', "Calculates the exact top-k Harmonic Centralities using the Borassi et al. algorithm."),
                        new Switch("okamoto", 'o', "Calculates the exact top-k Harmonic Centralities using the Okamoto et al. algorithm."),
                        new Switch("hyperball", 'h', "Runs HyperANF."),
                        new FlaggedOption("threads", JSAP.INTSIZE_PARSER, "0", false, 'T', "threads", "The number of threads to be used. If 0, the number will be estimated automatically."),
                        //new UnflaggedOption("graphBasename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, true, false, "The basename of the graph."),
                        //new UnflaggedOption("harmonicFilename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, true, false, "The filename where harmonic centrality scores (doubles in binary form) will be stored."),
                        //new UnflaggedOption("precision/k", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, false, false, "The precision for the Eppstein algorithm or k for Okamoto")
                });
        JSAPResult jsapResult = jsap.parse(args);
        if (jsap.messagePrinted()) {
            System.exit(1);
        }

        boolean mapped = jsapResult.getBoolean("mapped", false);
        boolean okamoto = jsapResult.getBoolean("okamoto", false);
        boolean naive = jsapResult.getBoolean("naive", false);
        boolean borassi = jsapResult.getBoolean("borassi", false);
        boolean hyperball = jsapResult.getBoolean("hyperball", false);

        /* Number of threads. */
        int threads = jsapResult.getInt("threads");

        ProgressLogger progressLogger = new ProgressLogger(LOGGER, "nodes");
        progressLogger.displayFreeMemory = true;
        progressLogger.displayLocalSpeed = true;


        /* Generalized class. */
        Object centralities;

        /* Graphs on which perform the experiment. */
        File graphList = new File("GraphList.txt");
        Scanner scanner = new Scanner(graphList);

        /* Experiments begins here */
        while (scanner.hasNextLine()) {

            String currentGraph = scanner.nextLine();

            /* Set up experimental metrics. */
            Experiment experiment = new Experiment();

            final String algoString = "Algorithm";
            if (naive) experiment.tag(algoString, "Naive");
            else if (okamoto) experiment.tag(algoString, "Okamoto");
            else if (borassi) experiment.tag(algoString, "Borassi");
            else if (hyperball) experiment.tag(algoString, "HyperANF");
            else experiment.tag(algoString , "Eppstein");

            final String timingTableName = "Timing";

            /* Path where the graph is stored. */
            final String graphBasename = "./Graphs/" + currentGraph + "/" + currentGraph; //jsapResult.getString("graphBasename");

            /* Reads the input graph. */
            ImmutableGraph graph = mapped ? ImmutableGraph.loadMapped(graphBasename, progressLogger) : ImmutableGraph.load(graphBasename, progressLogger);

            experiment
                    .tag("Graph Name", currentGraph)
                    .tag("Num. nodes", graph.numNodes())
                    .tag("Num. arcs", graph.numArcs());

            /* Transforms the graph to an undirected graph. */
            graph = Transform.symmetrize(graph);

            if (jsapResult.userSpecified("expand")) {
                graph = (new ArrayListMutableGraph(graph)).immutableView();
            }


            for (int k = WARMUP + REPEAT; k-- != 0; ) {
                ThreadMXBean bean = ManagementFactory.getThreadMXBean();
                long time;
                long visited_nodes = 0;
                long visited_arcs = 0;
                boolean isWarmup = k < REPEAT;

                if (naive) {
                    centralities = new GeometricCentralities(graph, threads, progressLogger);
                    time = bean.getCurrentThreadCpuTime();
                    ((GeometricCentralities)centralities).compute();
                    time = bean.getCurrentThreadCpuTime() - time;
//                    if (k == REPEAT - 1) {
//                        BinIO.storeDoubles(((GeometricCentralities)centralities).harmonic, harmonicsFileName);
//                    }
                    visited_arcs = ((GeometricCentralities)centralities).visitedArcs();
                    visited_nodes = ((GeometricCentralities)centralities).visitedNodes();
                }
                else if (hyperball) {
                    centralities = new HyperBall(graph, 7);
                    time = bean.getCurrentThreadCpuTime();
                    ((HyperBall)centralities).run();
                    time = bean.getCurrentThreadCpuTime() - time;
                }
                else {
                    centralities = new HarmonicCentrality(graph, threads, progressLogger);
                    ((HarmonicCentrality)centralities).top_k = okamoto;
                    ((HarmonicCentrality)centralities).borassi = borassi;
                    checkArgs(jsapResult, (HarmonicCentrality) centralities, okamoto || borassi);
                    time = bean.getCurrentThreadCpuTime();
                    ((HarmonicCentrality)centralities).compute();
                    time = bean.getCurrentThreadCpuTime() - time;
                    visited_arcs = ((HarmonicCentrality)centralities).visitedArcs();
                    visited_nodes = ((HarmonicCentrality)centralities).visitedNodes();
                }

                if (isWarmup) {
                    experiment.append(timingTableName,
                            "Iteration", (REPEAT - k),
                            "CPU Time", time,
                            "Visited nodes", visited_nodes,
                            "Visited arcs", visited_arcs);
                    if (k == REPEAT - 1 && naive) {
                        double[] harmonics = ((GeometricCentralities) centralities).harmonic;
                        for (int c = 0; c < harmonics.length; ++c) {
                            experiment.append("Centralities", "Node", c, "Value", harmonics[c]);
                        }

                    }
                }
            }

            File outFile = new File("./results/" + currentGraph + ".json");
            OutputStream os = new FileOutputStream(outFile);
            PrintWriter out = new PrintWriter(os);
            out.write(JsonFormatter.format(experiment));
            out.close();

        }



//        /* Experiments begins here */
//        for (int k = WARMUP + REPEAT; k-- != 0; ) {
//
//
//                if (!naive) {
//                    if(!borassi) System.out.println("Random samples = " + ((HarmonicCentrality)centralities).randomSamples());
//                    if (!okamoto) {
//                      //  double[] exact = BinIO.loadDoubles(jsapResult.getString("harmonicFilename"));
//                      //  int i = 0;
//                      //  for (double ignored : exact) {
////                        }
//
//                      //  System.out.println(Arrays.toString(errors(exact, ((HarmonicCentrality) centralities).harmonic)));
//                    } else {
//                        System.out.println("Additive samples = " + ((HarmonicCentrality)centralities).additiveSamples());
//                        System.out.println(checkTopK(((HarmonicCentrality) centralities).candidateSetHarmonics, ((HarmonicCentrality)centralities).candidateSet, jsapResult) ?
//                                "Correct" : "Incorrect");
//                    }
//                }
//            }
//        }


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
                if (centralities.borassi) {
                    System.err.println("Both Okamoto and Borassi algorithms selected. Choose only one of them.");
                    System.exit(1);
                }
            }
            else {
                double precision = Double.parseDouble(prec_k);
                if (precision > 1 || precision <= 0) {
                    System.err.println("The precision value must lay in the [0,1] interval.");
                    System.exit(1);
                }
                centralities.setPrecision(precision);
            }
        }
        else {
            System.err.println((top_k ? "k" : "precision") + " not specified");
            System.exit(1);
        }
    }

    /**
     * Computes the precision metrics for the Eppstein estimated harmonic centrality vector.
     * @param exact Vector containing the exact value of the harmonic centrality of each node
     * @param apx   Vector containing the approximated value of the harmonic centrality of each node
     * @return      Returns a vector containing the values of the error metrics.
     */
    private static double[] errors(double[] exact, double[] apx) {
        /* Average absolute error */
        double avgAbsErr = 0;
        /* Average relative error */
        double avgRelErr = 0;
        /* Error variance */
        double errorVariance = 0;
        /* Average of the exact harmonic centralities */
        double avg = 0;
        /* Vector containing the values of the error metrics */
        double[] toReturn = new double[3];

        double normalization = exact.length - 1;

        /* For each value in the vectors computes the absolute and the relative errors
         * and also updates the average */
        for (int i = 0; i < exact.length; ++i) {
            avgAbsErr += Math.abs(exact[i] /normalization  - apx[i]);
            avg += exact[i] / normalization;
            double den = exact[i] == 0 ? 1 : exact[i];
            avgRelErr += Math.abs((exact[i] / normalization - apx[i]) / den);
        }

        /* The harmonic centrality values have never been normalized. We add a normalization term: n*(n-1) */

        avg /= exact.length;

        for (int i = 0; i < exact.length; ++i) {
            errorVariance += Math.pow((apx[i] - avg), 2) / (exact.length - 1);
        }

        toReturn[0] = avgAbsErr / exact.length;
        toReturn[1] = avgRelErr / exact.length;
        toReturn[2] = errorVariance;

        return toReturn;
    }

    private static boolean checkTopK(double[] topk, int[] indexes, JSAPResult jsapResult) throws IOException {
        double[] exact = BinIO.loadDoubles(jsapResult.getString("harmonicFilename"));
        int k = Integer.parseInt(jsapResult.getString("precision/k"));
        double[][] sortedExact = HarmonicCentrality.sort(exact);
        double[][] sortedTopk = new double[topk.length][2];
        for (int i = 0; i < topk.length; ++i) {
            sortedTopk[i][0] = topk[i];
            sortedTopk[i][1] = indexes[i];
        }
        HarmonicCentrality.sort(sortedTopk);
        for (int i = 0; i < k; ++i) {
            if (!((int)sortedExact[i][1] == (int)(sortedTopk[i][1]))) {
                return false;
            }
        }
        return true;
    }
}
