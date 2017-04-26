import com.google.common.util.concurrent.AtomicDouble;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Eugenio on 4/26/17.
 */
public class ChechikTopCloseness {
    /** The graph under examination. */
    private final ImmutableGraph graph;
    /** Global progress logger. */
    private final ProgressLogger pl;
    /** Number of threads. */
    private final int numberOfThreads;
    private final double epsilon;
    private AtomicDouble[] apxFarness;
    private AtomicInteger nextNode = new AtomicInteger(0);
    private int[] samples;
    private double[] probabilities;


    public ChechikTopCloseness(ImmutableGraph graph, ProgressLogger pl, int numberOfThreads, double epsilon) {
        this.graph = graph;
        this.pl = pl;
        this.numberOfThreads = (numberOfThreads) == 0 ? Runtime.getRuntime().availableProcessors() : numberOfThreads;
        this.epsilon = epsilon;
        this.apxFarness = new AtomicDouble[this.graph.numNodes()];
    }

    public void compute() throws InterruptedException {
        Arrays.fill(apxFarness, new AtomicDouble(0.0D));
        ChechikEstimator estimator = new ChechikEstimator(graph, epsilon);
        estimator.computeCoefficients();
        samples = estimator.getSamples();
        probabilities = estimator.getProbabilities();
        System.out.println("S size: " + samples.length);
        ChechikTopCloseness.ComputeApproximationThread[] thread = new ChechikTopCloseness.ComputeApproximationThread[this.numberOfThreads];
        for (int i = 0; i < thread.length; ++i) {
            thread[i] = new ChechikTopCloseness.ComputeApproximationThread();
        }

        if (this.pl != null) {
            this.pl.start("Starting visits...");
            this.pl.expectedUpdates = (long) graph.numNodes();
            this.pl.itemsName = "nodes";
        }

        ExecutorService var11 = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        ExecutorCompletionService executorCompletionService = new ExecutorCompletionService(var11);

        int e = thread.length;

        while (e-- != 0) { executorCompletionService.submit(thread[e]);}

        try {
            e = thread.length;
            while (e-- != 0) { executorCompletionService.take().get(); }
        } catch (ExecutionException var9) {
            Throwable cause = var9.getCause();
            throw cause instanceof RuntimeException ? (RuntimeException)cause : new RuntimeException(cause.getMessage(), cause);
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
            this.distance = new int[ChechikTopCloseness.this.graph.numNodes()];
            this.queue = new IntArrayFIFOQueue();
        }

        public Void call() {
            int[] distance = this.distance;
            IntArrayFIFOQueue queue = this.queue;
            ImmutableGraph graph = ChechikTopCloseness.this.graph.copy();

            while (true) {
                int i = nextNode.getAndIncrement();
                if (i >= samples.length) {
                    return null;
                }
                queue.clear();
                queue.enqueue(samples[i]);
                Arrays.fill(distance, -1);
                distance[samples[i]] = 0;

                while (!queue.isEmpty()) { // Perform BFS and update farness
                    int sourceNode = queue.dequeueInt();
                    int d = distance[sourceNode] + 1;
                    LazyIntIterator successors = graph.successors(sourceNode);
                    int s;

                    while ((s = successors.nextInt()) != -1) {
                        if (distance[s] == -1) {
                            queue.enqueue(s);
                            distance[s] = d;
                            apxFarness[s].addAndGet((double) d / probabilities[i]);
                        }
                    }
                }
            }
        }
    }
}