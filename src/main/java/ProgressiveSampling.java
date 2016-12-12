import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.NodeIterator;
import org.apache.commons.lang.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by eugenio on 12/12/16.
 */
class ProgressiveSampling {
    /** The graph under examination. */
    private final ImmutableGraph graph;
    /** Progressive sampling pace. */
    private static final double ALPHA = 0.5;
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
    /** Number of total visited nodes. */
    private final AtomicInteger visitedNodes;
    /** Number of total visited arcs. */
    private final AtomicInteger visitedArcs;
    /** Required precision for the Eppstein algorithm. */
    private double precision;
    /** All random samples required (we hope to use less than the one contained in here). */
    private int[] samples;
    /** Multiplicative constant in front of the Eppstein number of random samples formula.
     * The formula is: number of samples = log(n) / precision^2 */
    private static final double C = 1;
    /** The number of extracted random samples until now. */
    private int randomSamples;
    /** Cumulated samples */
    private int cumulatedSamples = 0;
    /** Algorithm iterations. */
    private final AtomicInteger iterations;
    private boolean maxSamplesReached;


    ProgressiveSampling(ImmutableGraph graph, int requestedThreads, ProgressLogger pl) {
        this.pl = pl;
        this.graph = graph;
        this.harmonic = new double[graph.numNodes()];
        this.nextNode = new AtomicInteger();
        this.visitedArcs = new AtomicInteger();
        this.visitedNodes = new AtomicInteger();
        this.iterations = new AtomicInteger();
        this.numberOfThreads = requestedThreads != 0 ? requestedThreads:Runtime.getRuntime().availableProcessors();
        this.randomSamples = (int) Math.max(Math.log(graph.numNodes()), this.numberOfThreads);
    }

    /**
     * Computes (log(n) / epsilon^2) random samples.
     * @return An int array containing the random samples.
     */
    private int[] computeRandomSamples() {
        int[] result = new int[numberOfSamplesUpperBound()];
        List<Integer> nodeList = new ArrayList<>();
        NodeIterator nodeIterator = graph.nodeIterator();

        while (nodeIterator.hasNext()) {
            nodeList.add(nodeIterator.nextInt());
        }

        Collections.shuffle(nodeList);
        Integer[] shuffledArray = nodeList.toArray(new Integer[0]);

        System.arraycopy(ArrayUtils.toPrimitive(shuffledArray), 0, result, 0, result.length - 1);
        return result;
    }

    private int numberOfSamplesUpperBound() {
        /* Eppstein algorithm, it needs Big Theta of (C * log(n) / epsilon^2) nodes. */
        return (int)Math.ceil(C * Math.log(graph.numNodes()) / Math.pow(this.precision, 2));
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

    void setPrecision(double precision) {
        this.precision = precision;
    }

    void compute() throws InterruptedException {
        ProgressiveSampling.ProgressiveSamplingThread[] thread = new ProgressiveSampling.ProgressiveSamplingThread[this.numberOfThreads];

        for (int executorService = 0; executorService < thread.length; ++executorService) {
            thread[executorService] = new ProgressiveSampling.ProgressiveSamplingThread();
        }

        if (this.pl != null) {
            this.pl.start("Starting visits...");
            this.pl.expectedUpdates = (long) this.graph.numNodes();
            this.pl.itemsName = "nodes";
        }

        while (!maxSamplesReached) {

            this.samples = computeRandomSamples();

            ExecutorService var11 = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            ExecutorCompletionService executorCompletionService = new ExecutorCompletionService(var11);

            int e = thread.length;

            while (e-- != 0) {
                executorCompletionService.submit(thread[e]);
            }

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

            iterations.getAndIncrement();
            newRandomSamples();
        }

        if (pl != null) {
            pl.done();
        }
    }

    private final class ProgressiveSamplingThread implements Callable<Void> {
        private final IntArrayFIFOQueue queue;
        private final int[] distance;

        private ProgressiveSamplingThread() {
            this.distance = new int[ProgressiveSampling.this.graph.numNodes()];
            this.queue = new IntArrayFIFOQueue();
        }

        public Void call() {
            int[] distance = this.distance;
            IntArrayFIFOQueue queue = this.queue;
            ImmutableGraph graph = ProgressiveSampling.this.graph.copy();

            while (true) {
                int curr = ProgressiveSampling.this.nextNode.getAndIncrement();
                if (ProgressiveSampling.this.stop || curr >= randomSamples) {
                    return null;
                }

                queue.clear();
                queue.enqueue(samples[curr]);
                Arrays.fill(distance, -1);
                distance[samples[curr]] = 0;
            }
        }
    }

    private void newRandomSamples() {
        cumulatedSamples += randomSamples;
        if (cumulatedSamples == samples.length) {
            maxSamplesReached = true;
        }
        randomSamples = Math.min((int) Math.ceil((1 + ALPHA) * randomSamples), samples.length - cumulatedSamples);
    }

    private boolean stoppingConditions() {

        return true;
    }
}
