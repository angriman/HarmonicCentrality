import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.NodeIterator;
import org.apache.commons.lang.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newFixedThreadPool;

/**
 * Wrote by Eugenio on 4/26/17.
 */
class ChechikEstimator {
    private final ImmutableGraph graph;
    private double[] lambda;
    private double[] probabilities;
    private final int k;
    private ArrayList<Integer> s = new ArrayList<>();
    private ArrayList<Double> sp = new ArrayList<>();
    private boolean[] exact;
    private int numberOfBFS = 0;
    private int[] farness;
    private int[] samples;
    private AtomicInteger nextNode = new AtomicInteger(0);

    int[] getSamples() {return ArrayUtils.toPrimitive(s.toArray(new Integer[s.size()]));}
    int[] getFarness() {return farness;}
    int getNumberOfBFS() {return numberOfBFS;}
    double[] getProbabilities() {return ArrayUtils.toPrimitive(sp.toArray(new Double[sp.size()]));}
    boolean[] getExact() {return exact;}

    ChechikEstimator(ImmutableGraph graph, double epsilon) {
        this.graph = graph;
        this.lambda = new double[graph.numNodes()];
        this.probabilities = new double[graph.numNodes()];
        this.k = (int)(Math.ceil(Math.log(graph.numNodes()) / Math.pow(epsilon, 2)));
        this.exact = new boolean[graph.numNodes()];
        this.farness = new int[graph.numNodes()];
    }

    void computeCoefficients() {
        Arrays.fill(lambda, 1.0D / (double) graph.numNodes());
        updateLambda();
        computeProbabilities();
        s = new ArrayList<>();
        sp = new ArrayList<>();
        NodeIterator iterator = graph.nodeIterator();
        while (iterator.hasNext()) {
            int v = iterator.nextInt();
            if (Math.random() < probabilities[v]) {
                s.add(v);
                sp.add(probabilities[v]);
            }
        }
    }

    private void updateLambda() {
        int INITIAL_SET_SIZE = Math.min(graph.numNodes(), Math.max((int)Math.ceil(log2(graph.numNodes())), 2));
        numberOfBFS = INITIAL_SET_SIZE;
        this.samples = new Random().ints(0, graph.numNodes()).distinct().limit(INITIAL_SET_SIZE).toArray();

        int numberOfThreads = getRuntime().availableProcessors();
        ChechikEstimator.UpdateLambdaThread[] thread = new ChechikEstimator.UpdateLambdaThread[numberOfThreads];
        for (int i = 0; i < thread.length; ++i) {
            thread[i] = new ChechikEstimator.UpdateLambdaThread();
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
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        } finally {
            var11.shutdown();
        }
    }

    private static int log2(int x)  {
        return (int) (Math.log(x) / Math.log(2));
    }

    private final class UpdateLambdaThread implements Callable<Void> {
        private final IntArrayFIFOQueue queue;
        private final int[] distance;

        private UpdateLambdaThread() {
            this.distance = new int[ChechikEstimator.this.graph.numNodes()];
            this.queue = new IntArrayFIFOQueue();
        }

        public Void call() {
            int[] distance = this.distance;
            IntArrayFIFOQueue queue = this.queue;
            ImmutableGraph graph = ChechikEstimator.this.graph.copy();

            while (true) {
                int i = ChechikEstimator.this.nextNode.getAndIncrement();
                if (i >= samples.length) {
                    return null;
                }

                int v = samples[i];
                int total_distance = 0;

                queue.clear();
                queue.enqueue(v);
                Arrays.fill(distance, -1);
                distance[v] = 0;

                while(!queue.isEmpty()) {
                    int node = queue.dequeueInt();
                    int d = distance[node] + 1;
                    LazyIntIterator successors = graph.successors(node);
                    int s;

                    while((s = successors.nextInt()) != -1) {
                        if(distance[s] == -1) {
                            queue.enqueue(s);
                            distance[s] = d;
                            total_distance += d;
                            updateFarness(s, d);
                        }
                    }
                }

                exact[v] = true;
                for (int w = 0; w < graph.numNodes(); ++w) {
                    updateLambda(w, total_distance, distance);
                }
            }
        }
    }

    private synchronized void updateLambda(int node, int d, int[] distance) {
        this.lambda[node] = Math.max(this.lambda[node], (double)distance[node] / (double)d);
    }

    private synchronized void updateFarness(int source, int d) {
        farness[source] += d;
    }

    private void computeProbabilities() {
        NodeIterator iterator = graph.nodeIterator();
        while (iterator.hasNext()) {
            int v = iterator.nextInt();
            probabilities[v] = Math.min(1.0D, (double)k*lambda[v]);
        }
    }
}
