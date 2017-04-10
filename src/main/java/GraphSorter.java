
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Scanner;
import java.util.TreeSet;

/**
 * Created by Eugenio on 3/27/17.
 */
public class GraphSorter {
    public static void main(String[] args) throws IOException {
        File input = new File("./Graphs/Facebook/Facebook.txt");
        File output = new File("./Graphs/Facebook/sortedFacebook.txt");
        PrintWriter printWriter = new PrintWriter(output);
        Scanner in = new Scanner(input);
        TreeSet<Arch> treeSet = new TreeSet<>();

        while (in.hasNextLine()) {
            String line = in.nextLine();
            Scanner scanLine = new Scanner(line);
            int from = scanLine.nextInt();
            int to = scanLine.nextInt();
            Arch arch = new Arch(from, to);
            treeSet.add(arch);
            scanLine.close();
        }
        Iterator<Arch> iterator = treeSet.descendingIterator();
        while(iterator.hasNext()) {
            Arch cur = iterator.next();
            printWriter.println(cur.getFrom() + " " + cur.getTo());
        }

        //ImmutableGraph graph = ArcListASCIIGraph.loadSequential(graphName);
        //BVGraph graph = BVGraph.loadOffline(graphName, null);
        //graph.writeOffsets(new OutputBitStream(graphName.toString()+".offsets"), null);
        printWriter.close();
        in.close();
    }

    private static class Arch implements Comparable<Arch> {
        private int from = 0;
        private int to = 0;

        public Arch() {}

        public Arch(int from, int to) {
            this.from = from;
            this.to = to;
        }

        public int getFrom() {return from;}
        public int getTo() {return to;}

        @Override
        public int compareTo(Arch o) {
            int first = new Integer(o.getFrom()).compareTo(this.from);
            return (first == 0) ? new Integer(o.getTo()).compareTo(this.to) : first;
        }
    }
}
