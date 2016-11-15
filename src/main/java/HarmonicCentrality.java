//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HarmonicCentrality {
    private static final Logger LOGGER = LoggerFactory.getLogger(HarmonicCentrality.class);
    public static final double DEFAULT_ALPHA = 0.5D;
    private final ImmutableGraph graph;
    public final double[] harmonic;
    private final ProgressLogger pl;
    private final int numberOfThreads;
    protected final AtomicInteger nextNode;
    protected volatile boolean stop;
    private static final double precision = 0.2;
    private final int[] randomSamples;
    private final double normalization;

    public HarmonicCentrality(ImmutableGraph graph, int requestedThreads, ProgressLogger pl) {
        this.pl = pl;
        this.graph = graph;
        this.harmonic = new double[graph.numNodes()];
        this.nextNode = new AtomicInteger();
        this.numberOfThreads = requestedThreads != 0?requestedThreads:Runtime.getRuntime().availableProcessors();
        this.randomSamples = pickRandomSamples(numberOfSamples());
        this.normalization = (double)graph.numNodes() / ((double)(randomSamples.length * (graph.numNodes() - 1)));
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

        for(int executorService = 0; executorService < thread.length; ++executorService) {
            thread[executorService] = new HarmonicCentrality.IterationThread();
        }

        if(this.pl != null) {
            this.pl.start("Starting visits...");
            this.pl.expectedUpdates = (long)this.graph.numNodes();
            this.pl.itemsName = "nodes";
        }

        ExecutorService var11 = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        ExecutorCompletionService executorCompletionService = new ExecutorCompletionService(var11);
        int e = thread.length;

        while(e-- != 0) {
            executorCompletionService.submit(thread[e]);
        }

        try {
            e = thread.length;

            while(e-- != 0) {
                executorCompletionService.take().get();
            }
        } catch (ExecutionException var9) {
            this.stop = true;
            Throwable cause = var9.getCause();
            throw cause instanceof RuntimeException?(RuntimeException)cause:new RuntimeException(cause.getMessage(), cause);
        } finally {
            var11.shutdown();
        }

        if(this.pl != null) {
            this.pl.done();
        }
    }

    public static void main(String[] arg) throws IOException, JSAPException, InterruptedException {
        SimpleJSAP jsap = new SimpleJSAP(HarmonicCentrality.class.getName(), "Computes positive centralities of a graph using multiple parallel breadth-first visits.\n\nPlease note that to compute negative centralities on directed graphs (which is usually what you want) you have to compute positive centralities on the transpose.",
                new Parameter[]{
                        new Switch("expand", 'e', "expand", "Expand the graph to increase speed (no compression)."),
                        new Switch("mapped", 'm', "mapped", "Use loadMapped() to load the graph."),
                        new FlaggedOption("threads", JSAP.INTSIZE_PARSER, "0", false, 'T', "threads", "The number of threads to be used. If 0, the number will be estimated automatically."),
                        new UnflaggedOption("graphBasename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, true, false, "The basename of the graph."),
                        new UnflaggedOption("harmonicFilename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, true, false, "The filename where harmonic centrality scores (doubles in binary form) will be stored.")
        });
        JSAPResult jsapResult = jsap.parse(arg);
        if(jsap.messagePrinted()) {
            System.exit(1);
        }

        boolean mapped = jsapResult.getBoolean("mapped", false);
        String graphBasename = jsapResult.getString("graphBasename");
        int threads = jsapResult.getInt("threads");
        ProgressLogger progressLogger = new ProgressLogger(LOGGER, "nodes");
        progressLogger.displayFreeMemory = true;
        progressLogger.displayLocalSpeed = true;
        ImmutableGraph graph = mapped?ImmutableGraph.loadMapped(graphBasename, progressLogger):ImmutableGraph.load(graphBasename, progressLogger);
        if(jsapResult.userSpecified("expand")) {
            graph = (new ArrayListMutableGraph(graph)).immutableView();
        }

        HarmonicCentrality centralities = new HarmonicCentrality(graph, threads, progressLogger);
        centralities.compute();
        for (double h : centralities.harmonic) {
            System.out.println(h);
        }
        BinIO.storeDoubles(centralities.harmonic, jsapResult.getString("harmonicFilename"));
    }

    private final class IterationThread implements Callable<Void> {
        private final IntArrayFIFOQueue queue;
        private final int[] distance;

        private IterationThread() {
            this.distance = new int[HarmonicCentrality.this.graph.numNodes()];
            this.queue = new IntArrayFIFOQueue();
        }

        public Void call() {
            int[] distance = this.distance;
            IntArrayFIFOQueue queue = this.queue;
            ImmutableGraph graph = HarmonicCentrality.this.graph.copy();


            while(true) {
                int curr = HarmonicCentrality.this.nextNode.getAndIncrement();
                if(HarmonicCentrality.this.stop || curr >= randomSamples.length) {
                    return null;
                }

                queue.clear();
                queue.enqueue(randomSamples[curr]);
                Arrays.fill(distance, -1);
                distance[randomSamples[curr]] = 0;

                while(!queue.isEmpty()) {
                    int node = queue.dequeueInt();
                    int d = distance[node] + 1;
                    LazyIntIterator successors = graph.successors(node);

                    int s;
                    while((s = successors.nextInt()) != -1) {
                        if(distance[s] == -1) {
                            queue.enqueue(s);
                            distance[s] = d;
                        }
                    }
                }

                int i = 0;
                for (int d : distance) {
                    HarmonicCentrality.this.harmonic[i++] += HarmonicCentrality.this.normalization * ((d > 0) ? 1.0D / (double)d : 0);
                }

                if(HarmonicCentrality.this.pl != null) {
                    synchronized(HarmonicCentrality.this.pl) {
                        HarmonicCentrality.this.pl.update();
                    }
                }
            }
        }
    }
}
