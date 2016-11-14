/**
 * Created by Eugenio on 11/4/16.
 */

import com.martiansoftware.jsap.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.examples.BreadthFirst;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


public class HarmonicCentrality {
    private static final Logger LOGGER = LoggerFactory.getLogger(HarmonicCentrality.class);
    private final ImmutableGraph graph;
    private final ProgressLogger pl;
    private final int numberOfThreads;
    protected final AtomicInteger nextNode;
    protected volatile boolean stop;
    public final double[] harmonic;
    private static final double precision = 0.2;
    private final int[] randomSamples;

    public HarmonicCentrality(ImmutableGraph graph, int requestedThreads, ProgressLogger pl) {
        this.pl = pl;
        this.graph = graph;
        this.harmonic = new double[graph.numNodes()];
        this.nextNode = new AtomicInteger();
        this.numberOfThreads = requestedThreads != 0?requestedThreads:Runtime.getRuntime().availableProcessors();
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

    public static void main(String[] arg) throws IOException, InterruptedException, JSAPException {
//        SimpleJSAP jsap = new SimpleJSAP(HarmonicCentrality.class.getName(), "Computes the betweenness centrality a graph using an implementation of Brandes\'s algorithm based on multiple parallel breadth-first visits.", new Parameter[]{new Switch("expand", 'e', "expand", "Expand the graph to increase speed (no compression)."), new Switch("mapped", 'm', "mapped", "Use loadMapped() to load the graph."), new FlaggedOption("threads", JSAP.INTSIZE_PARSER, "0", false, 'T', "threads", "The number of threads to be used. If 0, the number will be estimated automatically."), new UnflaggedOption("graphBasename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, true, false, "The basename of the graph."), new UnflaggedOption("rankFilename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, true, false, "The filename where the resulting rank (doubles in binary form) are stored.")});
//        JSAPResult jsapResult = jsap.parse(arg);
//        if(jsap.messagePrinted()) {
//            System.exit(1);
//        }
//
//        boolean mapped = jsapResult.getBoolean("mapped", false);
        String graphBasename = "/home/eugenio/Downloads/Graphs/cnr2000/cnr-2000-hc";
        ProgressLogger progressLogger = new ProgressLogger(LOGGER, "nodes");
        progressLogger.displayFreeMemory = true;
        progressLogger.displayLocalSpeed = true;
        ImmutableGraph graph = ImmutableGraph.load(graphBasename, progressLogger); // mapped?ImmutableGraph.loadMapped(graphBasename, progressLogger):ImmutableGraph.load(graphBasename, progressLogger);
//        if(jsapResult.userSpecified("expand")) {
//            graph = (new ArrayListMutableGraph(graph)).immutableView();
//        }

        HarmonicCentrality harmonicCentrality = new HarmonicCentrality(graph, 4, progressLogger);
        harmonicCentrality.compute();
        //BinIO.storeDoubles(harmonicCentrality.betweenness, rankFilename);
    }

    private final class IterationThread implements Callable<Void> {
        private final IntArrayList queue;
        private final IntArrayList cutPoints;
        private final int[] distance;
        private final long[] sigma;

        private IterationThread() {
            this.distance = new int[HarmonicCentrality.this.graph.numNodes()];
            this.sigma = new long[HarmonicCentrality.this.graph.numNodes()];
            this.queue = new IntArrayList(HarmonicCentrality.this.randomSamples);
            this.cutPoints = new IntArrayList();
        }

        private boolean checkOverflow(long[] sigma, int node, long currSigma, int s) {
            if(sigma[s] > 9223372036854775807L - currSigma) {
                throw new HarmonicCentrality.PathCountOverflowException(sigma[s] + " > " + (9223372036854775807L - currSigma) + " (" + node + " -> " + s + ")");
            } else {
                return true;
            }
        }

        public Void call() {
            int[] distance = this.distance;
            long[] sigma = this.sigma;
            IntArrayList queue = this.queue;
            ImmutableGraph graph = HarmonicCentrality.this.graph.copy();

            while(true) {
                int curr = HarmonicCentrality.this.nextNode.getAndIncrement();
                if (HarmonicCentrality.this.stop || curr >= randomSamples.length) {
                    return null;
                }

                queue.clear();
                queue.add(randomSamples[curr]);
                LazyIntIterator succ;
                this.cutPoints.clear();
                this.cutPoints.add(0);
                Arrays.fill(distance, -1);
                Arrays.fill(sigma, 0L);
                distance[randomSamples[curr]] = 0;
                sigma[randomSamples[curr]] = 1L;
                boolean overflow = false;
                int d;
                int start;
                int end;
                int pos;
                int pos1;
                int s;

                for(d = 0; queue.size() != this.cutPoints.getInt(this.cutPoints.size() - 1); ++d) {
                    this.cutPoints.add(queue.size());
                    start = this.cutPoints.getInt(d);
                    end = this.cutPoints.getInt(d + 1);

                    for(pos = start; pos < end; ++pos) {
                        pos1 = queue.getInt(pos);
                        long node = sigma[pos1];
                        try {
                            succ = graph.successors(curr);
                            while ((s = succ.nextInt()) != -1) {
                                if (distance[s] == -1) {
                                    distance[s] = d + 1;
                                    queue.add(s);

                                    assert this.checkOverflow(sigma, pos1, node, s);

                                    overflow |= sigma[s] > 9223372036854775807L - node;
                                    sigma[s] += node;
                                } else if(distance[s] == d + 1) {
                                    assert this.checkOverflow(sigma, pos1, node, s);

                                    overflow |= sigma[s] > 9223372036854775807L - node;
                                    sigma[s] += node;
                                }
                            }
                        }
                        catch (StackOverflowError e) {
                            e.printStackTrace();
                            System.out.println("Error");
                        }
                    }
                }

                if(overflow) {
                    throw new HarmonicCentrality.PathCountOverflowException();
                }

                while (true) {
                    --d;
                    if (d <= 0) {
                        if(HarmonicCentrality.this.pl != null) {
                            synchronized(HarmonicCentrality.this.pl) {
                                HarmonicCentrality.this.pl.update();
                            }
                        }
                        break;
                    }

                    start = this.cutPoints.getInt(d);
                    end = this.cutPoints.getInt(d + 1);

                    for(pos = start; pos < end; ++pos) {
                        pos1 = queue.getInt(pos);
                        double var22 = (double)sigma[pos1];
                        succ = graph.successors(pos1);

                        while((s = succ.nextInt()) != -1) {
                            if(distance[s] == d + 1) {
                               // delta[pos1] += (1.0D + delta[s]) * var22 / (double)sigma[s];
                            }
                        }
                    }

                    HarmonicCentrality var21 = HarmonicCentrality.this;
                    synchronized(HarmonicCentrality.this) {
                        for(pos1 = start; pos1 < end; ++pos1) {
                            int var23 = queue.getInt(pos1);
                            //HarmonicCentrality.this.harmonic[var23] += delta[var23];
                        }
                    }
                }

            }
        }

    }

    public static final class PathCountOverflowException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public PathCountOverflowException() {
        }

        public PathCountOverflowException(String s) {
            super(s);
        }
    }

}

