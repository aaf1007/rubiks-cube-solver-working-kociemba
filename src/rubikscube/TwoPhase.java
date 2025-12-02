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

    // Move sequence storage: axis[i] = which face (0-5 for U,R,F,D,L,B)
    //                        power[i] = turn amount (1,2,3 for 90°,180°,270°)
    static int[] axis = new int[31];
    static int[] power = new int[31];

    // Phase 1 coordinates at each search depth
    // These track corner orientation, edge orientation, and E-slice edge positions
    static int[] flip = new int[31];      // Edge orientation coordinate
    static int[] twist = new int[31];     // Corner orientation coordinate
    static int[] slice = new int[31];     // E-slice edge position coordinate (just position, /24)

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
            String face = switch (axis[i]) {
                case 0 -> "U";
                case 1 -> "R";
                case 2 -> "F";
                case 3 -> "D";
                case 4 -> "L";
                case 5 -> "B";
                default -> "";
            };
            // Repeat face letter according to turn amount
            for (int j = 0; j < power[i]; j++) {
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
    public static String solve(PieceCube pc, int maxDepth, long timeOut) {
        int s;

        // Validate the cube is solvable (correct pieces, orientations, parity)
        if ((s = pc.verify()) != 0)
            return "Error " + Math.abs(s);

        // Extract all coordinates from the input cube
        // These form the starting point (depth 0) for the search
        flip[0] = pc.getFlip();           // Edge orientations
        twist[0] = pc.getTwist();         // Corner orientations
        parity[0] = pc.cornerParity();    // Permutation parity
        int fullSlice = pc.getSlice();
        slice[0] = fullSlice / 24;        // Just position (for phase 1)
        slicePerm[0] = fullSlice;         // Full coordinate (for phase 2)
        cornerPerm[0] = pc.getCornerPerm();
        urToUl[0] = pc.getURtoUL();
        ubToDf[0] = pc.getUBtoDF();

        // Initialize search state
        power[0] = 0;
        axis[0] = 0;
        minDistPhase1[1] = 1;  // Force at least one iteration
        int mv, n = 0;         // n = current search depth
        boolean busy = false;  // True when backtracking
        int depthPhase1 = 1;   // Current IDA* depth limit

        long tStart = System.currentTimeMillis();

        // Main IDA* loop for Phase 1
        // Iteratively increases depth limit until a solution is found
        do {
            // Inner loop: generate and try moves at current depth
            do {
                if ((depthPhase1 - n > minDistPhase1[n + 1]) && !busy) {
                    // We have depth budget remaining - go deeper
                    // Avoid same axis as previous move (redundant moves)
                    if (axis[n] == 0 || axis[n] == 3) {
                        axis[++n] = 1;  // After U or D, try R
                    } else {
                        axis[++n] = 0;  // After R,F,L,B, try U
                    }
                    power[n] = 1;  // Start with quarter turn
                } else if (++power[n] > 3) {
                    // Tried all turn amounts for this face, move to next face
                    do {
                        if (++axis[n] > 5) {
                            // Tried all faces - need to backtrack or increase depth
                            if (System.currentTimeMillis() - tStart > timeOut << 10)
                                return "Error 8";  // Timeout

                            if (n == 0) {
                                // At root with no solution - increase depth limit
                                if (depthPhase1 >= maxDepth)
                                    return "Error 7";  // Exceeded max depth
                                else {
                                    depthPhase1++;
                                    axis[n] = 0;
                                    power[n] = 1;
                                    busy = false;
                                    break;
                                }
                            } else {
                                // Backtrack to previous depth
                                n--;
                                busy = true;
                                break;
                            }
                        } else {
                            power[n] = 1;
                            busy = false;
                        }
                    // Skip if same axis as previous move, or opposite face in wrong order
                    // (e.g., U then D is redundant with D then U)
                    } while (n != 0 && (axis[n - 1] == axis[n] || axis[n - 1] - 3 == axis[n]));
                } else {
                    busy = false;
                }
            } while (busy);

            // Apply the current move using move tables (O(1) lookup)
            mv = 3 * axis[n] + power[n] - 1;  // Convert to move index 0-17
            flip[n + 1] = Tables.flipMove[flip[n]][mv];
            twist[n + 1] = Tables.twistMove[twist[n]][mv];
            slice[n + 1] = Tables.sliceMove[slice[n] * 24][mv] / 24;

            // Compute phase 1 heuristic as max of two pruning table lookups
            // This gives a lower bound on moves needed to reach G1
            minDistPhase1[n + 1] = Math.max(
                Tables.getPruning(Tables.sliceFlipPrune, Tables.N_SLICE1 * flip[n + 1] + slice[n + 1]),
                Tables.getPruning(Tables.sliceTwistPrune, Tables.N_SLICE1 * twist[n + 1] + slice[n + 1]));

            // If heuristic is 0, we've reached G1 - attempt phase 2
            if (minDistPhase1[n + 1] == 0 && n >= depthPhase1 - 5) {
                minDistPhase1[n + 1] = 10;  // Prevent re-entering phase 2 from same state
                if (n == depthPhase1 - 1 && (s = totalDepth(depthPhase1, maxDepth)) >= 0) {
                    // Phase 2 succeeded - check solution is valid (no redundant consecutive moves)
                    if (s == depthPhase1 || (axis[depthPhase1 - 1] != axis[depthPhase1] && axis[depthPhase1 - 1] != axis[depthPhase1] + 3))
                        return solutionToString(s);
                }
            }
        } while (true);
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
        // We need corner permutation and slice permutation, which weren't tracked in phase 1
        for (int i = 0; i < depthPhase1; i++) {
            mv = 3 * axis[i] + power[i] - 1;
            cornerPerm[i + 1] = Tables.cornerPermMove[cornerPerm[i]][mv];
            slicePerm[i + 1] = Tables.sliceMove[slicePerm[i]][mv];
            parity[i + 1] = Tables.parityMove[parity[i]][mv];
        }

        // Early pruning: check if corner+slice heuristic exceeds our budget
        d1 = Tables.getPruning(Tables.sliceCornerPrune,
            (Tables.N_SLICE2 * cornerPerm[depthPhase1] + slicePerm[depthPhase1]) * 2 + parity[depthPhase1]);
        if (d1 > maxDepthPhase2)
            return -1;  // Can't solve within depth limit

        // Compute edge permutation coordinate by merging helper coordinates
        for (int i = 0; i < depthPhase1; i++) {
            mv = 3 * axis[i] + power[i] - 1;
            urToUl[i + 1] = Tables.urToUlMove[urToUl[i]][mv];
            ubToDf[i + 1] = Tables.ubToDfMove[ubToDf[i]][mv];
        }
        udEdgePerm[depthPhase1] = Tables.mergeURtoULandUBtoDF[urToUl[depthPhase1]][ubToDf[depthPhase1]];

        // Early pruning: check if edge+slice heuristic exceeds our budget
        d2 = Tables.getPruning(Tables.sliceEdgePrune,
            (Tables.N_SLICE2 * udEdgePerm[depthPhase1] + slicePerm[depthPhase1]) * 2 + parity[depthPhase1]);
        if (d2 > maxDepthPhase2)
            return -1;

        // Check if already solved (both heuristics are 0)
        if ((minDistPhase2[depthPhase1] = Math.max(d1, d2)) == 0)
            return depthPhase1;

        // Phase 2 IDA* search - similar structure to phase 1
        int depthPhase2 = 1;
        int n = depthPhase1;
        boolean busy = false;
        power[depthPhase1] = 0;
        axis[depthPhase1] = 0;
        minDistPhase2[n + 1] = 1;

        do {
            do {
                if ((depthPhase1 + depthPhase2 - n > minDistPhase2[n + 1]) && !busy) {
                    // Go deeper - but respect phase 2 move restrictions
                    if (axis[n] == 0 || axis[n] == 3) {
                        axis[++n] = 1;
                        power[n] = 2;  // R,F,L,B start at half turn (only valid option)
                    } else {
                        axis[++n] = 0;
                        power[n] = 1;  // U,D start at quarter turn
                    }
                } else if ((axis[n] == 0 || axis[n] == 3) ? (++power[n] > 3) : ((power[n] = power[n] + 2) > 3)) {
                    // For U,D: try 1,2,3 quarter turns
                    // For R,F,L,B: only try 2 (half turn), then skip to next axis
                    do {
                        if (++axis[n] > 5) {
                            if (n == depthPhase1) {
                                // At phase 2 root - increase depth limit
                                if (depthPhase2 >= maxDepthPhase2)
                                    return -1;
                                else {
                                    depthPhase2++;
                                    axis[n] = 0;
                                    power[n] = 1;
                                    busy = false;
                                    break;
                                }
                            } else {
                                n--;
                                busy = true;
                                break;
                            }
                        } else {
                            // Set initial power based on face type
                            if (axis[n] == 0 || axis[n] == 3)
                                power[n] = 1;  // U,D: start at quarter turn
                            else
                                power[n] = 2;  // R,F,L,B: only half turns allowed
                            busy = false;
                        }
                    } while (n != depthPhase1 && (axis[n - 1] == axis[n] || axis[n - 1] - 3 == axis[n]));
                } else {
                    busy = false;
                }
            } while (busy);

            // Apply move and update phase 2 coordinates
            mv = 3 * axis[n] + power[n] - 1;
            cornerPerm[n + 1] = Tables.cornerPermMove[cornerPerm[n]][mv];
            slicePerm[n + 1] = Tables.sliceMove[slicePerm[n]][mv];
            parity[n + 1] = Tables.parityMove[parity[n]][mv];
            udEdgePerm[n + 1] = Tables.udEdgePermMove[udEdgePerm[n]][mv];

            // Compute phase 2 heuristic as max of edge and corner pruning
            minDistPhase2[n + 1] = Math.max(
                Tables.getPruning(Tables.sliceEdgePrune,
                    (Tables.N_SLICE2 * udEdgePerm[n + 1] + slicePerm[n + 1]) * 2 + parity[n + 1]),
                Tables.getPruning(Tables.sliceCornerPrune,
                    (Tables.N_SLICE2 * cornerPerm[n + 1] + slicePerm[n + 1]) * 2 + parity[n + 1]));

        } while (minDistPhase2[n + 1] != 0);  // Continue until solved (heuristic = 0)

        return depthPhase1 + depthPhase2;
    }
}
