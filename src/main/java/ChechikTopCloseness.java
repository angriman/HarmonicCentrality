import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.NodeIterator;
import org.apache.commons.lang.ArrayUtils;

import static java.lang.Runtime.getRuntime;

/**
 * Created by Eugenio on 4/27/17.
 */
public class ChechikTopCloseness {
    /**
     * The graph under examination.
     */
    private final ImmutableGraph graph;
    /**
     * Global progress logger.
     */
    private final ProgressLogger pl;
    private final ChechikFarnessEstimator estimator;
    private final Sorter sorter;

    public ChechikTopCloseness(ImmutableGraph graph, ProgressLogger pl, int numberOfThreads) {
        this.graph = graph;
        this.pl = pl;
        this.estimator = new ChechikFarnessEstimator(graph, pl, numberOfThreads, 0.05D);
        this.sorter = new Sorter(this.graph);
    }

    public void compute() throws InterruptedException {
        this.estimator.compute();
        double[] apxFarness = this.estimator.getApxFarness();
        Integer[] nodes = new Integer[graph.numNodes()];
        NodeIterator iterator = graph.nodeIterator();
        int i = 0;
        while(iterator.hasNext()) {
            nodes[i++] = iterator.nextInt();
        }
        sorter.farnessSort(apxFarness, nodes);
        System.out.println(isSorted(apxFarness) ? "Sorted" : "Not sorted");
       // System.out.println(ArrayUtils.toString(ArrayUtils.subarray(apxFarness, 0,120)));
        //System.out.println(ArrayUtils.toString(ArrayUtils.subarray(sorter.getTopKFromFarness(apxFarness, nodes), 10,20)));
    }

    private boolean isSorted(double[] nodes) {
        double prev = nodes[0];
        for (int i = 1; i < nodes.length; ++i) {
            if (nodes[i] < prev) {
                return false;
            }
            prev = nodes[i];
        }
        return true;
    }
}
