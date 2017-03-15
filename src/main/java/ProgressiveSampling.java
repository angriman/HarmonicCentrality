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
    private double[] prevCloseness;
    private double[] nextCloseness;
    private double[] exactCloseness;
    private double[] absoluteErrors;
    private double[] relativeErrors;
    private int prevSamples;
    private int distanceLowerBound[];


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
        this.prevCloseness = new double[graph.numNodes()];
        this.nextCloseness = new double[graph.numNodes()];
        this.absoluteErrors = new double[graph.numNodes()];
        this.relativeErrors = new double[graph.numNodes()];
        this.exactHarmonic = new double[graph.numNodes()];
        this.exactCloseness = new double[graph.numNodes()];
        this.distanceLowerBound = new int[graph.numNodes()];
        Arrays.fill(this.exactHarmonic, -1.0D);
        Arrays.fill(this.exactCloseness, -1.0D);
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
        System.arraycopy(nextHarmonic, 0, toReturn, 0, nextHarmonic.length);
        double norm = (double)(graph.numNodes()) / (double)((graph.numNodes() - 1) * cumulatedSamples);
       // System.out.println(norm);
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
        private final int[] levels; // Diametro del grafo
        private final int[] nodeLevel;

        private ProgressiveSamplingThread() {
            this.distance = new int[ProgressiveSampling.this.graph.numNodes()];
            this.queue = new IntArrayFIFOQueue();
            this.nodeLevel = new int[ProgressiveSampling.this.graph.numNodes()];
            this.levels = new int[9];
        }

        public Void call() {
            int[] distance = this.distance;
            int[] nodeLevel = this.nodeLevel;
            int[] levels = this.levels;
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
                ProgressiveSampling.this.exactCloseness[samples[curr]] = 0;
                queue.clear();
                queue.enqueue(samples[curr]);
                Arrays.fill(distance, -1);
                Arrays.fill(nodeLevel, 0);
                Arrays.fill(levels, 0);
                distance[samples[curr]] = 0;

                while (!queue.isEmpty()) {
                    int node = queue.dequeueInt();
                    int d = distance[node] + 1;
                    double hd = 1.0D / (double) d;
                    LazyIntIterator successors = graph.successors(node);
                    int s;

                    levels[d]++;
                    nodeLevel[node] = d;

                    while ((s = successors.nextInt()) != -1) {
                        visitedArcs.getAndIncrement();
                        if (distance[s] == -1) {
                            queue.enqueue(s);
                            visitedNodes.getAndIncrement();
                            distance[s] = d;
                            ProgressiveSampling.this.nextHarmonic[s] += hd;
                            ProgressiveSampling.this.nextCloseness[s] += (double)d;
                            ProgressiveSampling.this.exactHarmonic[samples[curr]] += hd;
                            ProgressiveSampling.this.exactCloseness[samples[curr]] += (double)d;
                        }
                    }
                }

                updateUpperBounds(levels, nodeLevel);

                if (ProgressiveSampling.this.pl != null) {
                    synchronized (ProgressiveSampling.this.pl) {
                        ProgressiveSampling.this.pl.update();
                    }
                }
            }
        }
    }

    private void updateUpperBounds(int[] levels, int[] nodelevel) {
        for (int n = 0; n < graph.numNodes(); ++n) {
            int curLevel = nodelevel[n];
            int sum = 0;
            int count = 0;
            for (int i = 0; i < levels.length; ++i) {
                count += levels[i];
                sum += Math.max(1, Math.abs(i - curLevel)) * levels[i] - 1;
            }
            int numNodes = levels[curLevel];
            if (curLevel > 0) {
                numNodes += levels[curLevel-1];
            }
            if (curLevel < levels.length-1) {
                numNodes += levels[curLevel+1];
            }
            sum += Math.max(0, numNodes - graph.outdegree(n));
            updateVector(n, sum);
        }
    }

    private synchronized void updateVector(int node, int sum) {
        distanceLowerBound[node] = Math.max(distanceLowerBound[node], sum);
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
       // randomSamples = Math.min((int) Math.ceil((1 + ALPHA) * cumulatedSamples), samples.length - cumulatedSamples);
        randomSamples = Math.min(samples.length - cumulatedSamples, randomSamples);
        int degreeBatch = 1;
        if (iterations.get() > degreeBatch) {
           //samples = (new RandomSamplesExtractor(graph)).update(cumulatedSamples, samples, nextHarmonic, iterations.get());
            double[] estimatedCloseness = new double[nextCloseness.length];
            System.arraycopy(nextCloseness, 0, estimatedCloseness, 0, nextCloseness.length);
            for (int i = 0; i < nextCloseness.length; ++i) {
                estimatedCloseness[i] = 1.0D / estimatedCloseness[i]; // Other multipliers do not influence the sorting
            }
           // printMaxEstimatedCloseness(estimatedCloseness);
            samples = (new RandomSamplesExtractor(graph)).update(cumulatedSamples, samples, estimatedCloseness, iterations.get());
        }
        else {
            System.out.println("Degree");
        }
    }

    private void printMaxEstimatedCloseness(double[] estimated) throws IOException {
        double max = 0;
        for (double c : estimated) {
            max = Math.max(max, c);
        }
        double n = (double) graph.numNodes();
        max *= (double)cumulatedSamples * (n - 1) / n;

        Integer[] currentSamples = new Integer[cumulatedSamples];
        for (int i = 0; i < currentSamples.length; ++i) {
            currentSamples[i] = samples[i];
        }
        final Double[] gtClos = ArrayUtils.toObject((new Evaluate()).getClos());
        Arrays.sort(currentSamples, new Comparator<Integer>() {
            @Override
            public int compare(Integer t1, Integer t2) {
                int first = gtClos[t2].compareTo(gtClos[t2]);
                return first == 0 ? t2.compareTo(t1) : first;
            }
        });

        double kth = currentSamples[9];
        int highestEstimatedNode = 0;

        for (int c = 1; c < estimated.length; ++c) {
            if (!Arrays.asList(currentSamples).contains(c)) {
                if (estimated[c] > estimated[highestEstimatedNode]) {
                    highestEstimatedNode = c;
                }
            }
        }

        int highestEst = (int)(1 / estimated[highestEstimatedNode]);
        Integer[] succ = ArrayUtils.toObject(graph.successorArray(highestEstimatedNode));
        int intersection = 0;
        for (Integer i : succ) {
            if (Arrays.asList(currentSamples).contains(i)) {
                ++intersection;
            }
        }

       // highestEst += (succ.length - intersection) + 2*(graph.numNodes() - cumulatedSamples - intersection);
       // System.out.println("K-th = " + kth + "   highest estimated = " + highestEst);
    }

    private boolean stoppingConditions() {
        return !(maxSamplesReached || precisionReached);
    }

    private void checkstop() {
        Integer[] topk = new Integer[cumulatedSamples];
        for (int i = 0; i < cumulatedSamples; ++i) {
            topk[i] = (int) exactCloseness[samples[i]];
        }

        Arrays.sort(topk, new Comparator<Integer>() {
            @Override
            public int compare(Integer t1, Integer t2) {
                return t1.compareTo(t2);
            }
        });
        
        double[] topkClos = new double[topk.length];
        
        for (int i = 0; i < topk.length; ++i) {
            topkClos[i] = 1.0D / (double)topk[i];
        }

        double highest = 0;
        for (int i = cumulatedSamples; i < samples.length; ++i) {
            int curr = samples[i];
            double upperC = 1.0D / distanceLowerBound[curr];
            highest = Math.max(highest, upperC);
        }

        highest *= (graph.numNodes() - 1);
      //  System.out.println(topkClos[0]*(graph.numNodes() - 1) + " vs " + highest);
    }


    private void checkPrecision() throws IOException {
        final Double[] gt = ArrayUtils.toObject((new Evaluate()).getGT());
        final Double[] gtClos = ArrayUtils.toObject((new Evaluate()).getClos());

       // checkstop();
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

        JSONArray abs = new JSONArray(Arrays.toString(absoluteErrors));
        JSONArray rel = new JSONArray(Arrays.toString(relativeErrors));
        double[][] h = HarmonicCentrality.sort(nextHarmonic);
        double[][] c = HarmonicCentrality.sort(nextCloseness);
        double[] harmonics = new double[graph.numNodes()];
        double[] closeness = new double[graph.numNodes()];
        double[] nodes = new double[graph.numNodes()];
        double[] nodesC = new double[graph.numNodes()];

        for (int i = 0; i < harmonics.length; ++i) {
            harmonics[i] = h[i][0] * normalization(true);
            closeness[i] = ((double)graph.numNodes() - 1) / c[i][0];
            nodes[i] = h[i][1];
            nodesC[i] = c[i][1];
        }

        Integer[] sortedCentralities = new Integer[graph.numNodes()];
        Integer[] sortedCloseness = new Integer[graph.numNodes()];
        NodeIterator nodeIterator = graph.nodeIterator();
        int count = 0;
        while (nodeIterator.hasNext()) {
            int next = nodeIterator.nextInt();
            sortedCloseness[count] = next;
            sortedCentralities[count++] = next;
        }

        Arrays.sort(sortedCentralities, new Comparator<Integer>() {
            @Override
            public int compare(Integer t1, Integer t2) {
                int first = gt[t2].compareTo(gt[t1]);
                return (first == 0) ? t2.compareTo(t1) : first;
            }
        });

        Arrays.sort(sortedCloseness, new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                int first = gtClos[o2].compareTo(gtClos[o1]);
                return (first == 0) ? o2.compareTo(o1) : first;
            }
        });

        JSONObject errors = new JSONObject();
        errors.put("Absolute", abs);
        errors.put("Relative", rel);
        errors.put("Harmonics", harmonics);
        errors.put("Nodes", nodes);
        errors.put("Closeness", closeness);
        errors.put("ClosenessNodes", nodesC);
        Integer[] currentSamples = new Integer[cumulatedSamples];

        //System.arraycopy(samples, 0, currentSamples, 0, currentSamples.length);
        for (int i = 0; i < currentSamples.length; ++i) {
            currentSamples[i] = samples[i];
        }
        Arrays.sort(currentSamples, new Comparator<Integer>() {
            @Override
            public int compare(Integer t1, Integer t2) {
                //int first = gt[t2].compareTo(gt[t1]);
                int first = gtClos[t2].compareTo(gtClos[t1]);
                return (first == 0) ? t2.compareTo(t1) : first;
            }
        });

        errors.put("CurrentExact", currentSamples);
    //    errors.put("GTNodes", sortedCentralities);
     //   errors.put("GT", gt);
        errors.put("GTNodes", sortedCloseness);
        errors.put("GT", gtClos);
       // errors.put("RealAbsolute", realAbs);
        //errors.put("RealRelative", realRel);

        String path = "./results/errors/";
        Test.checkPath(path);
        path += Test.currentGraphName() + "/";
        Test.checkPath(path);

        try (FileWriter file = new FileWriter(path+iterations.get() +  ".json")) {
           errors.write(file);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
      //  int k = 10;
//        if (currentSamples.length > k) {
//            double confidence = approximationPrecision();
//
//            Integer[] topComputed = new Integer[k];
//            System.arraycopy(currentSamples, 0, topComputed, 0, k);
//            double highestEstimated = 0;
//            int index = 0;
//            for (double i : nodes) {
//                if (!Arrays.asList(topComputed).contains((int) i)) {
//                    highestEstimated = nextHarmonic[(int)i];
//                    index = (int)i;
//                    break;
//                }
//                index = (int)i;
//            }
//
//
//            double threshold = highestEstimated  + (double) graph.outdegree(index) + 0.5 * Math.max(0, (graph.numNodes() - cumulatedSamples - graph.outdegree(index)));//confidence + highestEstimated;
//            threshold /= (double)(graph.numNodes() - 1);
//            System.out.println("Highest estimated = " + threshold);
//            //System.out.println("Confidence = " + confidence);
//            System.out.println("GT = " + (gt[currentSamples[k-1]] / ((double)(graph.numNodes() - 1))));
//            if (gt[currentSamples[k-1]] / ((double)(graph.numNodes() - 1)) > threshold) {
//                System.out.println("STOP");
//            }
//        }
        //System.out.println(approximationPrecision());
        System.arraycopy(nextHarmonic, 0, prevHarmonic, 0, nextHarmonic.length);
    }

    private double approximationPrecision() {
        if (cumulatedSamples <= 0) {
            return 0;
        }
       // return Math.pow((double) graph.numNodes() / ((double)(graph.numNodes() - 1)), 2) * 0.5 * (1.0D / (Math.log(2))) * (1.0D / (double)cumulatedSamples) * Math.log(graph.numNodes());
        return Math.sqrt(Math.log(graph.numNodes()) / cumulatedSamples);

    }
}
