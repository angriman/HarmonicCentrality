import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.NodeIterator;
import it.unimi.dsi.webgraph.Transform;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Created by eugenio on 21/12/16.
 */
public class RandomSamplesExtractor {

    private final ImmutableGraph graph;
    private int[] nodeScore;

    public RandomSamplesExtractor(ImmutableGraph graph) {
        this.graph = graph;
        this.nodeScore = new int[graph.numNodes()];
    }

    private boolean isSorted(double[] arr) {
        double prev = arr[0];
        for (int i = 1; i< arr.length; ++i) {
            if (prev > arr[i]) return false;
            prev = arr[i];
        }
        return true;
    }

    /* Da parallelizzare se va bene */
    public int[] compute() {
        return uniformlyAtRandom();
    }

    private int[] degreeSort() {
        NodeIterator nodeIterator = graph.nodeIterator();
        Integer[] nodes = new Integer[graph.numNodes()];
        int k = 0;
        while (nodeIterator.hasNext()) {
            nodes[k++] = nodeIterator.nextInt();
        }

        Arrays.sort(nodes, new Comparator<Integer>() {
            @Override
            public int compare(Integer t1, Integer t2) {
                return (new Integer(graph.outdegree(t2))).compareTo(graph.outdegree(t1));
            }
        });

        return ArrayUtils.toPrimitive(nodes);
    }

    private int[] outDegreeArray(Integer[] nodes) {
        int[] result = new int[nodes.length];
        for (int n = 0; n < result.length; ++n) {
            result[n] = graph.outdegree(nodes[n]);
        }
        return result;
    }



    public int[] update(int samples, int[] current, double[] estimated, int iteration) {
        if (iteration >= 3) {
            Integer[] result = new Integer[current.length];
            Arrays.fill(result, -1);
            System.arraycopy(ArrayUtils.toObject(current), 0, result, 0, samples);
            //double[][] h = HarmonicCentrality.sort(estimated);
          // System.out.println(findMax(normalize(estimated,samples)));
            double[][] h = HarmonicCentrality.scoreSort(normalize(estimated,samples), graph.outdegree(current[0]), graph);
            int count = samples;
            for (int i = 0; i < current.length; ++i) {
                Integer currentNode = (int) h[i][1];

                if (!Arrays.asList(result).contains(currentNode)) {
                    result[count++] = currentNode;
                }

            }
            return ArrayUtils.toPrimitive(result);
        }
        return current;
    }

    private double[] normalize(double[] arr, int samples) {
        double[] result = new double[arr.length];
        double n = (double)arr.length;
        double mult = n / ((n-1) * (double)samples);
        for (int i = 0; i<arr.length; ++i) {
            result[i] = arr[i] * mult;
        }
        return result;
    }

    private double findMax(double[] arr) {
        double max = 0;
        for (double x : arr) {
            max = Math.max(x, max);
        }
        return max;
    }

    private int[] uniformlyAtRandom() {
        List<Integer> nodeList = new ArrayList<>();
        NodeIterator nodeIterator = graph.nodeIterator();

        while (nodeIterator.hasNext()) {
            nodeList.add(nodeIterator.nextInt());
        }
        Collections.shuffle(nodeList);
        Integer[] shuffledArray = nodeList.toArray(new Integer[0]);
        return ArrayUtils.toPrimitive(shuffledArray);
    }

    private int[] wrongNeighborhood() {
        NodeIterator nodeIterator = graph.nodeIterator();
        Integer[] nodes = new Integer[graph.numNodes()];
        int n;
        int k = 0;
        while (nodeIterator.hasNext()) {
            int curr = nodeIterator.nextInt();
            nodes[k++] = curr;
            nodeScore[curr] = score(curr);
        }
        Arrays.sort(nodes, new Comparator<Integer>() {
            public int compare(Integer s1, Integer s2) {
                return s1.compareTo(s2);
            }
        });

        double[] pdf = computePDist();
        double[] cdf = new double[pdf.length];
        cdf[cdf.length - 1] = 1;
        for (int i = 1; i < pdf.length - 1; ++i) {
            cdf[i] = cdf[i - 1] + pdf[i];
        }
        double min = min(pdf);

        int[] result = new int[graph.numNodes()];

        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i<graph.numNodes(); ++i) {
            for (int j = 0; j < Math.floor(pdf[i]/min); ++j) {
                pool.add(i);
            }
        }
        Collections.shuffle(pool);
        n = 0;
        for (int d : pool) {
            if (!ArrayUtils.contains(result, d)) {
                result[n++] = d;
            }
        }

        //return result;
        return ArrayUtils.toPrimitive(nodes);
    }

    private boolean checkPool(int[] pool) {
        int start = 0;
        System.out.println(pool[pool.length - 1]);
        int end = graph.numNodes()-1;
        for (int p: pool) {
            if (p < start) {
                System.out.println(p + " < " + start);
                return false;
            }
            if (p > start) {
                if (p - start == 1) {
                    start = p;
                }
                else {
                    System.out.println(p + " - " + start);
                    return false;
                }
            }
        }
        System.out.println(start);
        return start == end;
    }

    private int score(int node) {
        int[] successorArray = graph.successorArray(node);
        int score = 0;
        for (int succ : successorArray) {
            score += graph.successorArray(succ).length;
        }
        return score;
    }

    private double[] computePDist() {
        long sum = 0;
        double[] d = new double[nodeScore.length];
        for (int s : nodeScore) {
            sum += (long)s;
        }

        for (int i = 0; i < nodeScore.length; ++i) {
            d[i] = (double)nodeScore[i] / (double)sum;
        }
        return d;
    }

    private double min(double[] distr) {
        double min = 1;
        int i = 0;
        for (int j = 1; j<distr.length; ++j) {
            double curr = distr[j];
            if (curr < min) {
                min = curr;
            }
        }
        return min;
    }

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(RandomSamplesExtractor.class);

    public static void main(String args[]) throws IOException {
        String graphBaseName = "./Graphs/facebook/facebook";
        ProgressLogger progressLogger = new ProgressLogger(LOGGER, "nodes");
        progressLogger.displayFreeMemory = true;
        progressLogger.displayLocalSpeed = true;
        ImmutableGraph graph = ImmutableGraph.loadMapped(graphBaseName, progressLogger);
        graph = Transform.symmetrize(graph);
        graph = (new ArrayListMutableGraph(graph)).immutableView();

        RandomSamplesExtractor random = new RandomSamplesExtractor(graph);
        random.compute();
    }
}
