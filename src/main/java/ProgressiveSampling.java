import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import org.apache.commons.lang.ArrayUtils;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by eugenio on 12/12/16.
 */
class ProgressiveSampling {
    /** The graph under examination. */
    private final ImmutableGraph graph;
    /** Progressive sampling pace. */
    private static final double ALPHA = 0;
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
    private double[] exactHarmonic;
    private double[] absoluteErrors;
    private double[] relativeErrors;
    private int prevSamples;


    ProgressiveSampling(ImmutableGraph graph, int requestedThreads, ProgressLogger pl) {
        this.pl = pl;
        this.graph = graph;
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
        this.exactHarmonic = new double[graph.numNodes()];
        Arrays.fill(this.exactHarmonic, -1);
    }

    /**
     * Computes (log(n) / epsilon^2) random samples.
     * @return An int array containing the random samples.
     */
    private int[] computeRandomSamples() {
        RandomSamplesExtractor extractor = new RandomSamplesExtractor(graph);
        return extractor.compute();
    }

    public double[] getNormalizedHarmonics() {
        double[] toReturn = new double[nextHarmonic.length];
        System.arraycopy(nextHarmonic, 0, toReturn, 0, nextHarmonic.length - 1);
        double norm = (double)(graph.numNodes()) / (double)((graph.numNodes() - 1) * cumulatedSamples);
        System.out.println(norm);
        for (int i = 0; i < toReturn.length; ++i) {
            toReturn[i] *= norm;
        }
        return toReturn;
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

    void compute() throws InterruptedException, IOException {
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
            try {
                newRandomSamples();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
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
                if (curr >= randomSamples + cumulatedSamples) {
                    ProgressiveSampling.this.nextNode.getAndDecrement();
                    return null;
                }
                if (ProgressiveSampling.this.stop) {
                    return null;
                }

                //System.out.println("Iteration = " + iterations.get() + " node = " + curr + " continue until node <= " + (randomSamples + cumulatedSamples));
                ProgressiveSampling.this.exactHarmonic[samples[curr]] = 0;
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
                            ProgressiveSampling.this.nextHarmonic[s] += hd;
                            ProgressiveSampling.this.exactHarmonic[samples[curr]] += hd;
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

    private double normalization(boolean current) {
        if (!current && prevSamples == 0) {
            return 0;
        }
        return (double)graph.numNodes() / (double)((current ? cumulatedSamples : prevSamples) * (graph.numNodes() - 1));
    }

    private void newRandomSamples() throws IOException {
        cumulatedSamples += randomSamples;

        if (cumulatedSamples == samples.length) {
            maxSamplesReached = true;
        }
        checkPrecision();
        prevSamples = cumulatedSamples;
        //randomSamples = Math.min((int) Math.ceil((1 + ALPHA) * cumulatedSamples), samples.length - cumulatedSamples);
        randomSamples = Math.min(samples.length - cumulatedSamples, randomSamples);
        samples = (new RandomSamplesExtractor(graph)).update(cumulatedSamples, samples, nextHarmonic);
    }

    private boolean stoppingConditions() {
        return !(maxSamplesReached || precisionReached);
    }

    private void checkPrecision() throws IOException {
        final Double[] gt = ArrayUtils.toObject((new Evaluate()).getGT());
       // double[] realAbs = new double[nextHarmonic.length];
        //double[] realRel = new double[nextHarmonic.length];
        double nextNorm = normalization(true);
        double prevNorm = normalization(false);
        for (int i = 0; i < graph.numNodes(); ++i) {
            absoluteErrors[i] = Math.abs(nextHarmonic[i] * nextNorm - prevHarmonic[i] * prevNorm);

            relativeErrors[i] = (prevHarmonic[i] == 0) ?
                    nextHarmonic[i] * nextNorm :
                    Math.abs((nextHarmonic[i] * nextNorm - prevHarmonic[i] * prevNorm) / (prevHarmonic[i] * prevNorm));
            //gt[i] /= (double)(graph.numNodes() - 1);
           // realAbs[i] = Math.abs(nextHarmonic[i] * normalization(true) - gt[i]);
           // realRel[i] = gt[i] == 0 ? nextHarmonic[i] * normalization(true) : Math.abs(nextHarmonic[i] * normalization(true) - gt[i]) / gt[i];
        }


//        JSONArray abs = new JSONArray(Arrays.toString(absoluteErrors));
//        JSONArray rel = new JSONArray(Arrays.toString(relativeErrors));
//        double[][] h = HarmonicCentrality.sort(nextHarmonic);
//        double[] harmonics = new double[graph.numNodes()];
//        double[] nodes = new double[graph.numNodes()];
//        for (int i = 0; i < harmonics.length; ++i) {
//            harmonics[i] = h[i][0] * normalization(true);
//            nodes[i] = h[i][1];
//        }
        Integer[] sortedCentralities = new Integer[graph.numNodes()];
        for (int i = 0; i < graph.numNodes(); ++i) {
            sortedCentralities[i] = i;
        }
        Arrays.sort(sortedCentralities, new Comparator<Integer>() {
            @Override
            public int compare(Integer t1, Integer t2) {
                return gt[t2].compareTo(gt[t1]);
            }
        });
        JSONObject errors = new JSONObject();
//        errors.put("Absolute", abs);
//        errors.put("Relative", rel);
//        errors.put("Harmonics", harmonics);
//        errors.put("Nodes", nodes);
        int[] currentSamples = new int[cumulatedSamples];
        System.arraycopy(samples, 0, currentSamples, 0, currentSamples.length);
        errors.put("CurrentExact", currentSamples);
        errors.put("GTNodes", sortedCentralities);
        errors.put("GT", gt);
       // errors.put("RealAbsolute", realAbs);
        //errors.put("RealRelative", realRel);

        String path = "./results/errors/";
        Test.checkPath(path);
        path += Test.currentGraphName() + "/";
        Test.checkPath(path);

        try (FileWriter file = new FileWriter(path+iterations.get()+".json")) {
           errors.write(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.arraycopy(nextHarmonic, 0, prevHarmonic, 0, nextHarmonic.length - 1);
    }
}
