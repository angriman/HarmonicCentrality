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
    private final static int WARMUP = 0;
    /** Number of repetition runs */
    private final static int REPEAT = 5;

    private static int topk = 1;
    private static double precision = 0.1;
    private static Algorithm algorithm = Algorithm.NAIVE;
    private enum Algorithm {
        EPPSTEIN, OKAMOTO, BORASSI, NAIVE, HYPERANF
    }



    public static void main(String[] args) throws IOException, JSAPException, InterruptedException {
        SimpleJSAP jsap = new SimpleJSAP(HarmonicCentrality.class.getName(), "Computes positive centralities of a graph using multiple parallel breadth-first visits.\n\nPlease note that to compute negative centralities on directed graphs (which is usually what you want) you have to compute positive centralities on the transpose.",
                new Parameter[]{
                        new Switch("expand", 'e', "expand", "Expand the graph to increase speed (no compression)."),
                        new Switch("mapped", 'm', "mapped", "Use loadMapped() to load the graph."),
                        new Switch("eppstein", 'p', "Use the eppstein algorithm to compute the approximated harmonic centralities"),
                        new Switch("borassi", 'b', "Calculates the exact top-k Harmonic Centralities using the Borassi et al. algorithm."),
                        new Switch("okamoto", 'o', "Calculates the exact top-k Harmonic Centralities using the Okamoto et al. algorithm."),
                        new Switch("hyperball", 'h', "Runs HyperANF."),
                        new FlaggedOption("threads", JSAP.INTSIZE_PARSER, "0", false, 'T', "threads", "The number of threads to be used. If 0, the number will be estimated automatically."),
                        new UnflaggedOption("precision/k", JSAP.DOUBLE_PARSER, JSAP.NO_DEFAULT, false, false, "The precision for the Eppstein algorithm.")
                });
        JSAPResult jsapResult = jsap.parse(args);
        if (jsap.messagePrinted()) {
            System.exit(1);
        }

        boolean mapped = jsapResult.getBoolean("mapped", false);
        if (jsapResult.getBoolean("eppstein", false)) algorithm = Algorithm.EPPSTEIN;
        else if (jsapResult.getBoolean("borassi", false)) algorithm = Algorithm.BORASSI;
        else if (jsapResult.getBoolean("okamoto", false)) algorithm = Algorithm.OKAMOTO;
        else if (jsapResult.getBoolean("hyperball", false)) algorithm = Algorithm.HYPERANF;

        /* Number of threads. */
        int threads = jsapResult.getInt("threads");

        switch (algorithm) {
            case EPPSTEIN:
                precision = jsapResult.getDouble("precision/k");
                break;
            case OKAMOTO:
            case BORASSI:
                topk = jsapResult.getInt("precision/k");
                break;
        }

        checkArgs();

        ProgressLogger progressLogger = new ProgressLogger(LOGGER, "nodes");
        progressLogger.displayFreeMemory = true;
        progressLogger.displayLocalSpeed = true;


        /* Generalized class. */
        Object centralities = null;

        /* Graphs on which perform the experiment. */
        File graphList = new File("GraphList.txt");
        Scanner scanner = new Scanner(graphList);

        /* Experiments begins here */
        while (scanner.hasNextLine()) {

            String currentGraph = scanner.nextLine();

            /* Set up experimental metrics. */
            Experiment experiment = new Experiment();

            final String algoString = "Algorithm";
            switch (algorithm) {
                case NAIVE:
                    experiment.tag(algoString, "Naive");
                    break;
                case EPPSTEIN:
                    experiment.tag(algoString , "Eppstein");
                    experiment.tag("Precision", precision);
                    break;
                case BORASSI:
                    experiment.tag(algoString, "Borassi");
                    experiment.tag("k", topk);
                    break;
                case HYPERANF:
                    experiment.tag(algoString, "HyperANF");
                    break;
                case OKAMOTO:
                    experiment.tag(algoString, "Okamoto");
                    experiment.tag("k", topk);
                    break;
            }

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
                long time = 0;
                long visited_nodes = 0;
                long visited_arcs = 0;
                boolean isWarmup = k < REPEAT;

                switch (algorithm) {
                    case NAIVE:
                        centralities = new GeometricCentralities(graph, threads, progressLogger);
                        time = bean.getCurrentThreadCpuTime();
                        ((GeometricCentralities)centralities).compute();
                        time = bean.getCurrentThreadCpuTime() - time;
                        visited_arcs = ((GeometricCentralities)centralities).visitedArcs();
                        visited_nodes = ((GeometricCentralities)centralities).visitedNodes();
                        break;
                    case HYPERANF:
                        centralities = new HyperBall(graph, (int)Math.log(threads));
                        time = bean.getCurrentThreadCpuTime();
                        ((HyperBall)centralities).run();
                        time = bean.getCurrentThreadCpuTime() - time;
                        break;
                    case BORASSI:
                        centralities = new HarmonicCentrality(graph, threads, progressLogger);
                        ((HarmonicCentrality) centralities).k = topk;
                        ((HarmonicCentrality) centralities).setPrecision(-1);
                        time = bean.getCurrentThreadCpuTime();
                        ((HarmonicCentrality) centralities).computeBorassi();
                        time = bean.getCurrentThreadCpuTime() - time;
                        visited_arcs = ((HarmonicCentrality)centralities).visitedArcs();
                        visited_nodes = ((HarmonicCentrality)centralities).visitedNodes();
                        break;
                    case OKAMOTO:
                        centralities = new HarmonicCentrality(graph, threads, progressLogger);
                        ((HarmonicCentrality)centralities).k = topk;
                        ((HarmonicCentrality)centralities).setPrecision(-1);
                        time = bean.getCurrentThreadCpuTime();
                        ((HarmonicCentrality)centralities).computeOkamoto();
                        time = bean.getCurrentThreadCpuTime() - time;
                        visited_arcs = ((HarmonicCentrality)centralities).visitedArcs();
                        visited_nodes = ((HarmonicCentrality)centralities).visitedNodes();
                        break;
                    case EPPSTEIN:
                        centralities = new HarmonicCentrality(graph, threads, progressLogger);
                        ((HarmonicCentrality)centralities).setPrecision(precision);
                        time = bean.getCurrentThreadCpuTime();
                        ((HarmonicCentrality)centralities).computeEppstein();
                        time = bean.getCurrentThreadCpuTime() - time;
                        visited_arcs = ((HarmonicCentrality)centralities).visitedArcs();
                        visited_nodes = ((HarmonicCentrality)centralities).visitedNodes();
                        break;
                }

                int iteration = REPEAT - k;

                if (isWarmup) {
                    experiment.append(timingTableName,
                            "Iteration", iteration,
                            "CPU Time", time,
                            "Visited nodes", visited_nodes,
                            "Visited arcs", visited_arcs);
                    switch (algorithm) {
                        case OKAMOTO:
                            break;
                        case BORASSI:
                            break;
                        case NAIVE:
                            if (k == (REPEAT - 1)) {
                                double[] harmonics = ((GeometricCentralities) centralities).harmonic;
                                for (int c = 0; c < harmonics.length; ++c) {
                                    experiment.append("Centralities", "Node", c, "Value", harmonics[c]);
                                }
                            }
                            break;
                        case EPPSTEIN:
                            double[] harmonics  = ((HarmonicCentrality)centralities).harmonic;
                            for (int c = 0; c < harmonics.length; ++c) {
                                experiment.append("Centralities", "Node", c, "Value", harmonics[c]);
                            }
                            break;
                        case HYPERANF:
                            break;
                    }
                }

            }

            File outFile = new File(outFileName(currentGraph));
            OutputStream os = new FileOutputStream(outFile);
            PrintWriter out = new PrintWriter(os);
            out.write(JsonFormatter.format(experiment));
            out.close();
        }
    }

    private static String outCentralitiesFileName(String currentGraph) {
        String filename = "./results/" + currentGraph;
        switch (algorithm) {
            case EPPSTEIN:
                filename += "_eppstein_" + precision;
                break;
            case BORASSI:
                filename += "_borassi_" + topk;
                break;
            case OKAMOTO:
                filename += "_okamoto_" + topk;
                break;
            case HYPERANF:
                filename += "_hyperanf_";
            default:break;
        }
        filename += "centralities.txt";
        return filename;
    }

    private static String outFileName(String currentGraph) {
        String filename = "./results/" + currentGraph;
        switch (algorithm) {
            case EPPSTEIN:
                filename += "_eppstein_" + precision;
                break;
            case BORASSI:
                filename += "_borassi_" + topk;
                break;
            case OKAMOTO:
                filename += "_okamoto_" + topk;
                break;
            case HYPERANF:
                filename += "_hyperanf_";
            default:break;
        }

        filename += ".json";
        return filename;
    }

    private static void checkArgs() {
        switch (algorithm) {
            case EPPSTEIN:
                if (precision > 1 || precision <= 0) {
                    reportArgsError("The precision must be > 0 and <= 1.");
                }
                break;
            case OKAMOTO:
            case BORASSI:
                if (topk < 0) {
                    reportArgsError("k must be > 0.");
                }
        }
    }

    private static void reportArgsError(String error) {
        System.err.println(error);
        System.exit(1);
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
