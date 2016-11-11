/**
 * Created by Eugenio on 11/4/16.
 */

import com.martiansoftware.jsap.JSAPException;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ImmutableGraph;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


public class HarmonicCentrality {
    private static final Logger LOGGER = LoggerFactory.getLogger(HarmonicCentrality.class);
    private final ImmutableGraph graph;
    private final int numberOfThreads; // Available CPUs
    public final double[] harmonic;
    private final ProgressLogger pl;
    protected final AtomicInteger nextNode;
    protected volatile boolean stop;
    private static final double precision = 0.2;
    private final int[] randomSamples;

    public HarmonicCentrality(ImmutableGraph graph, int requestedThreads, ProgressLogger pl) {
        this.graph = graph;
        this.pl = pl;
        this.harmonic = new double[graph.numNodes()];
        this.numberOfThreads = requestedThreads != 0?requestedThreads:Runtime.getRuntime().availableProcessors();
        this.nextNode = new AtomicInteger();
        this.randomSamples = pickRandomSamples(numberOfSamples());
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

    // Calculates the number of samples according to the required precision> log(n) / epsilon^2
    private int numberOfSamples() {
        if (precision <= 0) {
            return graph.numNodes();
        }

        if (precision >= 1) {
            return 1;
        }

        return (int)Math.ceil(Math.log(graph.numNodes()) / Math.pow(precision, 2));
    }

    private int[] pickRandomSamples(int k) {
        int[] samples = new int[k];
        if (k == graph.numNodes()) {
            for (int i = 0; i < k; ++i) {
                samples[i] = i;
            }
        }
        else {

            int count = 0;
            Random rand = new Random();
            while (count < k) {
                int randomNum = rand.nextInt(graph.numNodes());
                if (!ArrayUtils.contains(samples, randomNum)) {
                    samples[count] = randomNum;
                    ++count;
                }
            }
        }

        return samples;
    }

    public void compute() throws InterruptedException {
        HarmonicCentrality.IterationThread[] thread = new HarmonicCentrality.IterationThread[this.numberOfThreads];

        for (int executorService = 0; executorService < thread.length; ++executorService) {
            thread[executorService] = new HarmonicCentrality.IterationThread();
        }

        if (this.pl != null) {
            this.pl.start("Starting visits...");
            this.pl.expectedUpdates = (long)this.graph.numNodes();
            this.pl.itemsName = "nodes";
        }

        ExecutorService var11 = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        ExecutorCompletionService executorCompletionService = new ExecutorCompletionService(var11);
        int e = thread.length;

        while (e-- != 0) {
            executorCompletionService.submit(thread[e]);
        }

        try {
            e = thread.length;

            while (e-- != 0) {
                executorCompletionService.take().get();
            }
        } catch (ExecutionException var9) {
            this.stop = true;
            Throwable cause = var9.getCause();
            throw cause instanceof RuntimeException?(RuntimeException)cause:new RuntimeException(cause.getMessage(), cause);
        } finally {
            var11.shutdown();
        }

        if (this.pl != null) {
            this.pl.done();
        }
    }

    private final class IterationThread implements Callable<Void> {
        private final IntArrayList queue;

        private IterationThread() {
            this.queue = new IntArrayList(HarmonicCentrality.this.graph.numNodes());
        }

        public Void call() {
            return null;
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException, JSAPException {
        CharSequence basename = "/home/eugenio/Downloads/Graphs/cnr2000/cnr-2000-hc";
        ProgressLogger progressLogger = new ProgressLogger(LOGGER, "nodes");
        progressLogger.displayFreeMemory = true;
        progressLogger.displayLocalSpeed = true;

        try {

            ImmutableGraph graph = ImmutableGraph.loadOffline(basename);

            HarmonicCentrality harmonicCentrality = new HarmonicCentrality(graph, 0, progressLogger);
            harmonicCentrality.compute();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
