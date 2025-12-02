package rubikscube;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;

/**
 * Entry point for Rubik's Cube solver using Kociemba's two-phase algorithm.
 */
public class Solver {

    public static void main(String[] args) throws IOException {
//      System.out.println("number of arguments: " + args.length);
//      for (int i = 0; i < args.length; i++) {
//          System.out.println(args[i]);
//      }
        if (args.length < 2) {
            System.out.println("File names are not specified");
            System.out.println("usage: java " + MethodHandles.lookup().lookupClass().getName() + " input_file output_file");
            return;
        }

        // TODO
        //File input = new File(args[0]);
        String Input_Filename = args[0];
        StickerCube StartCube = new StickerCube(Input_Filename);
        PieceCube CC = StartCube.toPieceCube();

        // Verify cube is valid
        int verify = CC.verify();
        if (verify != 0) {
            System.out.println("Invalid cube: Error " + Math.abs(verify));
            return;
        }

        // Solve using two-phase algorithm
        String solution = TwoPhase.solve(CC, 25, 10);

        if (solution.startsWith("Error")) {
            System.out.println("Could not solve: " + solution);
            return;
        }

        System.out.println("Solution: " + solution);

        for (int i = 0; i < solution.length(); i++) {
            int move = charToMove(solution.charAt(i));  // char → int
            if (move >= 0) {
                CC.applyMove(move);  // now it's an int
            }
        }

        System.out.println("Solved: " + CC.isSolved());

        // Verify solution works
        // if (verifySolution(Input_Filename, solution)) {
        //     System.out.println("Solution verified!");
        // } else {
        //     System.out.println("Solution verification failed!");
        // }

        // Write solution to output file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(args[1]))) {
            writer.write(solution);
        }
    }

    /**
     * Verify solution by applying moves to original cube and checking if solved.
     * Solution format: repeated letters (e.g., UUU = U', UU = U2)
     */
    private static boolean verifySolution(String filename, String solution) throws IOException {
        StickerCube cube = new StickerCube(filename);
        PieceCube pc = cube.toPieceCube();

        // Each character is a single quarter turn
        for (int i = 0; i < solution.length(); i++) {
            int move = charToMove(solution.charAt(i));
            if (move >= 0) {
                pc.applyMove(move);
            }
        }

        return pc.isSolved();
    }

    /**
     * Convert face character to quarter-turn move index.
     */
    private static int charToMove(char c) {
        switch (c) {
            case 'U': return 0;  // U (quarter turn)
            case 'R': return 3;
            case 'F': return 6;
            case 'D': return 9;
            case 'L': return 12;
            case 'B': return 15;
            default: return -1;
        }
    }
}
