import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.NodeIterator;
import org.apache.commons.lang.ArrayUtils;

/**
 * Created by Eugenio on 4/27/17.
 */
public class ChechikTopCloseness {

    private final ImmutableGraph graph;
    private final ChechikFarnessEstimator estimator;
    private final Sorter sorter;
    private static final double epsilon = 0.05D;
    private String graphName;

    public ChechikTopCloseness(ImmutableGraph graph, ProgressLogger pl, int numberOfThreads) {
        this.graph = graph;
        this.estimator = new ChechikFarnessEstimator(graph, pl, numberOfThreads, epsilon);
        this.sorter = new Sorter(this.graph);
    }

    public void setGraphName(String name) {
        graphName = name;
    }

    public void compute() throws InterruptedException {
        this.estimator.compute();
        double[] apxFarness = this.estimator.getApxFarness();
        double[] apxCloseness = new double[graph.numNodes()];
        Integer[] nodes = new Integer[graph.numNodes()];
        NodeIterator iterator = graph.nodeIterator();
        int i = 0;
        while(iterator.hasNext()) {
            nodes[i++] = iterator.nextInt();
        }

        sorter.farnessSort(apxFarness, nodes);
        for (i = 0; i < nodes.length; ++i) {
            apxCloseness[i] = (double)(graph.numNodes() - 1) / apxFarness[i];
        }

        GTLoader loader = new GTLoader(graphName);
        loader.load();
        print(ArrayUtils.toString(ArrayUtils.subarray(loader.getCloseness(), 0, 10)));
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
}
