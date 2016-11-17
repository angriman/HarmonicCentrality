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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeometricCentralities {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeometricCentralities.class);
    public static final double DEFAULT_ALPHA = 0.5D;
    private final ImmutableGraph graph;
    public final double[] harmonic;
    public final double[] closeness;
    public final double[] lin;
    public final double[] exponential;
    public double alpha;
    public final long[] reachable;
    private final ProgressLogger pl;
    private final int numberOfThreads;
    protected final AtomicInteger nextNode;
    protected volatile boolean stop;
    private final AtomicInteger visitedNodes;
    private final AtomicInteger visitedArcs;

    public GeometricCentralities(ImmutableGraph graph, int requestedThreads, ProgressLogger pl) {
        this.pl = pl;
        this.graph = graph;
        this.harmonic = new double[graph.numNodes()];
        this.closeness = new double[graph.numNodes()];
        this.reachable = new long[graph.numNodes()];
        this.exponential = new double[graph.numNodes()];
        this.alpha = 0.5D;
        this.lin = new double[graph.numNodes()];
        this.nextNode = new AtomicInteger();
        this.numberOfThreads = requestedThreads != 0?requestedThreads:Runtime.getRuntime().availableProcessors();
        this.visitedNodes = new AtomicInteger();
        this.visitedArcs = new AtomicInteger();
    }

    public int visitedNodes() {
        return visitedNodes.get();
    }

    public int visitedArcs() {
        return visitedArcs.get();
    }

    public GeometricCentralities(ImmutableGraph graph, ProgressLogger pl) {
        this(graph, 0, pl);
    }

    public GeometricCentralities(ImmutableGraph graph, int requestedThreads) {
        this(graph, 1, (ProgressLogger)null);
    }

    public GeometricCentralities(ImmutableGraph graph) {
        this(graph, 0);
    }

    public void compute() throws InterruptedException {
        GeometricCentralities.IterationThread[] thread = new GeometricCentralities.IterationThread[this.numberOfThreads];

        for(int executorService = 0; executorService < thread.length; ++executorService) {
            thread[executorService] = new GeometricCentralities.IterationThread();
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
        SimpleJSAP jsap = new SimpleJSAP(GeometricCentralities.class.getName(), "Computes positive centralities of a graph using multiple parallel breadth-first visits.\n\nPlease note that to compute negative centralities on directed graphs (which is usually what you want) you have to compute positive centralities on the transpose.", new Parameter[]{new Switch("expand", 'e', "expand", "Expand the graph to increase speed (no compression)."), new Switch("mapped", 'm', "mapped", "Use loadMapped() to load the graph."), new FlaggedOption("threads", JSAP.INTSIZE_PARSER, "0", false, 'T', "threads", "The number of threads to be used. If 0, the number will be estimated automatically."), new UnflaggedOption("graphBasename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, true, false, "The basename of the graph."), new UnflaggedOption("closenessFilename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, true, false, "The filename where closeness centrality scores (doubles in binary form) will be stored."), new UnflaggedOption("linFilename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, true, false, "The filename where Lin\'s centrality scores (doubles in binary form) will be stored."), new UnflaggedOption("harmonicFilename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, true, false, "The filename where harmonic centrality scores (doubles in binary form) will be stored."), new UnflaggedOption("exponentialFilename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, true, false, "The filename where exponential centrality scores (doubles in binary form) will be stored."), new UnflaggedOption("reachableFilename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, true, false, "The filename where the number of reachable nodes (longs in binary form) will be stored.")});
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

        GeometricCentralities centralities = new GeometricCentralities(graph, threads, progressLogger);
        centralities.compute();
        BinIO.storeDoubles(centralities.closeness, jsapResult.getString("closenessFilename"));
        BinIO.storeDoubles(centralities.lin, jsapResult.getString("linFilename"));
        BinIO.storeDoubles(centralities.harmonic, jsapResult.getString("harmonicFilename"));
        BinIO.storeDoubles(centralities.exponential, jsapResult.getString("exponentialFilename"));
        BinIO.storeLongs(centralities.reachable, jsapResult.getString("reachableFilename"));
    }

    private final class IterationThread implements Callable<Void> {
        private final IntArrayFIFOQueue queue;
        private final int[] distance;

        private IterationThread() {
            this.distance = new int[GeometricCentralities.this.graph.numNodes()];
            this.queue = new IntArrayFIFOQueue();
        }

        public Void call() {
            int[] distance = this.distance;
            IntArrayFIFOQueue queue = this.queue;
            ImmutableGraph graph = GeometricCentralities.this.graph.copy();
            double base = GeometricCentralities.this.alpha;

            while(true) {
                int curr = GeometricCentralities.this.nextNode.getAndIncrement();
                if(GeometricCentralities.this.stop || curr >= graph.numNodes()) {
                    return null;
                }

                queue.clear();
                queue.enqueue(curr);
                Arrays.fill(distance, -1);
                distance[curr] = 0;
                int reachable = 0;

                while(!queue.isEmpty()) {
                    int node = queue.dequeueInt();
                    ++reachable;
                    int d = distance[node] + 1;
                    double hd = 1.0D / (double)d;
                    double ed = Math.pow(base, (double)d);
                    LazyIntIterator successors = graph.successors(node);

                    int s;
                    while((s = successors.nextInt()) != -1) {
                        visitedArcs.getAndIncrement();
                        if(distance[s] == -1) {
                            visitedNodes.getAndIncrement();
                            queue.enqueue(s);
                            distance[s] = d;
                            GeometricCentralities.this.closeness[curr] += (double)d;
                            GeometricCentralities.this.harmonic[curr] += hd;
                            GeometricCentralities.this.exponential[curr] += ed;
                        }
                    }
                }

                if(GeometricCentralities.this.pl != null) {
                    synchronized(GeometricCentralities.this.pl) {
                        GeometricCentralities.this.pl.update();
                    }
                }

                if(GeometricCentralities.this.closeness[curr] == 0.0D) {
                    GeometricCentralities.this.lin[curr] = 1.0D;
                } else {
                    GeometricCentralities.this.closeness[curr] = 1.0D / GeometricCentralities.this.closeness[curr];
                    GeometricCentralities.this.lin[curr] = (double)reachable * (double)reachable * GeometricCentralities.this.closeness[curr];
                }

                GeometricCentralities.this.reachable[curr] = (long)reachable;
            }
        }
    }
}
