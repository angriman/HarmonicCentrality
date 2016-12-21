import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.NodeIterator;
import it.unimi.dsi.webgraph.Transform;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        NodeIterator nodeIterator = graph.nodeIterator();
        //List<Double> randomExtract = new ArrayList<>();
        int n = 0;
        while (nodeIterator.hasNext()) {
            int curr = nodeIterator.nextInt();
            //randomExtract.add((double)n++ / (double)graph.numNodes());
            nodeScore[curr] = score(curr);
        }

       // Collections.shuffle(randomExtract);
//
        double[] pdf = computePDist();
        double[] cdf = new double[pdf.length];
        n = 0;
        cdf[cdf.length - 1] = 1;
        for (int i = 1; i < pdf.length - 1; ++i) {
            cdf[i] = cdf[i - 1] + pdf[i];
        }

        //System.out.println(isSorted(cdf) ? "Sorted" : "Not sorted");
        double min = min(pdf);
//        List<Integer> randomExtract = new ArrayList<>();
//        for (int i = 0; i< (int) Math.ceil((double)1 / min); ++i) {
//             randomExtract.add(i);
//        }
        int[] result = new int[graph.numNodes()];

//       Collections.shuffle(randomExtract);
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

//        System.out.println(checkPool(pool) ? "Success" : "Fail");
//        int count = 0;
//        for (Integer next : randomExtract) {
//            int pos = (int) Math.floor(next * min);
//            int entry = pool[pos];
//            if (!ArrayUtils.contains(result, entry)) {
//                result[count] = entry;
//                ++count;
//            }
//        }
        return result;
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
