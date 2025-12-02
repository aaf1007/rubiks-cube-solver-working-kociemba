package rubikscube;

/**
 * Two-phase algorithm implementation for solving Rubik's Cube using IDA* search.
 *
 * The algorithm splits solving into two phases:
 * Phase 1: Transform cube into G1 subgroup (twist=0, flip=0, slice edges in middle)
 *          Uses all 18 moves. Typical depth: 7-12 moves.
 * Phase 2: Solve from G1 to identity using restricted moves (U,D all; R,F,L,B only 180°)
 *          Typical depth: 10-18 moves.
 *
 * IDA* (Iterative Deepening A*) tries increasingly deeper solutions, using pruning
 * tables as heuristics to avoid exploring branches that can't lead to shorter solutions.
 */
public class TwoPhase {

    // Move sequence storage: faceIndex[i] = which face (0-5 for U,R,F,D,L,B)
    //                        turnCount[i] = turn amount (1,2,3 for 90°,180°,270°)
    static int[] faceIndex = new int[31];
    static int[] turnCount = new int[31];

    // Phase 1 coordinates at each search depth
    // These track corner orientation, edge orientation, and E-slice edge positions
    static int[] edgeOrient = new int[31];      // Edge orientation coordinate
    static int[] cornerOrient = new int[31];     // Corner orientation coordinate
    static int[] slicePos = new int[31];     // E-slice edge position coordinate (just position, /24)

    // Phase 2 coordinates at each search depth
    static int[] parity = new int[31];      // Permutation parity (0 or 1)
    static int[] cornerPerm = new int[31];  // Corner permutation coordinate
    static int[] slicePerm = new int[31];   // Full slice coordinate (position + order)
    static int[] urToUl = new int[31];      // Helper: edges UR, UF, UL
    static int[] ubToDf = new int[31];      // Helper: edges UB, DR, DF
    static int[] udEdgePerm = new int[31];  // UD edge permutation (merged from helpers)

    // IDA* heuristic: minimum moves estimated to reach goal from each search depth
    static int[] minDistPhase1 = new int[31];
    static int[] minDistPhase2 = new int[31];

