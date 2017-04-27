import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.NodeIterator;
import org.apache.commons.lang.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * Created by Eugenio on 4/26/17.
 */
public class ChechikEstimator {
    private final ImmutableGraph graph;
    private double[] lambda;
    private int[] distance;
    private double[] probabilities;
    private final int k;
    private ArrayList<Integer> s = new ArrayList<>();
    private ArrayList<Double> sp = new ArrayList<>();
    private boolean[] exact;

    public int[] getSamples() {return ArrayUtils.toPrimitive(s.toArray(new Integer[s.size()]));}
    public double[] getProbabilities() {return ArrayUtils.toPrimitive(sp.toArray(new Double[sp.size()]));}
    public boolean[] getExact() {return exact;}

    public ChechikEstimator(ImmutableGraph graph, double epsilon) {
        this.graph = graph;
        this.lambda = new double[graph.numNodes()];
        this.distance = new int[graph.numNodes()];
        this.probabilities = new double[graph.numNodes()];
        this.k = (int)Math.ceil(1.0D / Math.pow(epsilon, 2));
        this.exact = new boolean[graph.numNodes()];
    }

    public void computeCoefficients() {
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
        int INITIAL_SET_SIZE = 50;
        final int[] samples = new Random().ints(0, graph.numNodes()).distinct().limit(INITIAL_SET_SIZE).toArray();
        for (int u: samples) {
            exact[u] = true;
            int d = BFS(u);
            NodeIterator iterator = graph.nodeIterator();
            while (iterator.hasNext()) {
                int v = iterator.nextInt();
                this.lambda[v] = Math.max(this.lambda[v], (double)distance[v] / (double)d);
            }
        }
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
        return total_distance;
    }

    private void computeProbabilities() {
        NodeIterator iterator = graph.nodeIterator();
        while (iterator.hasNext()) {
            int v = iterator.nextInt();
            probabilities[v] = Math.min(1.0D, (double)k*lambda[v]);
        }
    }
}
