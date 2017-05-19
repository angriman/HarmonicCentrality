import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Objects;
import java.util.Scanner;
import java.util.TreeSet;

/**
 * Wrote by Eugenio on 3/27/17.
 */
public class GraphSorter {
    public static void main(String[] args) throws IOException {
        String graphName = "ca-condmat";
        File input = new File("./Graphs/"+graphName+"/"+graphName+".txt");
        File output = new File("./Graphs/"+graphName+"/"+"sorted_"+graphName+".txt");
        PrintWriter printWriter = new PrintWriter(output);
        Scanner in = new Scanner(input);
        TreeSet<Arch> treeSet = new TreeSet<>();
        int scannedLines = 0;
        int addedArcs = 0;
        while (in.hasNextLine()) {
            ++scannedLines;
            String line = in.nextLine();
            Scanner scanLine = new Scanner(line);
            if (!line.contains(".")) {
                int from = scanLine.nextInt();
                if (scanLine.hasNextInt()) {
                    int to = Math.abs(scanLine.nextInt());
                    Arch arch = new Arch(from, to);
                    int prevSize = treeSet.size();
                    if (treeSet.contains(arch)) {
                        System.out.println("Duplicate arc: " + arch.toString());
                    }
                    treeSet.add(arch);
                    if (treeSet.size() > prevSize) {
                        ++addedArcs;
                    }
                    scanLine.close();
                }
            }
        }

        System.out.println("Scanned lines = " + scannedLines + " Added arcs = " + addedArcs);

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
        private final int from;
        private final int to;

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

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Arch) {
                Arch comp = (Arch)obj;
                return comp.from == this.from && comp.to == this.to;
            }
            return false;
        }

        @Override
        public String toString() {
            return this.from + " " + this.to;
        }
    }
}
