import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import netscape.javascript.JSObject;
import org.apache.commons.lang.ArrayUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONString;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Eugenio on 3/24/17.
 */

public class TopCloseness {
    /** The graph under examination. */
    private final ImmutableGraph graph;
    /** Size of the batch */
    private final int BATCH_SIZE;
    /** Global progress logger. */
    private final ProgressLogger pl;
    /** Number of threads. */
    private final int numberOfThreads;
    /** Next node to be visited. */
    private final AtomicInteger nextNode = new AtomicInteger();
    /** Whether to stop the algorithm */
    private boolean stop = false;
    /** Top Closeness Vector */
    private int[] topCloseness;
    /** Number of top Closeness computed until now */
    private AtomicInteger topClosenessComputed = new AtomicInteger();
    /** Farness of each node */
    private int[] farness;
    /** Farness computed from other nodes */
    private int[] approxFarness;
    /** Sorter */
    private final Sorter sorter;
    /** Number of iterations */
    private int iterations = 1;

    public TopCloseness(ImmutableGraph graph, ProgressLogger pl, int numberOfThreads) {
        this.graph = graph;
        this.sorter = new Sorter(this.graph);
        this.pl = pl;
        this.numberOfThreads = (numberOfThreads) == 0 ? Runtime.getRuntime().availableProcessors() : numberOfThreads;
        this.BATCH_SIZE = 10;
        this.topCloseness = initializeTopCentralities(new int[this.graph.numNodes()]);
        this.farness = new int[this.graph.numNodes()];
        this.approxFarness = new int[this.graph.numNodes()];
    }

    private int[] initializeTopCentralities(int[] topC) {
        for (int i = 0; i < topC.length; ++i) {
            topC[i] = i;
        }
        return sorter.randomSort(topC);//degreeSort(topC);
    }

    public void compute() throws  InterruptedException {
        TopCloseness.ProgressiveSamplingThread[] thread = new TopCloseness.ProgressiveSamplingThread[this.numberOfThreads];
        for (int i = 0; i < thread.length; ++i) {
            thread[i] = new TopCloseness.ProgressiveSamplingThread();
        }

        if (this.pl != null) {
            this.pl.start("Starting visits...");
            this.pl.expectedUpdates = (long) graph.numNodes();
            this.pl.itemsName = "nodes";
        }

        int remainingNodes = graph.numNodes();

        while (remainingNodes > 0) {

            int numberOfThreads = Math.min(BATCH_SIZE, remainingNodes);
            numberOfThreads = Math.min(numberOfThreads, Runtime.getRuntime().availableProcessors());
            ExecutorService var11 = Executors.newFixedThreadPool(numberOfThreads);
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

            printResult();
            ++iterations;
            if (iterations >= 10) {
                updateSchedule();
            }

            remainingNodes = Math.max(0, remainingNodes - BATCH_SIZE);

        }

        if (pl != null) {
            pl.done();
        }

    }

    private final class ProgressiveSamplingThread implements Callable<Void> {
        private final IntArrayFIFOQueue queue;
        private final int[] distance;

        private ProgressiveSamplingThread() {
            this.distance = new int[TopCloseness.this.graph.numNodes()];
            this.queue = new IntArrayFIFOQueue();
        }

        public Void call() {
            int[] distance = this.distance;
            IntArrayFIFOQueue queue = this.queue;
            ImmutableGraph graph = TopCloseness.this.graph.copy();

            while (true) {
                int topIndex = TopCloseness.this.nextNode.getAndIncrement();
                if (topIndex >= BATCH_SIZE*iterations) {
                    TopCloseness.this.nextNode.getAndDecrement();
                    return null;
                }
               // System.out.println(topIndex);

                if (TopCloseness.this.stop || topIndex >= graph.numNodes()) {
                    TopCloseness.this.nextNode.getAndDecrement();
                    return null;
                }
                queue.clear();
                queue.enqueue(topCloseness[topIndex]);
                Arrays.fill(distance, -1);
                distance[topCloseness[topIndex]] = 0;

                while (!queue.isEmpty()) { // Perform BFS and update farness
                    int sourceNode = queue.dequeueInt();
                    int d = distance[sourceNode] + 1;
                    LazyIntIterator successors = graph.successors(sourceNode);
                    int s;

                    while ((s = successors.nextInt()) != -1) {
                        if (distance[s] == -1) {
                            queue.enqueue(s);
                            distance[s] = d;
                            TopCloseness.this.farness[topCloseness[topIndex]] += d;
                            TopCloseness.this.approxFarness[s] += d;
                        }
                    }
                }

                if (TopCloseness.this.pl != null) {
                    synchronized (TopCloseness.this.pl) {
                        TopCloseness.this.pl.update();
                    }
                }
            }
        }
    }

    private synchronized void updateSchedule() {
     //   int newValue = Math.min(TopCloseness.this.topClosenessComputed.get() + BATCH_SIZE, graph.numNodes());
      //  TopCloseness.this.topClosenessComputed.getAndSet(newValue);
        if (TopCloseness.this.nextNode.get() < TopCloseness.this.topCloseness.length) {
            TopCloseness.this.topCloseness = sorter.farnessSort(TopCloseness.this.topCloseness,
                    TopCloseness.this.approxFarness, TopCloseness.this.farness, nextNode);
        }
    }

    private void printResult() {
        int[] curRes = new int[nextNode.get()];

        System.arraycopy(topCloseness, 0, curRes, 0, nextNode.get());
        JSONArray currentResult = new JSONArray(curRes);

        String path = "./results/";
        try (FileWriter file = new FileWriter(path + nextNode.get() +  ".json")) {
            currentResult.write(file);
        }
        catch (IOException e) {
            e.printStackTrace();
        }

    }
}
