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
    private Integer[] topk;
    private TreeSet<Integer> topC;

    public ChechikTopCloseness(ImmutableGraph graph, ProgressLogger pl, int numberOfThreads, int k) {
        this.graph = graph;
        this.estimator = new ChechikFarnessEstimator(graph, pl, numberOfThreads, epsilon);
        this.sorter = new Sorter(this.graph);
        this.k = k;
        this.distance = new int[graph.numNodes()];
    }

    public Integer[] getTopk() {
        /*for (int i = 0; i < k; ++i) {
            this.topk[i] = topC.first();
            topC.remove(topC.first());
        }
        int i = 0;
        Integer cur;
        while ((cur = topC.first()) == apxCloseness[topk[k-1]]) {
            topk[k+i] = cur;
            ++i;
            topC.remove(cur);
        }
        return (Integer[]) ArrayUtils.subarray(topk, 0, k+i);*/
        return this.topk;
    }

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
            apxCloseness[i] = (double)(graph.numNodes() - 1) / apxFarness[i];
        }

        int to = getKth();
        computeRemainingCloseness(to);
        computeTopKSet(to);
       // completeTopCloseness();
    }

    private void computeTopKSet(int to) {
        double limit = (1.0D + epsilon)*apxCloseness[nodes[to]];
        int end = to;
        while (apxCloseness[end] >= limit && end < graph.numNodes() - 1) {
            System.out.println(limit + " <= " + apxCloseness[end]);
            ++end;
        }

        this.topk = new Integer[end];
        System.arraycopy(nodes, 0, topk, 0, topk.length);
    }

    private void completeTopCloseness() {
        topC = new TreeSet<>((o1, o2) -> new Double(apxCloseness[o2]).compareTo(apxCloseness[o1]));
        for (Integer v : this.topk) {
            if (!exact[v]) {
                apxCloseness[v] = (double)(graph.numNodes()-1) / (double)BFS(v);
            }
            topC.add(v);
        }
    }

    private void computeRemainingCloseness(int to) {
        for (int i = 0; i < to; ++i) {
            int v = nodes[i];
            if (!exact[v]) { // BFS not computed
                apxCloseness[v] = (double)(graph.numNodes()-1) / (double)BFS(v);
            }
        }
    }

    private int getKth() {
        double kth = apxCloseness[nodes[k-1]];
        int to = k;
        while (to < graph.numNodes() && kth <= apxCloseness[nodes[k]]) {
            ++to;
        }
        return to;
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
       // exact[source] = true;
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
