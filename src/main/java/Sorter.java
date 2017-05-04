import it.unimi.dsi.webgraph.ImmutableGraph;
import org.apache.commons.lang.ArrayUtils;

import java.lang.reflect.Array;
import java.util.*;

/**
 * Created by Eugenio on 3/25/17.
 */

public class Sorter {
    private final ImmutableGraph graph;

    public Sorter(ImmutableGraph graph) {
        this.graph = graph;
    }

    public int[] degreeSort(int[] arr) {
        Integer[] arr2 = ArrayUtils.toObject(arr);
        Arrays.sort(arr2, (t1, t2) -> {
            int first = new Integer(graph.outdegree(t2)).compareTo(graph.outdegree(t1));
            return (first == 0) ? t1.compareTo(t2) : first;
        });

        return ArrayUtils.toPrimitive(arr2);
    }

    public int[] randomSort(int[] arr) {
        List<Integer> shuffled = new ArrayList<>();
        for (int anArr : arr) {shuffled.add(anArr);}
        Collections.shuffle(shuffled);
        Integer[] shuffledArray = shuffled.toArray(new Integer[0]);
        return ArrayUtils.toPrimitive(shuffledArray);
    }

    public int[] farnessSort(final int[] scheduledNodes, final int[] approxFarness, final int[] farness, final int size) {
      //  System.out.println("Size = " + size);
        Integer[] arr2 = ArrayUtils.toObject(scheduledNodes);
        Arrays.sort(arr2, size, arr2.length, (o1, o2) -> {
            int first = new Integer(approxFarness[o1]).compareTo(approxFarness[o2]);
            return (first == 0) ? o1.compareTo(o2) : first;
        });

        Arrays.sort(arr2, 0, size, (o1, o2) -> {
            int first = new Integer(farness[o1]).compareTo(farness[o2]);
            if (farness[o1] == 0 || farness[o2] == 0) {
                System.out.println("male   ");
            }
            return first == 0 ? o1.compareTo(o2) : first;
        });

        return ArrayUtils.toPrimitive(arr2);
    }

    public int[] mergeAndSort(final int[] farness, final int[] approxFarness, int k) {
        int n = graph.numNodes();
        double[] approxClos = new double[graph.numNodes()];
        Integer[] result = new Integer[graph.numNodes()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = i;
            approxClos[i] = (farness[i] == 0) ? approximateCloseness(n, k, approxFarness[i]) : closeness(n, farness[i]);
        }

        final double[] finApxClos = approxClos.clone();
        Arrays.sort(result, (t1, t2) -> {
            int first = new Double(finApxClos[t2]).compareTo(finApxClos[t1]);
            return first == 0 ? t2.compareTo(t1) : first;
        });
        return ArrayUtils.toPrimitive(result);
    }

    public void farnessSort(double[] farness, Integer[] nodes) {
        Arrays.sort(nodes, (o1, o2) -> new Double(farness[o1]).compareTo(farness[o2]));
    }

    public void closenessSort(double[] closeness, Integer[] nodes) {
        Arrays.sort(nodes, (o1, o2) -> new Double(closeness[o2]).compareTo(closeness[o1]));
    }

    private double approximateCloseness(int n, int k, int approximatedFarness) {
        double dn = (double)n;
        double dk = (double)k;
        double f = (double)approximatedFarness;
        return dk * (dn - 1) / (dn*f);
    }

    private double closeness(int n, int farness) {
        return ((double)(n-1) / ((double) farness));
    }
}