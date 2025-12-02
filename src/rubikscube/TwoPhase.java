package rubikscube;

/**
 * Two-phase algorithm implementation for solving Rubik's Cube using IDA* search
 *
 * The algorithm splits solving into two phases:
 * Phase 1: Transform cube into G1 subgroup (twist=0, flip=0, slice edges in middle)
 *          Uses all 18 moves. Typical depth: 7-12 moves.
 * Phase 2: Solve from G1 to identity using restricted moves (U,D all; R,F,L,B only 180° moves)
 *          Typical depth: 10-18 moves.
 *
 * IDA* tries increasingly deeper solutions, using pruning
 * tables as heuristics to avoid exploring branches that can't lead to shorter solutions.
 */
public class TwoPhase {

    // faceIndex[i] = which face (0-5 for U,R,F,D,L,B)
    // turnCount[i] = turn amount (1,2,3 for 90°,180°,270°)
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
     * Convert the move sequence into a solution string.
     * CCW moves -> U' becomes UUU and etc...
     */
    static String solutionToString(int length) {
        StringBuilder solution = new StringBuilder();
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
                solution.append(face);
            }
        }
        return solution.toString();
    }

    /**
     * Solve the cube using the two-phase algorithm.
     *
     * @param cube     The cube to solve (as piece representation)
     * @param maxDepth Max total solution length to search for
     * @param timeOut  Timeout in seconds (shifted left 10 bits for milliseconds)
     * @return Solution string, or "Error N" if solving fails
     */
    public static String solve(Cubie cube, int maxDepth, long timeOut) {
        int result;

        // Validate the cube is solvable
        if ((result = cube.verify()) != 0)
            return "Error " + Math.abs(result);

        // Extract all coordinates from the input cube
        // These form the starting point (depth 0) for the search
        edgeOrient[0] = cube.getFlip();
        cornerOrient[0] = cube.getTwist();
        parity[0] = cube.cornerParity();
        int fullSlice = cube.getSlice();
        slicePos[0] = fullSlice / 24;
        slicePerm[0] = fullSlice;
        cornerPerm[0] = cube.getCornerPerm();
        urToUl[0] = cube.getURtoUL();
        ubToDf[0] = cube.getUBtoDF();

        long startTime = System.currentTimeMillis();

        // IDA* outer loop: try increasing depth limits until solution found
        int depthPhase1 = 1;
        while (depthPhase1 <= maxDepth) {

            // Initialize search at depth 0
            int depth = 0;
            faceIndex[0] = 0;
            turnCount[0] = 1;

            // DFS search at current depth limit
            while (depth >= 0) {

                // Check timeout
                if (System.currentTimeMillis() - startTime > timeOut << 10) {
                    return "Error 8";
                }

                // Compute coordinates and heuristic for current move
                int moveIndex = 3 * faceIndex[depth] + turnCount[depth] - 1;
                edgeOrient[depth + 1] = Tables.flipMove[edgeOrient[depth]][moveIndex];
                cornerOrient[depth + 1] = Tables.twistMove[cornerOrient[depth]][moveIndex];
                slicePos[depth + 1] = Tables.sliceMove[slicePos[depth] * 24][moveIndex] / 24;

                minDistPhase1[depth + 1] = Math.max(
                    Tables.getPruning(Tables.sliceFlipPrune, Tables.N_SLICE1 * edgeOrient[depth + 1] + slicePos[depth + 1]),
                    Tables.getPruning(Tables.sliceTwistPrune, Tables.N_SLICE1 * cornerOrient[depth + 1] + slicePos[depth + 1]));

                // Check if we reached G1 (heuristic = 0) at the target depth
                if (minDistPhase1[depth + 1] == 0 && depth == depthPhase1 - 1) {
                    // Try phase 2
                    result = totalDepth(depthPhase1, maxDepth);
                    if (result >= 0) {
                        // Verify no redundant consecutive moves at phase boundary
                        if (result == depthPhase1 || (faceIndex[depthPhase1 - 1] != faceIndex[depthPhase1] && faceIndex[depthPhase1 - 1] != faceIndex[depthPhase1] + 3)) {
                            return solutionToString(result);
                        }
                    }
                }

                // Can we go deeper? Check if remaining depth budget > heuristic estimate
                if (depth < depthPhase1 - 1 && depthPhase1 - depth - 1 >= minDistPhase1[depth + 1]) {
                    // Go deeper: pick first valid face (avoid same/opposite as previous)
                    depth++;
                    faceIndex[depth] = 0;
                    // Skip invalid faces (same as previous, or opposite in wrong order)
                    while (faceIndex[depth - 1] == faceIndex[depth] || faceIndex[depth - 1] - 3 == faceIndex[depth]) {
                        faceIndex[depth]++;
                    }
                    turnCount[depth] = 1;
                } else {
                    // Can't go deeper or already at max depth - try next move at current level
                    // Advance to next move: increment turnCount, then faceIndex if needed
                    boolean foundNext = false;
                    while (!foundNext && depth >= 0) {
                        turnCount[depth]++;
                        if (turnCount[depth] > 3) {
                            // Try next face
                            turnCount[depth] = 1;
                            faceIndex[depth]++;
                            // Skip invalid faces
                            while (depth > 0 && faceIndex[depth] <= 5 && (faceIndex[depth - 1] == faceIndex[depth] || faceIndex[depth - 1] - 3 == faceIndex[depth])) {
                                faceIndex[depth]++;
                            }
                            if (faceIndex[depth] > 5) {
                                // No more moves at this level - backtrack
                                depth--;
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
        int moveIndex, cornerDist, edgeDist;
        int maxDepthPhase2 = Math.min(10, maxDepth - depthPhase1);

        // Replay phase 1 moves to compute phase 2 starting coordinates
        for (int i = 0; i < depthPhase1; i++) {
            moveIndex = 3 * faceIndex[i] + turnCount[i] - 1;
            cornerPerm[i + 1] = Tables.cornerPermMove[cornerPerm[i]][moveIndex];
            slicePerm[i + 1] = Tables.sliceMove[slicePerm[i]][moveIndex];
            parity[i + 1] = Tables.parityMove[parity[i]][moveIndex];
        }

        // Early pruning: check corner+slice heuristic
        cornerDist = Tables.getPruning(Tables.sliceCornerPrune,
            (Tables.N_SLICE2 * cornerPerm[depthPhase1] + slicePerm[depthPhase1]) * 2 + parity[depthPhase1]);
        if (cornerDist > maxDepthPhase2)
            return -1;

        // Compute edge permutation coordinate
        for (int i = 0; i < depthPhase1; i++) {
            moveIndex = 3 * faceIndex[i] + turnCount[i] - 1;
            urToUl[i + 1] = Tables.urToUlMove[urToUl[i]][moveIndex];
            ubToDf[i + 1] = Tables.ubToDfMove[ubToDf[i]][moveIndex];
        }
        udEdgePerm[depthPhase1] = Tables.mergeURtoULandUBtoDF[urToUl[depthPhase1]][ubToDf[depthPhase1]];

        // Early pruning: check edge+slice heuristic
        edgeDist = Tables.getPruning(Tables.sliceEdgePrune,
            (Tables.N_SLICE2 * udEdgePerm[depthPhase1] + slicePerm[depthPhase1]) * 2 + parity[depthPhase1]);
        if (edgeDist > maxDepthPhase2)
            return -1;

        // Check if already solved
        if (Math.max(cornerDist, edgeDist) == 0)
            return depthPhase1;

        // IDA* outer loop for phase 2
        int depthPhase2 = 1;
        while (depthPhase2 <= maxDepthPhase2) {

            // Initialize search at phase 1 end point
            int depth = depthPhase1;
            faceIndex[depth] = 0;
            turnCount[depth] = 1;

            // DFS search at current depth limit
            while (depth >= depthPhase1) {

                // Compute coordinates and heuristic for current move
                moveIndex = 3 * faceIndex[depth] + turnCount[depth] - 1;
                cornerPerm[depth + 1] = Tables.cornerPermMove[cornerPerm[depth]][moveIndex];
                slicePerm[depth + 1] = Tables.sliceMove[slicePerm[depth]][moveIndex];
                parity[depth + 1] = Tables.parityMove[parity[depth]][moveIndex];
                udEdgePerm[depth + 1] = Tables.udEdgePermMove[udEdgePerm[depth]][moveIndex];

                minDistPhase2[depth + 1] = Math.max(
                    Tables.getPruning(Tables.sliceEdgePrune,
                        (Tables.N_SLICE2 * udEdgePerm[depth + 1] + slicePerm[depth + 1]) * 2 + parity[depth + 1]),
                    Tables.getPruning(Tables.sliceCornerPrune,
                        (Tables.N_SLICE2 * cornerPerm[depth + 1] + slicePerm[depth + 1]) * 2 + parity[depth + 1]));

                // Check if solved (heuristic = 0)
                if (minDistPhase2[depth + 1] == 0) {
                    return depthPhase1 + (depth - depthPhase1 + 1);
                }

                // Can we go deeper?
                if (depth < depthPhase1 + depthPhase2 - 1 &&
                    depthPhase1 + depthPhase2 - depth - 1 >= minDistPhase2[depth + 1]) {
                    // Go deeper: pick first valid face
                    depth++;
                    faceIndex[depth] = 0;
                    // Skip invalid faces
                    while (faceIndex[depth] <= 5 && (faceIndex[depth - 1] == faceIndex[depth] || faceIndex[depth - 1] - 3 == faceIndex[depth])) {
                        faceIndex[depth]++;
                    }
                    // Set turnCount based on face (U,D get 1; R,F,L,B get 2)
                    turnCount[depth] = (faceIndex[depth] == 0 || faceIndex[depth] == 3) ? 1 : 2;
                } else {
                    // Try next move at current level
                    boolean foundNext = false;
                    while (!foundNext && depth >= depthPhase1) {
                        // Advance turnCount (U,D: 1,2,3; R,F,L,B: only 2)
                        if (faceIndex[depth] == 0 || faceIndex[depth] == 3) {
                            turnCount[depth]++;
                            if (turnCount[depth] > 3) {
                                turnCount[depth] = 1;
                                faceIndex[depth]++;
                            } else {
                                foundNext = true;
                            }
                        } else {
                            // R,F,L,B only have half turns, so go to next face
                            faceIndex[depth]++;
                        }

                        if (!foundNext) {
                            // Skip invalid faces
                            while (depth > depthPhase1 && faceIndex[depth] <= 5 &&
                                   (faceIndex[depth - 1] == faceIndex[depth] || faceIndex[depth - 1] - 3 == faceIndex[depth])) {
                                faceIndex[depth]++;
                            }
                            if (faceIndex[depth] > 5) {
                                // Backtrack
                                depth--;
                            } else {
                                turnCount[depth] = (faceIndex[depth] == 0 || faceIndex[depth] == 3) ? 1 : 2;
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