    /**
     * Convert the move sequence (axis/power arrays) into a solution string.
     * Each move is represented by repeating the face letter:
     * - 1 letter = 90° clockwise (e.g., "U")
     * - 2 letters = 180° (e.g., "UU")
     * - 3 letters = 270° clockwise = 90° counter-clockwise (e.g., "UUU" = U')
     */
    static String solutionToString(int length) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < length; i++) {
            String face = switch (faceIndex[i]) {
                case 0 -> "U";
                case 1 -> "R";
                case 2 -> "F";
                case 3 -> "D";
                case 4 -> "L";
                case 5 -> "B";
                default -> "";
            };
            // Repeat face letter according to turn amount
            for (int j = 0; j < turnCount[i]; j++) {
                s.append(face);
            }
        }
        return s.toString();
    }

    /**
     * Main entry point: solve the cube using the two-phase algorithm.
     *
     * @param pc       The cube to solve (as piece representation)
     * @param maxDepth Maximum total solution length to search for
     * @param timeOut  Timeout in seconds (shifted left 10 bits for milliseconds)
     * @return Solution string, or "Error N" if solving fails
     */
    public static String solve(Cubie pc, int maxDepth, long timeOut) {
        int s;

        // Validate the cube is solvable (correct pieces, orientations, parity)
        if ((s = pc.verify()) != 0)
            return "Error " + Math.abs(s);

        // Extract all coordinates from the input cube
        // These form the starting point (depth 0) for the search
        edgeOrient[0] = pc.getFlip();
        cornerOrient[0] = pc.getTwist();
        parity[0] = pc.cornerParity();
        int fullSlice = pc.getSlice();
        slicePos[0] = fullSlice / 24;
        slicePerm[0] = fullSlice;
        cornerPerm[0] = pc.getCornerPerm();
        urToUl[0] = pc.getURtoUL();
        ubToDf[0] = pc.getUBtoDF();

        long tStart = System.currentTimeMillis();

        // IDA* outer loop: try increasing depth limits until solution found
        int depthPhase1 = 1;
        while (depthPhase1 <= maxDepth) {

            // Initialize search at depth 0
            int n = 0;
            faceIndex[0] = 0;
            turnCount[0] = 1;

            // DFS search at current depth limit
            while (n >= 0) {

                // Check timeout
                if (System.currentTimeMillis() - tStart > timeOut << 10) {
                    return "Error 8";
                }

                // Compute coordinates and heuristic for current move
                int mv = 3 * faceIndex[n] + turnCount[n] - 1;
                edgeOrient[n + 1] = Tables.flipMove[edgeOrient[n]][mv];
                cornerOrient[n + 1] = Tables.twistMove[cornerOrient[n]][mv];
                slicePos[n + 1] = Tables.sliceMove[slicePos[n] * 24][mv] / 24;

                minDistPhase1[n + 1] = Math.max(
                    Tables.getPruning(Tables.sliceFlipPrune, Tables.N_SLICE1 * edgeOrient[n + 1] + slicePos[n + 1]),
                    Tables.getPruning(Tables.sliceTwistPrune, Tables.N_SLICE1 * cornerOrient[n + 1] + slicePos[n + 1]));

                // Check if we reached G1 (heuristic = 0) at the target depth
                if (minDistPhase1[n + 1] == 0 && n == depthPhase1 - 1) {
                    // Try phase 2
                    s = totalDepth(depthPhase1, maxDepth);
                    if (s >= 0) {
                        // Verify no redundant consecutive moves at phase boundary
                        if (s == depthPhase1 || (faceIndex[depthPhase1 - 1] != faceIndex[depthPhase1] && faceIndex[depthPhase1 - 1] != faceIndex[depthPhase1] + 3)) {
                            return solutionToString(s);
                        }
                    }
                }

                // Can we go deeper? Check if remaining depth budget > heuristic estimate
                if (n < depthPhase1 - 1 && depthPhase1 - n - 1 >= minDistPhase1[n + 1]) {
                    // Go deeper: pick first valid axis (avoid same/opposite as previous)
                    n++;
                    faceIndex[n] = 0;
                    // Skip invalid axes (same as previous, or opposite in wrong order)
                    while (faceIndex[n - 1] == faceIndex[n] || faceIndex[n - 1] - 3 == faceIndex[n]) {
                        faceIndex[n]++;
                    }
                    turnCount[n] = 1;
                } else {
                    // Can't go deeper or already at max depth - try next move at current level
                    // Advance to next move: increment power, then axis if needed
                    boolean foundNext = false;
                    while (!foundNext && n >= 0) {
                        turnCount[n]++;
                        if (turnCount[n] > 3) {
                            // Try next axis
                            turnCount[n] = 1;
                            faceIndex[n]++;
                            // Skip invalid axes
                            while (n > 0 && faceIndex[n] <= 5 && (faceIndex[n - 1] == faceIndex[n] || faceIndex[n - 1] - 3 == faceIndex[n])) {
                                faceIndex[n]++;
                            }
                            if (faceIndex[n] > 5) {
                                // No more moves at this level - backtrack
                                n--;
                            } else {
                                foundNext = true;
                            }
                        } else {
                            foundNext = true;
                        }
                    }
                }
            }

            // No solution at this depth - increase limit
            depthPhase1++;
        }

        return "Error 7";  // Exceeded max depth
    }

    /**
     * Phase 2 search: from G1 subgroup to solved state.
     * Called when phase 1 reaches G1 (twist=0, flip=0, slice edges in place).
     *
     * Phase 2 uses restricted moves that preserve G1:
     * - U, D: all turns (1, 2, 3 quarter turns)
     * - R, F, L, B: only half turns (180°)
     *
     * @param depthPhase1 Number of moves used in phase 1
     * @param maxDepth    Maximum total solution length
     * @return Total solution length (phase1 + phase2), or -1 if no solution found
     */
    static int totalDepth(int depthPhase1, int maxDepth) {
        int mv, d1, d2;
        int maxDepthPhase2 = Math.min(10, maxDepth - depthPhase1);

        // Replay phase 1 moves to compute phase 2 starting coordinates
        for (int i = 0; i < depthPhase1; i++) {
            mv = 3 * faceIndex[i] + turnCount[i] - 1;
            cornerPerm[i + 1] = Tables.cornerPermMove[cornerPerm[i]][mv];
            slicePerm[i + 1] = Tables.sliceMove[slicePerm[i]][mv];
            parity[i + 1] = Tables.parityMove[parity[i]][mv];
        }

        // Early pruning: check corner+slice heuristic
        d1 = Tables.getPruning(Tables.sliceCornerPrune,
            (Tables.N_SLICE2 * cornerPerm[depthPhase1] + slicePerm[depthPhase1]) * 2 + parity[depthPhase1]);
        if (d1 > maxDepthPhase2)
            return -1;

        // Compute edge permutation coordinate
        for (int i = 0; i < depthPhase1; i++) {
            mv = 3 * faceIndex[i] + turnCount[i] - 1;
            urToUl[i + 1] = Tables.urToUlMove[urToUl[i]][mv];
            ubToDf[i + 1] = Tables.ubToDfMove[ubToDf[i]][mv];
        }
        udEdgePerm[depthPhase1] = Tables.mergeURtoULandUBtoDF[urToUl[depthPhase1]][ubToDf[depthPhase1]];

        // Early pruning: check edge+slice heuristic
        d2 = Tables.getPruning(Tables.sliceEdgePrune,
            (Tables.N_SLICE2 * udEdgePerm[depthPhase1] + slicePerm[depthPhase1]) * 2 + parity[depthPhase1]);
        if (d2 > maxDepthPhase2)
            return -1;

        // Check if already solved
        if (Math.max(d1, d2) == 0)
            return depthPhase1;

        // IDA* outer loop for phase 2
        int depthPhase2 = 1;
        while (depthPhase2 <= maxDepthPhase2) {

            // Initialize search at phase 1 end point
            int n = depthPhase1;
            faceIndex[n] = 0;
            turnCount[n] = 1;

            // DFS search at current depth limit
            while (n >= depthPhase1) {

                // Compute coordinates and heuristic for current move
                mv = 3 * faceIndex[n] + turnCount[n] - 1;
                cornerPerm[n + 1] = Tables.cornerPermMove[cornerPerm[n]][mv];
                slicePerm[n + 1] = Tables.sliceMove[slicePerm[n]][mv];
                parity[n + 1] = Tables.parityMove[parity[n]][mv];
                udEdgePerm[n + 1] = Tables.udEdgePermMove[udEdgePerm[n]][mv];

                minDistPhase2[n + 1] = Math.max(
                    Tables.getPruning(Tables.sliceEdgePrune,
                        (Tables.N_SLICE2 * udEdgePerm[n + 1] + slicePerm[n + 1]) * 2 + parity[n + 1]),
                    Tables.getPruning(Tables.sliceCornerPrune,
                        (Tables.N_SLICE2 * cornerPerm[n + 1] + slicePerm[n + 1]) * 2 + parity[n + 1]));

                // Check if solved (heuristic = 0)
                if (minDistPhase2[n + 1] == 0) {
                    return depthPhase1 + (n - depthPhase1 + 1);
                }

                // Can we go deeper?
                if (n < depthPhase1 + depthPhase2 - 1 &&
                    depthPhase1 + depthPhase2 - n - 1 >= minDistPhase2[n + 1]) {
                    // Go deeper: pick first valid axis
                    n++;
                    faceIndex[n] = 0;
                    // Skip invalid axes
                    while (faceIndex[n] <= 5 && (faceIndex[n - 1] == faceIndex[n] || faceIndex[n - 1] - 3 == faceIndex[n])) {
                        faceIndex[n]++;
                    }
                    // Set power based on axis type (U,D get 1; R,F,L,B get 2)
                    turnCount[n] = (faceIndex[n] == 0 || faceIndex[n] == 3) ? 1 : 2;
                } else {
                    // Try next move at current level
                    boolean foundNext = false;
                    while (!foundNext && n >= depthPhase1) {
                        // Advance power (U,D: 1,2,3; R,F,L,B: only 2)
                        if (faceIndex[n] == 0 || faceIndex[n] == 3) {
                            turnCount[n]++;
                            if (turnCount[n] > 3) {
                                turnCount[n] = 1;
                                faceIndex[n]++;
                            } else {
                                foundNext = true;
                            }
                        } else {
                            // R,F,L,B only have half turns, so go to next axis
                            faceIndex[n]++;
                        }

                        if (!foundNext) {
                            // Skip invalid axes
                            while (n > depthPhase1 && faceIndex[n] <= 5 &&
                                   (faceIndex[n - 1] == faceIndex[n] || faceIndex[n - 1] - 3 == faceIndex[n])) {
                                faceIndex[n]++;
                            }
                            if (faceIndex[n] > 5) {
                                // Backtrack
                                n--;
                            } else {
                                turnCount[n] = (faceIndex[n] == 0 || faceIndex[n] == 3) ? 1 : 2;
                                foundNext = true;
                            }
                        }
                    }
                }
            }

            // No solution at this depth - increase limit
            depthPhase2++;
        }

        return -1;  // No solution found within depth limit
    }
}
