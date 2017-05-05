import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.NodeIterator;

import java.util.Arrays;
import java.util.TreeSet;

/**
 * Created by Eugenio on 4/27/17.
 */
public class ChechikTopCloseness {

    private final ImmutableGraph graph;
    private final ChechikFarnessEstimator estimator;
    private final Sorter sorter;
    private static final double epsilon = 0.05D;
    private boolean[] exact;
    private final int k;
    private double[] apxCloseness;
    private Integer[] nodes;
    private int[] distance;
    private TreeSet<Integer> topC;
    private int numberOfBFS;
    private int[] farness;
    private TreeSet<Integer> toReturnTopK;

    public ChechikTopCloseness(ImmutableGraph graph, ProgressLogger pl, int numberOfThreads, int k, String graphName) {
        this.graph = graph;
        this.estimator = new ChechikFarnessEstimator(graph, pl, numberOfThreads, epsilon, graphName);
        this.sorter = new Sorter(this.graph);
        this.k = k;
        this.distance = new int[graph.numNodes()];
        topC = new TreeSet<>((o1, o2) -> {
            int first = new Double(apxCloseness[o2]).compareTo(apxCloseness[o1]);
            return first == 0 ? o1.compareTo(o2) : first;
        });
        toReturnTopK = new TreeSet<>(topC.comparator());
    }

    public int[] getFarness() {return this.farness;}

    public Integer[] getTopk() {
       // System.out.println("K = " + k + " To = " + topC.size());
     /*   TreeSet<Integer> toReturn = new TreeSet<>(topC.comparator());
        int i = 0;
        while (!topC.isEmpty()) {
            Integer v = topC.pollFirst();
            if (i >= k) {
              //  if (apxCloseness[v] < apxCloseness[toReturn.last()]) {
                    break;
             //   }
            }
            else {
                toReturn.add(v);
                ++i;
            }
            if (!exact[toReturn.last()]) System.out.println("Una centrality nel top k set non è esatta!");

        }
        return toReturn.toArray(new Integer[toReturn.size()]);*/
      // return topC.toArray(new Integer[topC.size()]);
        return toReturnTopK.toArray(new Integer[toReturnTopK.size()]);
    }

    public double[] getApxCloseness() {return apxCloseness;}

    public int getNumberOfBFS() {return numberOfBFS;}

    public void compute() throws InterruptedException {
        this.estimator.compute();
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

        numberOfBFS = estimator.getNumberOfBFS();
        int to = k;
        int from = 0;
        while (toReturnTopK.size() < k) {
            computeRemainingCloseness(from, to);
            double limit = limit(to-1);
            updateTopK(limit);
            from = to;
            ++to;
        }
    }

    private void computeRemainingCloseness(int from, int to) {
        this.farness = estimator.getFarness();
        for (int i = from; i < to; ++i) {
            int v = nodes[i];
            if (!exact[v]) { // BFS not computed
                apxCloseness[v] = 1.0D / (double)BFS(v);
                ++numberOfBFS;
            }
            else {
                if (farness[v] == 0 || !exact[v]) {System.out.println("Error!"); System.exit(1);}
                apxCloseness[v] = 1.0D / (double)farness[v];
            }
            topC.add(v);
        }
    }

    private void updateTopK(double limit) {
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
