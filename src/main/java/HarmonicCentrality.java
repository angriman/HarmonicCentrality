//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Implements the Eppstein et al. and Okamoto et al. algorithms for the efficient randomized computation of the Harmonic
 * Centrality. The Eppstein algorithm can be used by specifying the precision at the end of the command line while the
 * Okamoto algorithm needs the option -k to be activated and the k specified at the end of the line.
 */

public class HarmonicCentrality {
    private static final Logger LOGGER = LoggerFactory.getLogger(HarmonicCentrality.class);

    /** The graph under examination. */
    private final ImmutableGraph graph;
    /** Harmonic centrality vector. */
    final double[] harmonic;
    /** Global progress logger. */
    private final ProgressLogger pl;
    /** Number of threads. */
    private final int numberOfThreads;
    /** Next node to be visited. */
    private final AtomicInteger nextNode;
    /** Whether to stop abruptly the visiting process. */
    private volatile boolean stop;
    /** Required precision for the Eppstein algorithm. */
    double precision;
    /** Random samples vector for the Eppstein algorithm. */
    private int[] randomSamples;
    /** Normalization term for the Eppstein estimated harmonic centrality value. */
    private double normalization;
    /** Whether or not to compute the exact top-k harmonic centralities using the Okamoto algorithm. */
    boolean top_k = false;
    /** Whether or not to compute the exact top-k harmonic centralities using the Borassi et al. algorithm. */
    boolean borassi = false;
    /** Number of top-k centralities to compute using the Okamoto algorithm. */
    int k = 0;
    /** Candidate set vector for the Okamoto algorithm. */
    int[] candidateSet;
    /** Corresponding value of the harmonic centrality of the nodes inside the candidate set. */
    double[] candidateSetHarmonics;
    /** Multiplicative constant in front of the f_function. Theory tells that it must be > 1.
    * A high value of ALPHA determines a greater threshold --> greater candidate set.
    * The formula is: ALPHA * sqrt(log(n) / l) */
    private static final double ALPHA = 1.01;
    /** Multiplicative function in front of the Okamoto number of random samples formula.
    * The formula is: number of random samples = BETA * n^(1/3) * log^(2/3)(n) */
    private static final double BETA = 1;
    /** Multiplicative constant in front of the Eppstein number of random samples formula.
    * The formula is: number of samples = log(n) / precision^2 */
    private static final double C = 0.25;
    /** Number of total visited nodes. */
    private final AtomicInteger visitedNodes;
    /** Number of total visited arcs. */
    private final AtomicInteger visitedArcs;

    /** Creates a new instance of this class for the computation of the harmonic centrality using efficient randomized
     * algorithms.
     *
     * @param graph the input graph.
     * @param requestedThreads  requested number of threads (0 for {@link Runtime#availableProcessors()}).
     * @param pl a global progress logger, or {@code null}.
     */
    HarmonicCentrality(ImmutableGraph graph, int requestedThreads, ProgressLogger pl) {
        this.pl = pl;
        this.graph = graph;
        this.harmonic = new double[graph.numNodes()];
        this.nextNode = new AtomicInteger();
        this.visitedNodes = new AtomicInteger();
        this.visitedArcs = new AtomicInteger();
        this.numberOfThreads = requestedThreads != 0 ? requestedThreads:Runtime.getRuntime().availableProcessors();
    }

    /**
     * @return the number of visited nodes.
     */
    int visitedNodes() {
        return this.visitedNodes.get();
    }

    /**
     * @return the number of visited arcs.
     */
    int visitedArcs() {
        return this.visitedArcs.get();
    }

    /**
     * @return the number of extracted random samples.
     */
    int randomSamples() {
        return randomSamples.length;
    }

