/**
 * Created by Eugenio on 11/4/16.
 */

import com.martiansoftware.jsap.JSAPException;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.algo.BetweennessCentrality;

import java.io.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


public class HarmonicCentrality {
    private final ImmutableGraph graph;
    private final int numberOfThreads;
    public final double[] harmonic;
    private final ProgressLogger pl;
    protected final AtomicInteger nextNode;
    protected volatile boolean stop;

    public HarmonicCentrality(ImmutableGraph graph, int requestedThreads, ProgressLogger pl) {
        this.graph = graph;
        this.pl = pl;
        this.harmonic = new double[graph.numNodes()];
        this.numberOfThreads = requestedThreads != 0?requestedThreads:Runtime.getRuntime().availableProcessors();
        this.nextNode = new AtomicInteger();
    }

    public HarmonicCentrality(ImmutableGraph graph, ProgressLogger pl) {
        this(graph, 0, pl);
    }

    public HarmonicCentrality(ImmutableGraph graph, int requestedThreads) {
        this(graph, 1, (ProgressLogger)null);
    }

    public HarmonicCentrality(ImmutableGraph graph) {
        this(graph, 0);
    }

    public void compute() throws InterruptedException {

    }


    public static void main(String[] args) throws IOException, InterruptedException, JSAPException {
        CharSequence basename = "/home/eugenio/Downloads/Graphs/cnr2000/cnr-2000-hc";
        if (args.length > 0) {
            try {

                ImmutableGraph graph = ImmutableGraph.loadOffline(basename);


            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        else {
            System.out.println("Error: no input graph provided");
        }
    }
}
