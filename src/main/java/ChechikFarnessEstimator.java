import com.google.common.util.concurrent.AtomicDouble;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import org.apache.commons.lang.ArrayUtils;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Runtime.getRuntime;
import static java.lang.System.console;
import static java.lang.System.out;
import static java.util.Arrays.fill;
import static java.util.concurrent.Executors.newFixedThreadPool;

/**
 * Created by Eugenio on 4/26/17.
 */
public class ChechikFarnessEstimator {
    /**
     * The graph under examination.
     */
    private final ImmutableGraph graph;
    /**
     * Global progress logger.
     */
    private final ProgressLogger pl;
    /**
     * Number of threads.
     */
    private final int numberOfThreads;
    private double epsilon;
    private double[] apxFarness;
    private AtomicInteger nextNode = new AtomicInteger(0);
    private int[] samples;
    private double[] probabilities;
    private boolean[] exact;
    private int numberOfBFS = 0;
    private int[] farness;

    public double[] getApxFarness() {
        for (int i = 0; i < graph.numNodes(); ++i) {
            if (exact[i]) {
                apxFarness[i] = (double)farness[i];
            }
        }
        return apxFarness;
    }

    public int[] getFarness() {return farness;}

    public int getNumberOfThreads() {return numberOfBFS;}

    public boolean[] getExact() {return exact;}

    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }


    public ChechikFarnessEstimator(ImmutableGraph graph, ProgressLogger pl, int numberOfThreads, double epsilon) {
        this.graph = graph;
        this.pl = pl;
        this.numberOfThreads = (numberOfThreads) == 0 ? getRuntime().availableProcessors() : numberOfThreads;
        this.epsilon = epsilon;
        this.apxFarness = new double[this.graph.numNodes()];
    }

    public void compute() throws InterruptedException {
        fill(apxFarness, 0);
        ChechikEstimator estimator = new ChechikEstimator(graph, epsilon);
        estimator.computeCoefficients();
        samples = estimator.getSamples();
        probabilities = estimator.getProbabilities();
        exact = estimator.getExact();
        numberOfBFS = estimator.getNumberOfBFS();
        numberOfBFS += samples.length;
        this.farness = estimator.getFarness();
        ChechikFarnessEstimator.ComputeApproximationThread[] thread = new ChechikFarnessEstimator.ComputeApproximationThread[this.numberOfThreads];
        for (int i = 0; i < thread.length; ++i) {
            thread[i] = new ChechikFarnessEstimator.ComputeApproximationThread();
        }

        if (this.pl != null) {
            this.pl.start("Starting visits...");
            this.pl.expectedUpdates = (long) graph.numNodes();
            this.pl.itemsName = "nodes";
        }

        ExecutorService var11 = newFixedThreadPool(getRuntime().availableProcessors());
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
            Throwable cause = var9.getCause();
            throw cause instanceof RuntimeException ? (RuntimeException) cause : new RuntimeException(cause.getMessage(), cause);
        } finally {
            var11.shutdown();
        }

        if (this.pl != null) {
            this.pl.done();
        }
    }

    private final class ComputeApproximationThread implements Callable<Void> {
        private final IntArrayFIFOQueue queue;
        private final int[] distance;

        private ComputeApproximationThread() {
            this.distance = new int[ChechikFarnessEstimator.this.graph.numNodes()];
            this.queue = new IntArrayFIFOQueue();
        }

        public Void call() {
            int[] distance = this.distance;
            IntArrayFIFOQueue queue = this.queue;
            ImmutableGraph graph = ChechikFarnessEstimator.this.graph.copy();

            while (true) {
                int i = nextNode.getAndIncrement();
                if (i >= samples.length) {
                    return null;
                }

                int v = samples[i];
                double p = probabilities[i];
                queue.clear();
                queue.enqueue(v);
                exact[v] = true;
                fill(distance, -1);
                distance[v] = 0;

                while (!queue.isEmpty()) {
                    int sourceNode = queue.dequeueInt();
                    int d = distance[sourceNode] + 1;
                    LazyIntIterator successors = graph.successors(sourceNode);
                    int s;

                    while ((s = successors.nextInt()) != -1) {
                        if (distance[s] == -1) {
                            queue.enqueue(s);
                            distance[s] = d;
                            updateApxFarness(v, s, d, p);
                        }
                    }
                }

                if (ChechikFarnessEstimator.this.pl != null) {
                    synchronized (ChechikFarnessEstimator.this.pl) {
                        ChechikFarnessEstimator.this.pl.update();
                    }
                }
            }
        }
    }

    private synchronized void updateApxFarness(int source, int dest, double d, double p) {
        apxFarness[dest] += d / p;
        farness[source] += d;
    }


    private void print(String s) {
        System.out.println(s);
    }
}