    /**
     * @return the number of further samples added to the candidate set.
     */
    int additiveSamples() {
        return candidateSet.length - k;
    }
    /** Computes the number of random samples to be computed according to the algorithm.
     *
     * @return the number of random samples.
     */
    private int numberOfSamples() {
        /* Okamoto algorithm: it needs BETA * n^(2/3) * (log(n))^(1/3) nodes. */
        if (top_k) {
            return (int)Math.ceil(BETA * Math.cbrt(Math.pow(graph.numNodes(), 2) * Math.log(graph.numNodes())));
        }
        /* Eppstein algorithm, it needs C * log(n) / epsilon^2 nodes. */
        return (int)Math.ceil(C * Math.log(graph.numNodes()) / Math.pow(precision, 2));
    }

    /** Computes k unique random samples computed uniformly at random.
     *
     * @param k number of random samples to be computed.
     * @return  a k-long integer array containing k unique random samples.
     */
    private int[] pickRandomSamples(int k) {
        /* Check whether 0 < k <= n. */
        if (k >= graph.numNodes()) {
            System.err.println("Number of random samples to be extracted greater than n.");
            System.exit(1);
        }
        if (k < 0) {
            System.err.println("Negative number of random samples.");
            System.exit(1);
        }

        /* Tree structure for better performances. */
        TreeSet<Integer> set = new TreeSet<Integer>();
        /* How many different samples have been extracted at each loop. */
        int count = 0;
        /* For integer random extraction. */
        Random rand = new Random();

        /* Keep on looping until k unique random samples have been extracted. */
        while (count < k) {
            int randomNum = rand.nextInt(graph.numNodes());
            if (!set.contains(randomNum)) {
                set.add(randomNum);
                ++count;
            }
        }
        return ArrayUtils.toPrimitive(set.toArray(new Integer[set.size()]));
    }

    /** f(l) function used by the Okamoto algorithm.
     *
     * @return the threshold to be used to calculate the candidate set.
     */
    private double f_function() {
        return ALPHA * Math.sqrt(Math.log(graph.numNodes()) / randomSamples.length);
    }

