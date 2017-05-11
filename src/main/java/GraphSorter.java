
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
        String graphName = "twitter";
        File input = new File("./Graphs/"+graphName+"/"+graphName+".txt");
        File output = new File("./Graphs/"+graphName+"/"+"sorted_"+graphName+".txt");
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
        Arch first = iterator.next();
        boolean minusOne = false;
        if (first.getFrom() == 1) {
            minusOne = true;
        }
        print(printWriter, first, minusOne);
        printWriter.println();

        while(iterator.hasNext()) {
            Arch cur = iterator.next();

            print(printWriter, cur, minusOne);
            if (iterator.hasNext()) {
                printWriter.println();
            }
        }
        printWriter.close();
        in.close();
    }

    private static void print(PrintWriter printWriter, Arch arch, boolean m) {

        int from = arch.getFrom();
        int to = arch.getTo();
        if (m) {
            from-=1;
            to-=1;
        }
        printWriter.print(from + " " + to);
    }

    private static class Arch implements Comparable<Arch> {
        private int from = 0;
        private int to = 0;

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
