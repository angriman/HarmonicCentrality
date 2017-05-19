import it.unimi.dsi.webgraph.*;

import java.io.*;
import java.util.*;

/**
 * Wrote by Eugenio on 4/10/17.
 */
public class GraphBuilder {
    public static void main(String[] args) throws IOException {
        String graphName = "gnutella2";
        String graphBaseName = "./Graphs/"+graphName+"/"+graphName;
        //DataInputStream dataIn = new DataInputStream(new FileInputStream(graphBaseName));
       // ArcListASCIIGraph graph = new ArcListASCIIGraph(dataIn, 0);
        File graphFile = new File(graphBaseName+".txt");
        Scanner in = new Scanner(graphFile);
        Map<Integer, Integer> set = new HashMap<>();
        TreeSet<Arch> arches = new TreeSet<>();
        while (in.hasNextLine()) {
            Scanner line = new Scanner(in.nextLine());
            int from = line.nextInt();
            int to = line.nextInt();
            if (!set.containsKey(from)) {
                int id = set.size();
                set.put(from, id);
            }
            if (!set.containsKey(to)) {
                int id = set.size();
                set.put(to, id);
            }

            arches.add(new Arch(set.get(from), set.get(to)));
        }

        ArrayListMutableGraph graph = new ArrayListMutableGraph(set.size());

        while (!arches.isEmpty()) {
            Arch arch = arches.pollFirst();
            graph.addArc(arch.from, arch.to);
        }

        BVGraph.store(graph.immutableView(), "./Graphs/"+graphName+"/"+graphName);
        //ImmutableGraph graph = ArcListASCIIGraph.loadOffline(graphBaseName);
        //System.out.println(graph.numNodes());
       // ImmutableGraph graph = ArcListASCIIGraph.loadOffline(graphBaseName);
      //  BVGraph.store(graph, "./Graphs/"+graphName+"/"+graphName);
    }

    private static class Arch implements Comparable<Arch> {
        private int from = 0;
        private int to = 0;

        Arch(int from, int to) {
            this.from = from;
            this.to = to;
        }

        int getFrom() {return from;}
        int getTo() {return to;}



        @Override
        public int compareTo(Arch o) {
            int first = new Integer(o.getFrom()).compareTo(this.from);
            return (first == 0) ? new Integer(o.getTo()).compareTo(this.to) : first;
        }
    }

    private static class Node implements Comparable<Node> {

        private final Integer index;
        private Integer id;

        public Node(Integer index) {
            this.index = index;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public Integer getIndex() {
            return index;
        }

        @Override
        public int compareTo(Node o) {
            return this.getIndex().compareTo(o.getIndex());
        }
    }
}