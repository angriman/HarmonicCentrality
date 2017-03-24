import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ImmutableGraph;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Eugenio on 3/24/17.
 */
public class TopCloseness {
    /** The graph under examination. */
    private final ImmutableGraph graph;
    /** Global progress logger. */
    private final ProgressLogger pl;
    /** Number of threads. */
    private final int numberOfThreads;
    /** Next node to be visited. */
    private final AtomicInteger nextNode;
    /** Whether to stop the algorithm */
    private boolean stop = false;

    public TopCloseness(ImmutableGraph graph, ProgressLogger pl, int numberOfThreads) {
        this.graph = graph;
        this.pl = pl;
        this.numberOfThreads = numberOfThreads;
        this.nextNode = new AtomicInteger();
        System.out.println("Input graph: " + graph.numNodes() + " nodes and " + graph.numArcs() + " arcs.");
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

                int node = TopCloseness.this.nextNode.getAndIncrement();

                if (TopCloseness.this.stop) {
                    return null;
                }
            }

        }
    }
}
