package rubikscube;

import java.io.*;
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

        // Verify solution works
        if (verifySolution(Input_Filename, solution)) {
            System.out.println("Solution verified!");
        } else {
            System.out.println("Solution verification failed!");
        }

        // Write solution to output file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(args[1]))) {
            writer.write(solution);
        }
    }

    /**
     * Verify solution by applying moves to original cube and checking if solved.
     */
    private static boolean verifySolution(String filename, String solution) throws IOException {
        StickerCube cube = new StickerCube(filename);
        PieceCube pc = cube.toPieceCube();

        // Parse and apply each move in solution
        int i = 0;
        while (i < solution.length()) {
            char face = solution.charAt(i);
            int baseMove = faceToBase(face);
            if (baseMove < 0) {
                i++;
                continue;
            }

            // Check for modifier (2 or ')
            int move = baseMove; // Default: quarter turn clockwise
            if (i + 1 < solution.length()) {
                char next = solution.charAt(i + 1);
                if (next == '2') {
                    move = baseMove + 1; // Half turn
                    i++;
                } else if (next == '\'') {
                    move = baseMove + 2; // Counter-clockwise
                    i++;
                }
            }

            pc.applyMove(move);
            i++;
        }

        return pc.isSolved();
    }

    /**
     * Convert face character to base move index.
     */
    private static int faceToBase(char c) {
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
