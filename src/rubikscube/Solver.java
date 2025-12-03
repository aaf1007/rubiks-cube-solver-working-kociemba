package rubikscube;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;


public class Solver {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("File names are not specified");
            System.out.println("usage: java " + MethodHandles.lookup().lookupClass().getName() + " input_file output_file");
            return;
        }

        String inputFilename = args[0];
        RubiksCube rubiksCube = new RubiksCube(inputFilename);
        Cubie cubie = rubiksCube.toCubie();

        // Solve using two-phase algorithm
        String solution = TwoPhase.solve(cubie, 25, 10);

        //System.out.println("\u001B[32mSolution: " + solution + "\u001B[0m");

        for (int i = 0; i < solution.length(); i++) {
            int move = charToMove(solution.charAt(i));
            if (move >= 0) {
                cubie.applyMove(move);
            }
        }
        // Verify Solution
        //System.out.println("Solved: " + cubie.isSolved());

        // Write solution to output file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(args[1]))) {
            writer.write(solution);
        }
    }

    /**
     * Convert face character to turn move index
     */
    private static int charToMove(char c) {
        switch (c) {
            case 'U': return 0;  
            case 'R': return 3;
            case 'F': return 6;
            case 'D': return 9;
            case 'L': return 12;
            case 'B': return 15;
            default: return -1;
        }
    }
}
