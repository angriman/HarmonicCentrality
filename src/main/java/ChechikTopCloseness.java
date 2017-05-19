import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.NodeIterator;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newFixedThreadPool;

/**
 * Wrote by Eugenio on 4/27/17.
 */
class ChechikTopCloseness {

    private final ImmutableGraph graph;
    private final ChechikFarnessEstimator estimator;
    private final Sorter sorter;
    private static final double epsilon = 0.1D;
    private boolean[] exact;
    private final int k;
    private double[] apxCloseness;
    private Integer[] nodes;
    private int[] distance;
    private final TreeSet<Integer> topC;
    private int numberOfBFS;
    private int numberOfBFSForApx;
    private int[] farness;
    private TreeSet<Integer> toReturnTopK;
    private final int availableProcessors;
    private final AtomicInteger next;

    ChechikTopCloseness(ImmutableGraph graph, ProgressLogger pl, int numberOfThreads, int k) throws IOException {
        this.graph = graph;
        this.estimator = new ChechikFarnessEstimator(graph, pl, numberOfThreads, epsilon);
        this.sorter = new Sorter(this.graph);
        this.k = k;
        this.distance = new int[graph.numNodes()];
        this.topC = new TreeSet<>((o1, o2) -> {
            int first = new Double(apxCloseness[o2]).compareTo(apxCloseness[o1]);
            return first == 0 ? o1.compareTo(o2) : first;
        });
        this.toReturnTopK = new TreeSet<>(topC.comparator());
        this.availableProcessors = getRuntime().availableProcessors();
        this.next = new AtomicInteger(0);
    }

    int getNumberOfBFS() {return numberOfBFS;}

    int getNumberOfBFSForApx() {return numberOfBFSForApx;}

    void compute() throws InterruptedException {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();

        long time = -bean.getCurrentThreadCpuTime();
        this.estimator.compute();
        time += bean.getCurrentThreadCpuTime();
        System.out.println("Approximation time = " + time);

        time = -bean.getCurrentThreadCpuTime();
        this.exact = this.estimator.getExact();
        double[] apxFarness = this.estimator.getApxFarness();
        this.apxCloseness = new double[graph.numNodes()];
        this.nodes = new Integer[graph.numNodes()];
        NodeIterator iterator = graph.nodeIterator();
        int i = 0;
        while(iterator.hasNext()) {
            nodes[i++] = iterator.nextInt();
        }

        sorter.farnessSort(apxFarness, nodes);
        for (i = 0; i < nodes.length; ++i) {
            apxCloseness[i] = 1.0D / apxFarness[i];
        }

        time += bean.getCurrentThreadCpuTime();
        System.out.println("Pre processing time = " + time);
        numberOfBFS = estimator.getNumberOfBFS();
        numberOfBFSForApx = numberOfBFS;
        int to = k;
        int from = 0;
        time = -bean.getCurrentThreadCpuTime();
        startComputingCloseness();
        /*while (toReturnTopK.size() < k) {
            computeRemainingCloseness(from, to);
            double limit = limit(to-1);
            updateTopK(limit);
            from = to;
            ++to;
        }*/
        time += bean.getCurrentThreadCpuTime();
        System.out.println("Remaining BFS time time = " + time);
    }

    private void startComputingCloseness() {
        ChechikTopCloseness.BFSThread[] thread = new ChechikTopCloseness.BFSThread[this.availableProcessors];
        for (int i = 0; i < thread.length; ++i) {
            thread[i] = new ChechikTopCloseness.BFSThread();
        }

        ExecutorService var11 = newFixedThreadPool(getRuntime().availableProcessors());
        ExecutorCompletionService<Void> executorCompletionService = new ExecutorCompletionService<>(var11);

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

    private final class BFSThread implements Callable<Void> {
        private final IntArrayFIFOQueue queue;
        private final int[] distance;

        private BFSThread() {
            this.distance = new int[ChechikTopCloseness.this.graph.numNodes()];
            this.queue = new IntArrayFIFOQueue();
        }

        public Void call() {
            int[] distance = this.distance;
            IntArrayFIFOQueue queue = this.queue;
            ImmutableGraph graph = ChechikTopCloseness.this.graph.copy();
            int l;
            while (true) {
                int i = ChechikTopCloseness.this.next.getAndIncrement();
                l = i;
                if (toReturnTopK.size() >= k || i >= graph.numNodes()) {
                    return null;
                }

                int v = nodes[i];
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
                        }
                    }
                }
                updateAfterBFS(v, total_distance, l);
            }
        }
    }

    private synchronized void updateAfterBFS(int v, int d, int l) {
        topC.add(v);
        exact[v] = true;
        apxCloseness[v] = 1.0D / (double)d;
        ++numberOfBFS;
        if (l >= k-1) {
            updateTopK(limit(l));
        }
    }

    private void computeRemainingCloseness(int from, int to) {
        this.farness = estimator.getFarness();

        for (int i = from; i < to; ++i) {
            int v = nodes[i];
            if (!exact[v]) { // BFS not computed
                apxCloseness[v] = 1.0D / (double)BFS(v);
                ++numberOfBFS;
                exact[v] = true;
            }
            else {
               // if (farness[v] == 0) {System.out.println("Error!"); System.exit(1);}
                apxCloseness[v] = farness[v] == 0 ? 0 : 1.0D / (double)farness[v];
            }
            topC.add(v);
        }
    }

    private synchronized void updateTopK(double limit) {
        while (apxCloseness[topC.first()] >= limit && !topC.isEmpty()) {
            toReturnTopK.add(topC.pollFirst());
        }
    }


    private double limit(int x) {
        return (1.0D + epsilon) * apxCloseness[nodes[x]];
    }

    private int BFS(int source) {
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        Arrays.fill(this.distance, -1);
        distance[source] = 0;
        int total_distance = 0;
        queue.enqueue(source);
        while (!queue.isEmpty()) {
            int sourceNode = queue.dequeueInt();
            int d = distance[sourceNode] + 1;
            LazyIntIterator successors = graph.successors(sourceNode);
            int s;

            while ((s = successors.nextInt()) != -1) {
                if (distance[s] == -1) {
                    queue.enqueue(s);
                    distance[s] = d;
                    total_distance += d;
                }
            }
        }
        exact[source] = true;
        return total_distance;
    }

    private void print(String s) {
        System.out.println(s);
    }


    private boolean isClosenessSorted(Integer[] nodes, double[] apxClos) {
        double prev = apxClos[nodes[0]];
        for (int i = 1; i < nodes.length; ++i) {
            if (apxClos[nodes[i]] > prev) {
                return false;
            }
            prev = apxClos[nodes[i]];
        }
        return true;
    }

    private boolean isFarnessSorted(Integer[] nodes, double[] apxFar) {
        double prev = apxFar[nodes[0]];
        for (int i = 1; i < nodes.length; ++i) {
            if (apxFar[nodes[i]] < prev) {
                return false;
            }
            prev = apxFar[nodes[i]];
        }
        return true;
    }

    private boolean isSorted(double[] arr) {
        double prev = arr[0];
        for (int i = 1; i < arr.length; ++i) {
            if (arr[i] > prev) {
                return false;
            }
            prev = arr[i];
        }
        return true;
    }
}
