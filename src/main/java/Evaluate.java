import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

/**
 * Created by eugenio on 22/11/16.
 */

/**
 * Reads results files and computes performance metrics
 */
public class Evaluate {
    public static void main(String[] args) throws IOException {
        File inputFile = new File("./results.json");
        Scanner input = new Scanner(inputFile);
        JSONObject reader = new JSONObject(input.nextLine());
        System.out.println("NODES: " + reader.getInt("nodes") + "\nARCS: " + reader.getInt("arcs") + "\nTIME: " + reader.getDouble("time"));
    }
}
