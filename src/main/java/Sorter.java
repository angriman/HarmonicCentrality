import it.unimi.dsi.webgraph.ImmutableGraph;
import org.apache.commons.lang.ArrayUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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
        Arrays.sort(arr2, new Comparator<Integer>() {
            @Override
            public int compare(Integer t1, Integer t2) {
                return new Integer(graph.outdegree(t2)).compareTo(graph.outdegree(t1));
            }
        });
        return ArrayUtils.toPrimitive(arr2);
    }

    public int[] randomSort(int[] arr) {
        List<Integer> shuffled = new ArrayList<>();
        for (int i = 0; i < arr.length; ++i) {
            shuffled.add(arr[i]);
        }
        Collections.shuffle(shuffled);
        Integer[] shuffledArray = shuffled.toArray(new Integer[0]);
        return ArrayUtils.toPrimitive(shuffledArray);
    }

    public int[] farnessSort(final int[] scheduledNodes, final int[] approxFarness, final int[] farness, AtomicInteger size) {
        Integer[] arr2 = ArrayUtils.toObject(scheduledNodes);
        Arrays.sort(arr2, size.get(), arr2.length, new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return new Integer(approxFarness[o1]).compareTo(approxFarness[o2]);
            }
        });

        Arrays.sort(arr2, 0, size.get(), new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                int first = new Integer(farness[o1]).compareTo(farness[o2]);
                if (farness[o1] == 0 || farness[o2] == 0) {
                    System.out.println("male   ");
                }
                return first == 0 ? o1.compareTo(o2) : first;
            }
        });

        return ArrayUtils.toPrimitive(arr2);
    }

    public int[] mergeAndSort(final int[] farness, final int[] approxFarness, int k) {
        int n = graph.numNodes();
        final double[] approxClos = new double[graph.numNodes()];
        Integer[] result = new Integer[graph.numNodes()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = i;
            approxClos[i] = (farness[i] == 0) ? approximateCloseness(n, k, approxFarness[i]) : closeness(n, farness[i]);
        }
        Arrays.sort(result, new Comparator<Integer>() {
            @Override
            public int compare(Integer t1, Integer t2) {
                int first = new Double(approxClos[t2]).compareTo(approxClos[t1]);
                return first == 0 ? new Integer(t2).compareTo(t1) : first;
            }
        });

        return ArrayUtils.toPrimitive(result);
    }

    private double approximateCloseness(int n, int k, int approximatedFarness) {
        double dn = (double)n;
        double dk = (double)k;
        double f = (double)approximatedFarness;
        return (1/f)*(dn/(dk*(dn-1)));
    }

    private double closeness(int n, int farness) {
        return (1/(double) farness)*((double)(n-1));
    }
}
