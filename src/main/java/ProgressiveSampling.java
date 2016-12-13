import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.NodeIterator;
import org.apache.commons.lang.ArrayUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
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
    private boolean precisionReached;
    private double[] prevHarmonic;
    private double[] nextHarmonic;
    private double[] absoluteErrors;
    private double[] relativeErrors;


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
        this.prevHarmonic = new double[graph.numNodes()];
        this.nextHarmonic = new double[graph.numNodes()];
        this.absoluteErrors = new double[graph.numNodes()];
        this.relativeErrors = new double[graph.numNodes()];
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
        this.samples = computeRandomSamples();
        if (this.pl != null) {
            this.pl.start("Starting visits...");
            this.pl.expectedUpdates = (long) samples.length;
            this.pl.itemsName = "nodes";
        }


        while (stoppingConditions()) {

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

                while (!queue.isEmpty()) {
                    int node = queue.dequeueInt();
                    int d = distance[node] + 1;
                    double hd = 1.0D / (double) d;
                    LazyIntIterator successors = graph.successors(node);
                    int s;

                    while ((s = successors.nextInt()) != -1) {
                        visitedArcs.getAndIncrement();
                        if (distance[s] == -1) {
                            queue.enqueue(s);
                            visitedNodes.getAndIncrement();
                            distance[s] = d;
                            ProgressiveSampling.this.nextHarmonic[s] += hd * normalization();
                        }
                    }
                }

                if (ProgressiveSampling.this.pl != null) {
                    synchronized (ProgressiveSampling.this.pl) {
                        ProgressiveSampling.this.pl.update();
                    }
                }
            }
        }
    }

    private double normalization() {
        return (double)graph.numNodes() / (double)(ProgressiveSampling.this.randomSamples * (ProgressiveSampling.this.graph.numNodes() - 1));
    }

    private void newRandomSamples() {
        cumulatedSamples += randomSamples;
        if (cumulatedSamples == samples.length) {
            maxSamplesReached = true;
        }
        checkPrecision();

        randomSamples = Math.min((int) Math.ceil((1 + ALPHA) * randomSamples), samples.length - cumulatedSamples);
    }

    private boolean stoppingConditions() {
        return !(maxSamplesReached || precisionReached);
    }

    private void checkPrecision() {

        for (int i = 0; i < graph.numNodes(); ++i) {
            ProgressiveSampling.this.absoluteErrors[i] = Math.abs(ProgressiveSampling.this.nextHarmonic[i] - ProgressiveSampling.this.prevHarmonic[i]);
            ProgressiveSampling.this.relativeErrors[i] = (ProgressiveSampling.this.prevHarmonic[i] == 0) ?
                    ProgressiveSampling.this.nextHarmonic[i] :
                    Math.abs((ProgressiveSampling.this.nextHarmonic[i] - ProgressiveSampling.this.prevHarmonic[i]) / ProgressiveSampling.this.prevHarmonic[i]);
        }

        JSONArray abs = new JSONArray(Arrays.asList(absoluteErrors));
        JSONArray rel = new JSONArray(Arrays.asList(relativeErrors));

        JSONObject errors = new JSONObject();
        errors.put("Absolute", abs);
        errors.put("Relative", rel);

        try (FileWriter file = new FileWriter("./results/errors"+iterations.get()+".json")) {
           errors.write(file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.arraycopy(nextHarmonic, 0, prevHarmonic, 0, nextHarmonic.length - 1);

    }
}