    /** Computes the harmonic centralities using the required algorithm.
     *
     * @throws InterruptedException
     */
    void compute() throws InterruptedException {
        if (!borassi) {
            randomSamples = pickRandomSamples(numberOfSamples());
            normalization = (double) graph.numNodes() / ((double) (graph.numNodes() - 1) * (double) randomSamples.length);
            HarmonicCentrality.HarmonicApproximationThread[] thread = new HarmonicCentrality.HarmonicApproximationThread[this.numberOfThreads];

            for (int executorService = 0; executorService < thread.length; ++executorService) {
                thread[executorService] = new HarmonicCentrality.HarmonicApproximationThread();
            }

            if (this.pl != null) {
                this.pl.start("Starting visits...");
                this.pl.expectedUpdates = (long) this.graph.numNodes();
                this.pl.itemsName = "nodes";
            }

            ExecutorService var11 = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            ExecutorCompletionService executorCompletionService = new ExecutorCompletionService(var11);
            int e = thread.length;

            while (e-- != 0) executorCompletionService.submit(thread[e]);

            try {
                e = thread.length;

                while (e-- != 0) {
                    executorCompletionService.take().get();
                }
            } catch (ExecutionException var9) {
                this.stop = true;
                Throwable cause = var9.getCause();
                throw cause instanceof RuntimeException ? (RuntimeException) cause : new RuntimeException(cause.getMessage(), cause);
            } finally {
                var11.shutdown();
            }

            if (top_k) {
                double[][] h = sort(harmonic);

                int from = k - 1;
                int additiveSamples = 0;
                double threshold = f_function();
                while (from + additiveSamples < graph.numNodes() && h[from + additiveSamples][0] >= h[from][0] - threshold) {
                    ++additiveSamples;
                }

                candidateSet = new int[from + additiveSamples];
                candidateSetHarmonics = new double[candidateSet.length];

                for (int i = 0; i < candidateSet.length; ++i) {
                    candidateSet[i] = (int) h[i][1];
                }

                HarmonicCentrality.HarmonicExactComputationThread[] exactComputationThreads = new HarmonicCentrality.HarmonicExactComputationThread[this.numberOfThreads];
                HarmonicCentrality.this.nextNode.set(0);

                for (int executorService = 0; executorService < exactComputationThreads.length; ++executorService) {
                    exactComputationThreads[executorService] = new HarmonicCentrality.HarmonicExactComputationThread();
                }

                if (this.pl != null) {
                    this.pl.start("Starting visits...");
                    this.pl.expectedUpdates = (long) this.graph.numNodes();
                    this.pl.itemsName = "nodes";
                }

                var11 = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
                executorCompletionService = new ExecutorCompletionService(var11);

                e = exactComputationThreads.length;

                while (e-- != 0) executorCompletionService.submit(exactComputationThreads[e]);

                try {
                    e = exactComputationThreads.length;

                    while (e-- != 0) {
                        executorCompletionService.take().get();
                    }
                } catch (ExecutionException var9) {
                    this.stop = true;
                    Throwable cause = var9.getCause();
                    throw cause instanceof RuntimeException ? (RuntimeException) cause : new RuntimeException(cause.getMessage(), cause);
                } finally {
                    var11.shutdown();
                }
            }
        }
        else {
            HarmonicCentrality.BFSCutThread[] thread = new HarmonicCentrality.BFSCutThread[this.numberOfThreads];

            for (int executorService = 0; executorService < thread.length; ++executorService) {
                thread[executorService] = new HarmonicCentrality.BFSCutThread();
            }

            if (this.pl != null) {
                this.pl.start("Starting visits...");
                this.pl.expectedUpdates = (long) this.graph.numNodes();
                this.pl.itemsName = "nodes";
            }

            ExecutorService var11 = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            ExecutorCompletionService executorCompletionService = new ExecutorCompletionService(var11);
            int e = thread.length;

            while (e-- != 0) executorCompletionService.submit(thread[e]);

            try {
                e = thread.length;

                while (e-- != 0) {
                    executorCompletionService.take().get();
                }
            } catch (ExecutionException var9) {
                this.stop = true;
                Throwable cause = var9.getCause();
                throw cause instanceof RuntimeException ? (RuntimeException) cause : new RuntimeException(cause.getMessage(), cause);
            } finally {
                var11.shutdown();
            }
        }

        if (this.pl != null) {
            this.pl.done();
        }
    }

