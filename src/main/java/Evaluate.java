import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
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
        try {
            File inputFile = new File("./results/dummygraph.json");
            Scanner input = new Scanner(inputFile);
            JSONObject reader = new JSONObject(input.nextLine());
            JSONObject tags = reader.getJSONObject("tags");
            System.out.println(tags.getInt("Num. arcs"));
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
