package rubikscube;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;

/**
 * Main entry point for the Rubik's Cube solver.
 *
 * Usage: java rubikscube.Solver <input_file> <output_file>
 *
 * The program:
 * 1. Reads a cube state from the input file (54 stickers in unfolded format)
 * 2. Converts it to piece representation
 * 3. Solves using Kociemba's two-phase algorithm
 * 4. Writes the solution to the output file
 *
 * Solution format: Each character is a 90° clockwise turn of that face.
 * Multiple letters = multiple turns (e.g., "UU" = 180°, "UUU" = 270° = U')
 */
public class Solver {

    public static void main(String[] args) throws IOException {
        // Check command-line arguments
        if (args.length < 2) {
            System.out.println("File names are not specified");
            System.out.println("usage: java " + MethodHandles.lookup().lookupClass().getName() + " input_file output_file");
            return;
        }

        // Step 1: Parse input file into sticker representation
        String Input_Filename = args[0];
        StickerCube StartCube = new StickerCube(Input_Filename);

        // Step 2: Convert stickers to piece representation (permutation + orientation)
        PieceCube CC = StartCube.toPieceCube();

        // Step 3: Solve using two-phase algorithm
        // Parameters: cube, max solution length, timeout in seconds
        String solution = TwoPhase.solve(CC, 25, 10);

        // Check for errors
        if (solution.startsWith("Error")) {
            System.out.println("Could not solve: " + solution);
            return;
        }

        // Print solution in green
        System.out.println("\u001B[32mSolution: " + solution + "\u001B[0m");

        // Step 4: Verify the solution by applying it to the cube
        for (int i = 0; i < solution.length(); i++) {
            int move = charToMove(solution.charAt(i));
            if (move >= 0) {
                CC.applyMove(move);
            }
        }
        System.out.println("Solved: " + CC.isSolved());

        // Step 5: Write solution to output file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(args[1]))) {
            writer.write(solution);
        }
    }

    /**
     * Convert a face character to the corresponding quarter-turn move index.
     * Each face has moves at indices: face*3, face*3+1, face*3+2 for 90°, 180°, 270°.
     * This returns the 90° move; the solver's output repeats letters for more turns.
     *
     * @param c Face character (U, R, F, D, L, or B)
     * @return Move index for quarter turn, or -1 if invalid
     */
    private static int charToMove(char c) {
        switch (c) {
            case 'U': return 0;   // U quarter turn
            case 'R': return 3;   // R quarter turn
            case 'F': return 6;   // F quarter turn
            case 'D': return 9;   // D quarter turn
            case 'L': return 12;  // L quarter turn
            case 'B': return 15;  // B quarter turn
            default: return -1;   // Invalid character
        }
    }
}