    public static void main(String[] arg) throws IOException, JSAPException, InterruptedException {
        SimpleJSAP jsap = new SimpleJSAP(HarmonicCentrality.class.getName(), "Computes positive centralities of a graph using multiple parallel breadth-first visits.\n\nPlease note that to compute negative centralities on directed graphs (which is usually what you want) you have to compute positive centralities on the transpose.",
                new Parameter[]{
                        new Switch("expand", 'e', "expand", "Expand the graph to increase speed (no compression)."),
                        new Switch("mapped", 'm', "mapped", "Use loadMapped() to load the graph."),
                        new Switch("top_k" , 'k', "Calculates the exact top-k Harmonic Centralities using the Okamoto et al. algorithm."),
                        new FlaggedOption("threads", JSAP.INTSIZE_PARSER, "0", false, 'T', "threads", "The number of threads to be used. If 0, the number will be estimated automatically."),
                        new UnflaggedOption("graphBasename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, true, false, "The basename of the graph."),
                        new UnflaggedOption("harmonicFilename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, true, false, "The filename where harmonic centrality scores (doubles in binary form) will be stored."),
                        new UnflaggedOption("precision/k", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, false, false, "The precision for the Eppstein algorithm or k for Okamoto")
        });

        JSAPResult jsapResult = jsap.parse(arg);
        if(jsap.messagePrinted()) {
            System.exit(1);
        }

        boolean mapped = jsapResult.getBoolean("mapped", false);
        boolean top_k = jsapResult.getBoolean("top_k", false);
        String graphBasename = "./Graphs/" + jsapResult.getString("graphBasename");
        int threads = jsapResult.getInt("threads");
        ProgressLogger progressLogger = new ProgressLogger(LOGGER, "nodes");
        progressLogger.displayFreeMemory = true;
        progressLogger.displayLocalSpeed = true;
        ImmutableGraph graph = mapped?ImmutableGraph.loadMapped(graphBasename, progressLogger):ImmutableGraph.load(graphBasename, progressLogger);
        if(jsapResult.userSpecified("expand")) {
            graph = (new ArrayListMutableGraph(graph)).immutableView();
        }

        HarmonicCentrality centralities = new HarmonicCentrality(graph, threads, progressLogger);
        centralities.top_k = top_k;
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
        centralities.compute();
    }

    /** Thread for the estimation of the harmonic centrality of a single node using the Eppstein algorithm.
     *
     */
    private final class HarmonicApproximationThread implements Callable<Void> {
        private final IntArrayFIFOQueue queue;
        private final int[] distance;

        private HarmonicApproximationThread() {
            this.distance = new int[HarmonicCentrality.this.graph.numNodes()];
            this.queue = new IntArrayFIFOQueue();
        }

        public Void call() {
            int[] distance = this.distance;
            IntArrayFIFOQueue queue = this.queue;
            ImmutableGraph graph = HarmonicCentrality.this.graph.copy();


            while (true) {
                int curr = HarmonicCentrality.this.nextNode.getAndIncrement();
                if (HarmonicCentrality.this.stop || curr >= randomSamples.length) {
                    return null;
                }

                queue.clear();
                queue.enqueue(randomSamples[curr]);
                Arrays.fill(distance, -1);
                distance[randomSamples[curr]] = 0;

                while (!queue.isEmpty()) {
                    int node = queue.dequeueInt();
                    int d = distance[node] + 1;
                    double hd = 1.0D / (double) d;
                    LazyIntIterator successors = graph.successors(node);

                    int s;
                    while ((s = successors.nextInt()) != -1) {
                        visitedArcs.getAndIncrement();
                        if(distance[s] == -1) {
                            queue.enqueue(s);
                            visitedNodes.getAndIncrement();
                            distance[s] = d;
                            HarmonicCentrality.this.harmonic[s] += normalization * hd;
                        }
                    }
                }

                if (HarmonicCentrality.this.pl != null) {
                    synchronized (HarmonicCentrality.this.pl) {
                        HarmonicCentrality.this.pl.update();
                    }
                }
            }
        }
    }

    /** Thread for the computation of the exact value of the harmonic centrality using the naive algorithm.
     *  We use it just for the candidate set.S
     */
    private final class HarmonicExactComputationThread implements Callable<Void> {
        private final IntArrayFIFOQueue queue;
        private final int[] distance;

        private HarmonicExactComputationThread() {
            this.distance = new int[HarmonicCentrality.this.graph.numNodes()];
            this.queue = new IntArrayFIFOQueue();
        }

        public Void call() {
            int[] distance = this.distance;
            IntArrayFIFOQueue queue = this.queue;
            ImmutableGraph graph = HarmonicCentrality.this.graph.copy();

            while (true) {
                int curr = HarmonicCentrality.this.nextNode.getAndIncrement();
                if (HarmonicCentrality.this.stop || curr >= candidateSet.length) {
                    return null;
                }

                queue.clear();
                queue.enqueue(candidateSet[curr]);
                Arrays.fill(distance, -1);
                distance[candidateSet[curr]] = 0;

                while(!queue.isEmpty()) {
                    int node = queue.dequeueInt();
                    int d = distance[node] + 1;
                    double hd = 1.0D / ((double)d * (double)(graph.numNodes() - 1));
                    LazyIntIterator successors = graph.successors(node);

                    int s;
                    while((s = successors.nextInt()) != -1) {
                        visitedArcs.getAndIncrement();
                        if(distance[s] == -1) {
                            visitedNodes.getAndIncrement();
                            queue.enqueue(s);
                            distance[s] = d;
                            HarmonicCentrality.this.candidateSetHarmonics[curr] += hd;
                        }
                    }
                }

                if (HarmonicCentrality.this.pl != null) {
                    synchronized (HarmonicCentrality.this.pl) {
                        HarmonicCentrality.this.pl.update();
                    }
                }
            }
        }
    }

    private final TreeSet<Double[]> borassi_list = new TreeSet<Double[]>(new Comparator<Double[]>() {
        public int compare(Double[] o1, Double[] o2) {
            return o2[0].compareTo(o1[0]);
        }
    });

    private final class BFSCutThread implements Callable<Void> {
        private final IntArrayFIFOQueue queue;
        private final int[] distance;

        private BFSCutThread() {
            this.distance = new int[HarmonicCentrality.this.graph.numNodes()];
            this.queue = new IntArrayFIFOQueue();
        }

        public Void call() {
            IntArrayFIFOQueue queue = this.queue;
            ImmutableGraph graph = HarmonicCentrality.this.graph.copy();

            while (true) {
                int curr = HarmonicCentrality.this.nextNode.getAndIncrement();
                if (HarmonicCentrality.this.stop || curr >= graph.numNodes()) {
                    return null;
                }

                double apx_h = 0;
                double h = 0;
                double gamma = 0;
                double nd = 0;
                queue.clear();
                queue.enqueue(curr);
                Arrays.fill(distance, -1);
                int d = 0;

                while (!queue.isEmpty()) {
                    int node = queue.dequeueInt();
                    int dist = distance[node] + 1;
                    LazyIntIterator succ = graph.successors(node);
                    int s;
                    while ((s = succ.nextInt()) != -1) {
                        if (distance[s] == -1) {
                            queue.enqueue(s);
                            distance[s] = dist;
                        }
                    }

                    if (distance[node] > d) {
                        apx_h += h + gamma / ((d + 1) * (d + 2)) + (graph.numNodes() - nd) / (d + 2);
                        if (borassi_list.size() > k && apx_h / (graph.numNodes() - 1) <= borassi_list.first()[0]) {
                            return null;
                        }
                        ++d;
                    }
                    if (node != curr) {
                        h += 1 / d;
                    }
                    gamma += graph.outdegree(node);
                    ++nd;
                }

                Double new_h = h / (graph.numNodes() - 1);
                if (borassi_list.size() > 0) {
                    Double[] last = borassi_list.last();
                    if (last[0] < new_h) {
                        borassi_list.remove(last);
                        addEntry(new_h, (double)curr);
                    }
                }
                else {
                    addEntry(new_h, (double)curr);
                }
            }
        }
    }

    /** Adds a new Harmonic Centrality entry to the Borassi et al. top-k candidate set.
     *
     * @param value the value of the harmonic centrality.
     * @param index the id of the corresponding node.
     */
    private void addEntry(Double value, Double index) {
    Double[] newEntry = new Double[2];
    newEntry[0] = value;
    newEntry[1] = index;
    borassi_list.add(newEntry);
}

    /** Sorts a double array by keeping track of the corresponding index. It is useful in order to not losing
     *  the association between the nodes and their harmonic centrality value.
     * @param arr the input array (contains the harmonic centrality values corresponding to each node)
     * @return a 2D array: first [harmonic_centrality_value][corresponding_node_id].
     */
    static double[][] sort(double[] arr) {
        double[][] newArr = new double[arr.length][2];
        for (int i = 0; i < arr.length; ++i) {
            newArr[i][0] = arr[i];
            newArr[i][1] = i;
        }
        sort(newArr);
        return newArr;
    }

    /** Sorts a 2D array with respect to the value contained in the 1st dimension.
     *
     * @param arr the sorted array
     */
    static void sort(double[][] arr) {
        Arrays.sort(arr, new Comparator<double[]>() {
            public int compare(double[] e1, double[] e2) {
                return(Double.valueOf(e2[0]).compareTo(e1[0]));
            }
        });
    }

}
