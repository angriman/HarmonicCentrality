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
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;
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
    private final ImmutableGraph graph;
    private final double[] harmonic;
    private final ProgressLogger pl;
    private final int numberOfThreads;
    private final AtomicInteger nextNode;
    private volatile boolean stop;
    double precision = 0.2;
    private final int[] randomSamples;
    private final double normalization;
    boolean top_k = false;
    int k = 0;
    private int[] candidateSet;
    private double[] candidateSetHarmonics;
    private static final double ALPHA = 1.01;

    private final AtomicInteger visitedNodes;
    private final AtomicInteger visitedArcs;

    HarmonicCentrality(ImmutableGraph graph, int requestedThreads, ProgressLogger pl) {
        this.pl = pl;
        this.graph = graph;
        this.harmonic = new double[graph.numNodes()];
        this.nextNode = new AtomicInteger();
        this.visitedNodes = new AtomicInteger();
        this.visitedArcs = new AtomicInteger();
        this.numberOfThreads = requestedThreads != 0?requestedThreads:Runtime.getRuntime().availableProcessors();
        this.randomSamples = pickRandomSamples(numberOfSamples());
        this.normalization = (double)graph.numNodes() / (double)randomSamples.length;
    }

    int visitedNodes() {
        return this.visitedNodes.get();
    }

    int visitedArcs() {
        return this.visitedArcs.get();
    }

    private int numberOfSamples() {
        if (top_k) {
            return num_samples();
        }
        return (int)Math.ceil(Math.log(graph.numNodes()) / Math.pow(precision, 2));
    }

    private int[] pickRandomSamples(int k) {
        int[] samples = new int[k];
        if (k == graph.numNodes()) {
            for (int i = 0; i < k; ++i) {
                samples[i] = i;
            }
        }
        else {
            int count = 0;
            Random rand = new Random();
            while (count < k) {
                int randomNum = rand.nextInt(graph.numNodes());
                if (!ArrayUtils.contains(samples, randomNum)) {
                    samples[count] = randomNum;
                    ++count;
                }
            }
        }

        return samples;
    }

    private int num_samples() {
        return (int)Math.ceil(Math.pow(graph.numNodes(), 2/3) * Math.pow(Math.log(graph.numNodes()) , 1/3));
    }

    private double f_function() {
        return ALPHA * Math.sqrt(Math.log(graph.numNodes()) / randomSamples.length);
    }

    void compute() throws InterruptedException {
        HarmonicCentrality.HarmonicApproximationThread[] thread = new HarmonicCentrality.HarmonicApproximationThread[this.numberOfThreads];

        for(int executorService = 0; executorService < thread.length; ++executorService) {
            thread[executorService] = new HarmonicCentrality.HarmonicApproximationThread();
        }

        if(this.pl != null) {
            this.pl.start("Starting visits...");
            this.pl.expectedUpdates = (long)this.graph.numNodes();
            this.pl.itemsName = "nodes";
        }

        ExecutorService var11 = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        ExecutorCompletionService executorCompletionService = new ExecutorCompletionService(var11);
        int e = thread.length;

        while(e-- != 0) executorCompletionService.submit(thread[e]);

        try {
            e = thread.length;

            while(e-- != 0) {
                executorCompletionService.take().get();
            }
        }
        catch (ExecutionException var9) {
            this.stop = true;
            Throwable cause = var9.getCause();
            throw cause instanceof RuntimeException?(RuntimeException)cause:new RuntimeException(cause.getMessage(), cause);
        }
        finally {
            var11.shutdown();
        }

        if (top_k) {
            double[][] h = sort(harmonic);

            int from = k - 1;
            int additiveSamples = 0;
            double threshold = 2 * f_function();

            while (h[from + additiveSamples][0] > h[from][0] - threshold) {
                ++additiveSamples;
            }

            candidateSet = new int[k + additiveSamples];
            candidateSetHarmonics = new double[candidateSet.length];
            for (int i = 0; i < candidateSet.length; ++i) {
                candidateSet[i] = (int)(h[graph.numNodes() - 1 - k - additiveSamples + i][1]);
            }

            HarmonicCentrality.HarmonicExactComputationThread[] exactComputationThreads = new HarmonicCentrality.HarmonicExactComputationThread[this.numberOfThreads];
            HarmonicCentrality.this.nextNode.set(0);

            for (int executorService = 0; executorService < exactComputationThreads.length; ++executorService) {
                exactComputationThreads[executorService] = new HarmonicCentrality.HarmonicExactComputationThread();
            }

            if (this.pl != null) {
                this.pl.start("Starting visits...");
                this.pl.expectedUpdates = (long)this.graph.numNodes();
                this.pl.itemsName = "nodes";
            }

            var11 = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            executorCompletionService = new ExecutorCompletionService(var11);

            e = exactComputationThreads.length;

            while(e-- != 0) executorCompletionService.submit(exactComputationThreads[e]);

            try {
                e = exactComputationThreads.length;

                while(e-- != 0) {
                    executorCompletionService.take().get();
                }
            } catch (ExecutionException var9) {
                this.stop = true;
                Throwable cause = var9.getCause();
                throw cause instanceof RuntimeException?(RuntimeException)cause:new RuntimeException(cause.getMessage(), cause);
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


            while(true) {
                int curr = HarmonicCentrality.this.nextNode.getAndIncrement();
                if(HarmonicCentrality.this.stop || curr >= randomSamples.length) {
                    return null;
                }

                queue.clear();
                queue.enqueue(randomSamples[curr]);
                Arrays.fill(distance, -1);
                distance[randomSamples[curr]] = 0;

                while(!queue.isEmpty()) {
                    int node = queue.dequeueInt();
                    int d = distance[node] + 1;
                    LazyIntIterator successors = graph.successors(node);

                    int s;
                    while((s = successors.nextInt()) != -1) {
                        visitedArcs.getAndIncrement();
                        if(distance[s] == -1) {
                            queue.enqueue(s);
                            visitedNodes.getAndIncrement();
                            distance[s] = d;
                        }
                    }
                }

                int i = 0;
                for (int d : distance) {
                    HarmonicCentrality.this.harmonic[i++] += HarmonicCentrality.this.normalization * ((d > 0) ? 1.0D / (double)d : 0);
                }

                if(HarmonicCentrality.this.pl != null) {
                    synchronized(HarmonicCentrality.this.pl) {
                        HarmonicCentrality.this.pl.update();
                    }
                }
            }
        }
    }

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
                queue.enqueue(curr);
                Arrays.fill(distance, -1);
                distance[curr] = 0;

                while (!queue.isEmpty()) {
                    int node = queue.dequeueInt();
                    int d = distance[node] + 1;
                    double hd = 1.0D / (double) d;
                    LazyIntIterator successors = graph.successors(node);

                    int s;
                    while ((s = successors.nextInt()) != -1) {
                        if (distance[s] == -1) {
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

    private double[][] sort(double[] arr) {
        double[][] newArr = new double[arr.length][2];
        for (int i = 0; i < arr.length; ++i) {
            newArr[i][0] = arr[i];
            newArr[i][1] = i;
        }
        sort(newArr);
        return newArr;
    }

    private void sort(double[][] arr) {
        Arrays.sort(arr, new Comparator<double[]>() {
            public int compare(double[] e1, double[] e2) {
                return(Double.valueOf(e2[0]).compareTo(e1[0]));
            }
        });
    }

    private double[][] sortAndCut() {
        double[][] result = new double[candidateSet.length][2];
        for (int i = 0; i < candidateSet.length; ++i) {
            result[i][0] = candidateSetHarmonics[i];
            result[i][1] = candidateSet[i];
        }
        sort(result);
        double[][] toReturn = new double[k][2];
        System.arraycopy(result, 0, toReturn, 0, k);
        return toReturn;
    }
}
